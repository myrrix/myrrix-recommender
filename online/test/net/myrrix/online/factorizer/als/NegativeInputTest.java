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
import net.myrrix.common.math.MatrixUtils;
import net.myrrix.online.factorizer.MatrixFactorizer;

public final class NegativeInputTest extends MyrrixTest {

  private static final Logger log = LoggerFactory.getLogger(NegativeInputTest.class);

  @Test
  public void testALS() throws Exception {

    FastByIDMap<FastByIDFloatMap> byRow = new FastByIDMap<FastByIDFloatMap>();
    FastByIDMap<FastByIDFloatMap> byCol = new FastByIDMap<FastByIDFloatMap>();

    // Octave: R = [ 1 1 1 0 ; 0 -1 1 1 ; -1 0 0 1 ]
    MatrixUtils.addTo(0, 0,  1.0f, byRow, byCol);
    MatrixUtils.addTo(0, 1,  1.0f, byRow, byCol);
    MatrixUtils.addTo(0, 2,  1.0f, byRow, byCol);
    MatrixUtils.addTo(1, 1, -1.0f, byRow, byCol);
    MatrixUtils.addTo(1, 2,  1.0f, byRow, byCol);
    MatrixUtils.addTo(1, 3,  1.0f, byRow, byCol);
    MatrixUtils.addTo(2, 0, -1.0f, byRow, byCol);
    MatrixUtils.addTo(2, 3,  1.0f, byRow, byCol);

    // Octave: Y = [ 0.1 0.2 ; 0.2 0.5 ; 0.3 0.1 ; 0.2 0.2 ];
    FastByIDMap<float[]> previousY = new FastByIDMap<float[]>();
    previousY.put(0L, new float[] {0.1f, 0.2f});
    previousY.put(1L, new float[] {0.2f, 0.5f});
    previousY.put(2L, new float[] {0.3f, 0.1f});
    previousY.put(3L, new float[] {0.2f, 0.2f});

    MatrixFactorizer als = new AlternatingLeastSquares(byRow, byCol, 2, 100);
    als.setPreviousY(previousY);
    als.call();

    RealMatrix product = MatrixUtils.multiplyXYT(als.getX(), als.getY());

    /*
     Octave result:
   0.83870   0.84350   0.93644   0.69230
   0.55670   0.56371   0.83513   0.89458
   0.33828   0.34512   0.65170   0.83742
     */

    StringBuilder productString = new StringBuilder(100);
    for (int row = 0; row < product.getRowDimension(); row++) {
      productString.append('\n').append(Arrays.toString(product.getRow(row)));
    }
    log.info("{}", productString);

    assertArrayEquals(
        new double[] {0.8386964797973633, 0.8435029536485672, 0.9364429116249084, 0.692302331328392},
        product.getRow(0));
    assertArrayEquals(
        new double[] {0.5566975474357605, 0.5637081563472748, 0.8351274281740189, 0.8945793211460114},
        product.getRow(1));
    assertArrayEquals(
        new double[] {0.3382817953824997, 0.34512191265821457, 0.6516955867409706, 0.837417971342802},
        product.getRow(2));
  }

}
