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

import com.google.common.base.Preconditions;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
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
    return multiply(MTMinverse, M, null);
  }

  /**
   * @see #getLeftInverse(FastByIDMap)
   */
  public static FastByIDMap<float[]> getTransposeRightInverse(FastByIDMap<float[]> M) {
    if (M.isEmpty()) {
      return new FastByIDMap<float[]>();
    }
    RealMatrix MTM = transposeTimesSelf(M);
    RealMatrix MTMinverse = new LUDecomposition(MTM).getSolver().getInverse();
    return multiply(M, MTMinverse, null);
  }

  /**
   * @param S tall, skinny matrix
   * @param M small {@link RealMatrix}
   * @return S * M
   */
  public static FastByIDMap<float[]> multiply(FastByIDMap<float[]> S, RealMatrix M, FastByIDMap<float[]> result) {
    if (result == null) {
      result = new FastByIDMap<float[]>(S.size(), 1.2f);
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
   * @param M small {@link RealMatrix}
   * @param S tall, skinny matrix
   * @return M * S
   */
  public static FastByIDMap<float[]> multiply(RealMatrix M, FastByIDMap<float[]> S, FastByIDMap<float[]> result) {
    if (result == null) {
      result = new FastByIDMap<float[]>(S.size(), 1.2f);
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
   * @return column vector M * V
   */
  private static float[] matrixMultiply(double[][] M, float[] V) {
    int dimension = V.length;
    float[] out = new float[dimension];
    for (int i = 0; i < dimension; i++) {
      double total = 0.0;
      double[] matrixRow = M[i];
      for (int j = 0; j < dimension; j++) {
        total += V[j] * matrixRow[j];
      }
      out[i] = (float) total;
    }
    return out;
  }

  /**
   * @param M tall, skinny matrix
   * @return MT * M
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

}
