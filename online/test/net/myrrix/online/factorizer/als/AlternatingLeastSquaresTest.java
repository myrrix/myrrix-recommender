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
      productString.append('\n').append(Arrays.toString(product.getRow(row)));
    }
    log.info("{}", productString);

    assertArrayEquals(
        new float[] {0.375636f, 0.93950576f, 0.9583561f, 0.94475424f, 0.35734674f},
        product.getRow(0));
    assertArrayEquals(
        new float[] {0.3705553f, 0.9655348f, 0.97907597f, 0.9705947f, 0.35111937f},
        product.getRow(1));
    assertArrayEquals(
        new float[] {0.9125805f, 0.1808266f, 0.5008378f, 0.19995895f, 0.9437789f},
        product.getRow(2));
    assertArrayEquals(
        new float[] {0.9606167f, 0.56925243f, 0.85666955f, 0.58824134f, 0.97982174f},
        product.getRow(3));
    assertArrayEquals(
        new float[] {0.34965515f, 0.94532204f, 0.95363116f, 0.9499925f, 0.33008313f},
        product.getRow(4));

  }

}
