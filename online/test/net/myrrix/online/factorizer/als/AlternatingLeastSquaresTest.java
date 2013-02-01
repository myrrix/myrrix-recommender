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

package net.myrrix.online.factorizer.als;

import java.util.Arrays;

import org.apache.commons.math3.linear.RealMatrix;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.MyrrixTest;
import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.online.factorizer.MatrixFactorizer;
import net.myrrix.common.math.MatrixUtils;

public final class AlternatingLeastSquaresTest extends MyrrixTest {

  private static final Logger log = LoggerFactory.getLogger(AlternatingLeastSquaresTest.class);

  @Test
  public void testALS() throws Exception {

    FastByIDMap<FastByIDFloatMap> byRow = new FastByIDMap<FastByIDFloatMap>();
    FastByIDMap<FastByIDFloatMap> byCol = new FastByIDMap<FastByIDFloatMap>();
    // Octave: R = [ 0 2 3 1 0 ; 0 0 4 5 0 ; 1 0 0 0 2 ; 3 0 1 0 5 ; 0 2 2 2 0 ]
    MatrixUtils.addTo(0, 1,  2.0f, byRow, byCol);
    MatrixUtils.addTo(0, 2,  3.0f, byRow, byCol);
    MatrixUtils.addTo(0, 3,  1.0f, byRow, byCol);
    MatrixUtils.addTo(1, 2,  4.0f, byRow, byCol);
    MatrixUtils.addTo(1, 3,  5.0f, byRow, byCol);
    MatrixUtils.addTo(2, 0,  1.0f, byRow, byCol);
    MatrixUtils.addTo(2, 4,  2.0f, byRow, byCol);
    MatrixUtils.addTo(3, 0,  3.0f, byRow, byCol);
    MatrixUtils.addTo(3, 2,  1.0f, byRow, byCol);
    MatrixUtils.addTo(3, 4,  5.0f, byRow, byCol);
    MatrixUtils.addTo(4, 1,  2.0f, byRow, byCol);
    MatrixUtils.addTo(4, 2,  2.0f, byRow, byCol);
    MatrixUtils.addTo(4, 3,  2.0f, byRow, byCol);

    // Octave: Y = [ 0.1 0.2 ; 0.2 0.5 ; 0.3 0.1 ; 0.2 0.2 ; 0.5 0.4 ];
    FastByIDMap<float[]> previousY = new FastByIDMap<float[]>();
    previousY.put(0L, new float[] {0.1f, 0.2f});
    previousY.put(1L, new float[] {0.2f, 0.5f});
    previousY.put(2L, new float[] {0.3f, 0.1f});
    previousY.put(3L, new float[] {0.2f, 0.2f});
    previousY.put(4L, new float[] {0.5f, 0.4f});

    MatrixFactorizer als = new AlternatingLeastSquares(byRow, byCol, 2, 0.0001, 40);
    als.setPreviousY(previousY);
    als.call();

    RealMatrix product = MatrixUtils.multiplyXYT(als.getX(), als.getY());

    StringBuilder productString = new StringBuilder(100);
    for (int row = 0; row < product.getRowDimension(); row++) {
      productString.append('\n').append(Arrays.toString(doubleToFloatArray(product.getRow(row))));
    }
    log.info("{}", productString);

    assertArrayEquals(
        new float[] {0.37615702f, 0.9395947f, 0.95842475f, 0.9447453f, 0.3578715f},
        product.getRow(0));
    assertArrayEquals(
        new float[] {0.37096402f, 0.965766f, 0.9792176f, 0.9707236f, 0.35152173f},
        product.getRow(1));
    assertArrayEquals(
        new float[] {0.91260827f, 0.18102002f, 0.5011245f, 0.20005447f, 0.94379795f},
        product.getRow(2));
    assertArrayEquals(
        new float[] {0.9606254f, 0.5691479f, 0.8565864f, 0.58800423f, 0.9798256f},
        product.getRow(3));
    assertArrayEquals(
        new float[] {0.35010007f, 0.9454391f, 0.95368934f, 0.9500119f, 0.33052757f},
        product.getRow(4));

  }

}
