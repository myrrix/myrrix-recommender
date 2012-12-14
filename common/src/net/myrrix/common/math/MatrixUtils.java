/*
 * Copyright Myrrix Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.myrrix.common.math;

import java.lang.reflect.Field;
import java.util.Arrays;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;

/**
 * Contains utility methods for dealing with matrices, which are here represented as
 * {@link FastByIDMap}s of {@link FastByIDFloatMap}s, or of {@code float[]}.
 *
 * @author Sean Owen
 */
public final class MatrixUtils {

  private static final Logger log = LoggerFactory.getLogger(MatrixUtils.class);

  private static final int PRINT_COLUMN_WIDTH = 12;
  private static final double SINGULARITY_THRESHOLD =
      Double.parseDouble(System.getProperty("common.matrix.singularityThreshold", "0.001"));

  // This hack saves a lot of time spent copying out data from Array2DRowRealMatrix objects
  private static final Field MATRIX_DATA_FIELD = loadField(Array2DRowRealMatrix.class, "data");
  private static final Field RDIAG_FIELD = loadField(QRDecomposition.class, "rDiag");

  private static Field loadField(Class<?> clazz, String fieldName) {
    Field field;
    try {
      field = clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException nsfe) {
      log.error("Can't access {}.{}", clazz, fieldName);
      throw new IllegalStateException(nsfe);
    }
    field.setAccessible(true);
    return field;
  }

  private MatrixUtils() {
  }

  /**
   * Efficiently increments an entry in two parallel, sparse matrices.
   *
   * @param row row to increment
   * @param column column to increment
   * @param value increment value
   * @param RbyRow matrix R to update, keyed by row
   * @param RbyColumn matrix R to update, keyed by column
   */
  public static void addTo(long row,
                           long column,
                           float value,
                           FastByIDMap<FastByIDFloatMap> RbyRow,
                           FastByIDMap<FastByIDFloatMap> RbyColumn) {

    FastByIDFloatMap theRow = RbyRow.get(row);
    if (theRow == null) {
      theRow = new FastByIDFloatMap();
      RbyRow.put(row, theRow);
    }
    theRow.increment(column, value);

    FastByIDFloatMap theColumn = RbyColumn.get(column);
    if (theColumn == null) {
      theColumn = new FastByIDFloatMap();
      RbyColumn.put(column, theColumn);
    }
    theColumn.increment(row, value);
  }

  /**
   * Efficiently removes an entry in two parallel, sparse matrices.
   *
   * @param row row to remove
   * @param column column to remove
   * @param RbyRow matrix R to update, keyed by row
   * @param RbyColumn matrix R to update, keyed by column
   */
  public static void remove(long row,
                            long column,
                            FastByIDMap<FastByIDFloatMap> RbyRow,
                            FastByIDMap<FastByIDFloatMap> RbyColumn) {
    FastByIDFloatMap theRow = RbyRow.get(row);
    if (theRow != null) {
      theRow.remove(column);
      if (theRow.isEmpty()) {
        RbyRow.remove(row);
      }
    }
    FastByIDFloatMap theColumn = RbyColumn.get(column);
    if (theColumn != null) {
      theColumn.remove(row);
      if (theColumn.isEmpty()) {
        RbyColumn.remove(column);
      }
    }
  }

  /**
   * <p>Pseudo-inverts a tall skinny matrix M. The result can be used as a left-inverse of M or
   * right-inverse of MT:</p>
   *
   * <p>{@code ((MT * M)^-1 * MT) * M = I}</p>
   * <p>{@code MT * (M * (MT * M)^-1) = I}</p>
   *
   * @param M tall skinny matrix
   * @return a pseudo-inverse of M
   */
  public static FastByIDMap<float[]> getPseudoInverse(FastByIDMap<float[]> M) {
    if (M == null || M.isEmpty()) {
      return M;
    }
    // Second argument is really MT. Passing M since it will be treated as MT.
    return multiply(getTransposeTimesSelfInverse(M), M);
  }

  public static RealMatrix getTransposeTimesSelfInverse(FastByIDMap<float[]> M) {
    if (M == null || M.isEmpty()) {
      return null;
    }
    RealMatrix MTM = transposeTimesSelf(M);
    return invert(MTM);
  }

  public static RealMatrix invert(RealMatrix M) {
    QRDecomposition decomposition = new QRDecomposition(M, SINGULARITY_THRESHOLD);
    DecompositionSolver solver = decomposition.getSolver();
    RealMatrix inverse;
    try {
      inverse = solver.getInverse();
    } catch (SingularMatrixException sme) {
      log.warn("Matrix is near-singular; add more data or decrease the value of model.features ({})", sme.toString());
      double[] rDiag;
      try {
        rDiag = (double[]) RDIAG_FIELD.get(decomposition);
      } catch (IllegalAccessException iae) {
        log.warn("Can't read QR decomposition fields to suggest dimensionality");
        throw sme;
      }
      for (int i = 0; i < rDiag.length; i++) {
        if (FastMath.abs(rDiag[i]) <= SINGULARITY_THRESHOLD) {
          log.info("Suggested value of -Dmodel.features is about {} or less", i);
          break;
        }
      }
      throw sme;
    }
    return new Array2DRowRealMatrix(inverse.getData());
  }

  /**
   * @param M small {@link RealMatrix}
   * @param S wide, short matrix
   * @return M * S as a newly allocated matrix
   */
  public static FastByIDMap<float[]> multiply(RealMatrix M, FastByIDMap<float[]> S) {
    FastByIDMap<float[]> result = new FastByIDMap<float[]>(S.size(), 1.25f);
    double[][] matrixData = accessMatrixDataDirectly(M);
    for (FastByIDMap.MapEntry<float[]> entry : S.entrySet()) {
      result.put(entry.getKey(), matrixMultiply(matrixData, entry.getValue()));
    }
    return result;
  }

