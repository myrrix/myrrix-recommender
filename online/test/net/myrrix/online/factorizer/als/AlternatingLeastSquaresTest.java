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
import net.myrrix.online.factorizer.MatrixUtils;

public final class AlternatingLeastSquaresTest extends MyrrixTest {

  private static final Logger log = LoggerFactory.getLogger(AlternatingLeastSquaresTest.class);

  @Test
  public void testALS() throws Exception {

    FastByIDMap<FastByIDFloatMap> byRow = new FastByIDMap<FastByIDFloatMap>();
    FastByIDMap<FastByIDFloatMap> byCol = new FastByIDMap<FastByIDFloatMap>();
    // Octave: R = [ 0 2 3 -1 0 ; 0 0 4 5 0 ; 1 0 0 0 -2 ; 3 0 -1 0 5 ; 0 2 2 2 0 ]
    MatrixUtils.addTo(0, 1,  2.0f, byRow, byCol);
    MatrixUtils.addTo(0, 2,  3.0f, byRow, byCol);
    MatrixUtils.addTo(0, 3, -1.0f, byRow, byCol);
    MatrixUtils.addTo(1, 2,  4.0f, byRow, byCol);
    MatrixUtils.addTo(1, 3,  5.0f, byRow, byCol);
    MatrixUtils.addTo(2, 0,  1.0f, byRow, byCol);
    MatrixUtils.addTo(2, 4, -2.0f, byRow, byCol);
    MatrixUtils.addTo(3, 0,  3.0f, byRow, byCol);
    MatrixUtils.addTo(3, 2, -1.0f, byRow, byCol);
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


    MatrixFactorizer als = new AlternatingLeastSquares(byRow, byCol, 2, 5);
    als.setPreviousY(previousY);
    als.call();

    RealMatrix product = MatrixUtils.multiplyXYT(als.getX(), als.getY());

    /*
    Octave's result:
     -1.72063    0.98501    0.85312    0.98119   -2.03784
     -1.73882    0.99589    0.88007    0.99208   -2.05957
      1.11508    0.30489   35.98222    0.42299    0.92985
      0.76444   -0.26461    6.32216   -0.24170    0.83368
     -1.70496    0.97541    0.82088    0.97154   -2.01901
     */

    /*
     Java result:
     [-1.721226692199707,   0.9850207567214966,   0.8548178672790527,  0.9811955690383911,  -2.039476156234741]
     [-1.739422082901001,   0.9958971738815308,   0.8816533088684082,  0.9920884370803833,  -2.0612285137176514]
     [ 1.1154060363769531,  0.3050117492675781,  35.65459442138672,    0.4231681823730469,   0.9296188354492188]
     [ 0.7640366554260254, -0.26404595375061035,  6.268417835235596,  -0.24110984802246094,  0.8333296775817871]
     [-1.7055492401123047,  0.9754164218902588,   0.8227567672729492,  0.9715485572814941,  -2.020637035369873]
     */

    StringBuilder productString = new StringBuilder(100);
    for (int row = 0; row < product.getRowDimension(); row++) {
      productString.append('\n').append(Arrays.toString(product.getRow(row)));
    }
    log.info("{}", productString);

    assertArrayEquals(new double[] {-1.721226692199707, 0.9850207567214966, 0.8548178672790527, 0.9811955690383911, -2.039476156234741},
                      product.getRow(0));
    assertArrayEquals(new double[] {-1.739422082901001, 0.9958971738815308, 0.8816533088684082, 0.9920884370803833, -2.0612285137176514},
                      product.getRow(1));
    assertArrayEquals(new double[] {1.1154060363769531, 0.3050117492675781, 35.65459442138672, 0.4231681823730469, 0.9296188354492188},
                      product.getRow(2));
    assertArrayEquals(new double[] {0.7640366554260254, -0.26404595375061035, 6.268417835235596, -0.24110984802246094, 0.8333296775817871},
                      product.getRow(3));
    assertArrayEquals(new double[] {-1.7055492401123047, 0.9754164218902588, 0.8227567672729492, 0.9715485572814941, -2.020637035369873},
                      product.getRow(4));

  }

}
