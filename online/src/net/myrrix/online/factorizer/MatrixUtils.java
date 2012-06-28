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

package net.myrrix.online.factorizer;

import java.lang.reflect.Field;
import java.util.Arrays;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;

/**
 * Contains utility methods for dealing with matrices, which are here represented as
 * {@link FastByIDMap}s of {@link FastByIDFloatMap}s, or of {@code float[]}.
 *
 * @author Sean Owen
 */
public final class MatrixUtils {

  private static final Logger log = LoggerFactory.getLogger(MatrixUtils.class);

  private static final int PRINT_COLUMN_WIDTH = 12;

  // This hack saves a lot of time spent copying out data from Array2DRowRealMatrix objects
  private static final Field MATRIX_DATA_FIELD;
  static {
    try {
      MATRIX_DATA_FIELD = Array2DRowRealMatrix.class.getDeclaredField("data");
    } catch (NoSuchFieldException nsfe) {
      log.error("Can't access Array2DRowRealMatrix.data", nsfe);
      throw new IllegalStateException(nsfe);
    }
    MATRIX_DATA_FIELD.setAccessible(true);
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

    float oldValue = theRow.get(column);
    if (Float.isNaN(oldValue)) {
      theRow.put(column, value);
    } else {
      theRow.put(column, value + oldValue);
    }

    FastByIDFloatMap theColumn = RbyColumn.get(column);
    if (theColumn == null) {
      theColumn = new FastByIDFloatMap();
      RbyColumn.put(column, theColumn);
    }

    oldValue = theColumn.get(row);
    if (Float.isNaN(oldValue)) {
      theColumn.put(row, value);
    } else {
      theColumn.put(row, value + oldValue);
    }
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
    }
    FastByIDFloatMap theColumn = RbyColumn.get(column);
    if (theColumn != null) {
      theColumn.remove(row);
    }
  }

  /**
   * Left-inverts a tall skinny matrix. The left inverse of M is ( (MT * M)^-1 * MT ).
   *
   * @param M tall skinny matrix to left-invert
   * @return a left inverse of M
   */
  public static FastByIDMap<float[]> getLeftInverse(FastByIDMap<float[]> M) {
    if (M.isEmpty()) {
      return new FastByIDMap<float[]>();
    }
    RealMatrix MTM = transposeTimesSelf(M);
    RealMatrix MTMinverse = new LUDecomposition(MTM).getSolver().getInverse();
    // Second argument is really MT. Passing M since it will be treated as MT.
    return multiply(MTMinverse, M, null);
  }

  /**
   * Right-inverts the transpose of a tall, skinny matrix. The right inverse of MT is ( M * (MT * M)^-1 ).
   *
   * @param M tall, skinny matrix to right-invert the transpose of
   * @return a right inverse of MT
   */
  public static FastByIDMap<float[]> getTransposeRightInverse(FastByIDMap<float[]> M) {
    if (M.isEmpty()) {
      return new FastByIDMap<float[]>();
    }
    RealMatrix MTM = transposeTimesSelf(M);
    RealMatrix MTMinverse = new LUDecomposition(MTM).getSolver().getInverse();
    // Computing M * (MT * M)^-1, but instead, will compute the transpose of (MT * M)^-1 times transpose of M
    // to end up with the transpose of the answer. But it doesn't matter since the same representation is used
    // either way and is not ambiguous. Again passing M and second argument when it's conceptually MT as it
    // will be treated correctly.
    return multiply(MTMinverse.transpose(), M, null);
  }

  /**
   * @param M small {@link RealMatrix}
   * @param S wide, short matrix
   * @return M * S
   */
  public static FastByIDMap<float[]> multiply(RealMatrix M, FastByIDMap<float[]> S, FastByIDMap<float[]> result) {
    if (result == null) {
      result = new FastByIDMap<float[]>(S.size(), 1.25f);
    } else {
      result.clear();
    }
    double[][] matrixData = accessMatrixDataDirectly(M);
    for (FastByIDMap<float[]>.MapEntry entry : S.entrySet()) {
      long id = entry.getKey();
      float[] vector = entry.getValue();
      float[] resultVector = matrixMultiply(matrixData, vector);
      result.put(id, resultVector);
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
    for (FastByIDMap<float[]>.MapEntry entry : M.entrySet()) {
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
    Preconditions.checkState(result != null);
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

  /**
   * @param M matrix to print
   * @return a print-friendly rendering of a dense, skinny matrix.
   */
  public static String featureMatrixToString(FastByIDMap<float[]> M) {
    long[] rowKeys = keysInOrder(M);
    StringBuilder result = new StringBuilder();
    for (long rowKey : rowKeys) {
      appendWithPadOrTruncate(rowKey, result);
      for (float f : M.get(rowKey)) {
        result.append('\t');
        appendWithPadOrTruncate(f, result);
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
    for (FastByIDMap<FastByIDFloatMap>.MapEntry entry : M.entrySet()) {
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
    appendWithPadOrTruncate(String.valueOf(value), to);
  }

  private static void appendWithPadOrTruncate(float value, StringBuilder to) {
    String stringValue = String.valueOf(value);
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