  public static RealMatrix multiplyXYT(FastByIDMap<float[]> X, FastByIDMap<float[]> Y) {
    int Ysize = Y.size();
    int Xsize = X.size();
    RealMatrix result = new Array2DRowRealMatrix(Xsize, Ysize);
    for (int row = 0; row < Xsize; row++) {
      for (int col = 0; col < Ysize; col++) {
        result.setEntry(row, col, SimpleVectorMath.dot(X.get(row), Y.get(col)));
      }
    }
    return result;
  }

  /**
   * @param matrix an {@link Array2DRowRealMatrix}
   * @return its "data" field -- not a copy
   */
  private static double[][] accessMatrixDataDirectly(RealMatrix matrix) {
    try {
      return (double[][]) MATRIX_DATA_FIELD.get(matrix);
    } catch (IllegalAccessException iae) {
      throw new IllegalStateException(iae);
    }
  }

  public static double[] multiply(RealMatrix matrix, float[] V) {
    double[][] M = accessMatrixDataDirectly(matrix);
    int rows = M.length;
    int cols = V.length;
    double[] out = new double[rows];
    for (int i = 0; i < rows; i++) {
      double total = 0.0;
      double[] matrixRow = M[i];
      for (int j = 0; j < cols; j++) {
        total += V[j] * matrixRow[j];
      }
      out[i] = total;
    }
    return out;
  }

  /**
   * @param M matrix
   * @param V column vector
   * @return column vector M * V
   */
  private static float[] matrixMultiply(double[][] M, float[] V) {
    int rows = M.length;
    int cols = V.length;
    float[] out = new float[rows];
    for (int i = 0; i < rows; i++) {
      double total = 0.0;
      double[] matrixRow = M[i];
      for (int j = 0; j < cols; j++) {
        total += V[j] * matrixRow[j];
      }
      out[i] = (float) total;
    }
    return out;
  }

  /**
   * @param M tall, skinny matrix
   * @return MT * M as a dense matrix
   */
  public static RealMatrix transposeTimesSelf(FastByIDMap<float[]> M) {
    RealMatrix result = null;
    for (FastByIDMap.MapEntry<float[]> entry : M.entrySet()) {
      float[] vector = entry.getValue();
      int dimension = vector.length;
      if (result == null) {
        result = new Array2DRowRealMatrix(dimension, dimension);
      }
      for (int row = 0; row < dimension; row++) {
        float rowValue = vector[row];
        for (int col = 0; col < dimension; col++) {
          result.addToEntry(row, col, rowValue * vector[col]);
        }
      }
    }
    Preconditions.checkNotNull(result);
    return result;
  }

  /**
   * @param M matrix to print
   * @return a print-friendly rendering of a sparse matrix. Not useful for wide matrices.
   */
  public static String matrixToString(FastByIDMap<FastByIDFloatMap> M) {
    StringBuilder result = new StringBuilder();
    long[] colKeys = unionColumnKeysInOrder(M);
    appendWithPadOrTruncate("", result);
    for (long colKey : colKeys) {
      result.append('\t');
      appendWithPadOrTruncate(colKey, result);
    }
    result.append("\n\n");
    long[] rowKeys = keysInOrder(M);
    for (long rowKey : rowKeys) {
      appendWithPadOrTruncate(rowKey, result);
      FastByIDFloatMap row = M.get(rowKey);
      for (long colKey : colKeys) {
        result.append('\t');
        float value = row.get(colKey);
        if (Float.isNaN(value)) {
          appendWithPadOrTruncate("", result);
        } else {
          appendWithPadOrTruncate(value, result);
        }
      }
      result.append('\n');
    }
    result.append('\n');
    return result.toString();
  }

  private static long[] keysInOrder(FastByIDMap<?> map) {
    FastIDSet keys = new FastIDSet(map.size(), 1.25f);
    LongPrimitiveIterator it = map.keySetIterator();
    while (it.hasNext()) {
      keys.add(it.nextLong());
    }
    long[] keysArray = keys.toArray();
    Arrays.sort(keysArray);
    return keysArray;
  }

  private static long[] unionColumnKeysInOrder(FastByIDMap<FastByIDFloatMap> M) {
    FastIDSet keys = new FastIDSet(1000, 1.25f);
    for (FastByIDMap.MapEntry<FastByIDFloatMap> entry : M.entrySet()) {
      LongPrimitiveIterator it = entry.getValue().keySetIterator();
      while (it.hasNext()) {
        keys.add(it.nextLong());
      }
    }
    long[] keysArray = keys.toArray();
    Arrays.sort(keysArray);
    return keysArray;
  }

  private static void appendWithPadOrTruncate(long value, StringBuilder to) {
    appendWithPadOrTruncate(Long.toString(value), to);
  }

  private static void appendWithPadOrTruncate(float value, StringBuilder to) {
    String stringValue = Float.toString(value);
    if (value >= 0.0f) {
      stringValue = ' ' + stringValue;
    }
    appendWithPadOrTruncate(stringValue, to);
  }

  private static void appendWithPadOrTruncate(CharSequence value, StringBuilder to) {
    int length = value.length();
    if (length >= PRINT_COLUMN_WIDTH) {
      to.append(value, 0, PRINT_COLUMN_WIDTH);
    } else {
      for (int i = length; i < PRINT_COLUMN_WIDTH; i++) {
        to.append(' ');
      }
      to.append(value);
    }
  }

}
