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

    MatrixFactorizer als = new AlternatingLeastSquares(byRow, byCol, 2, 20);
    als.setPreviousY(previousY);
    als.call();

    RealMatrix product = MatrixUtils.multiplyXYT(als.getX(), als.getY());

    /*
    Octave's result:
    -0.0031015   0.9885626   0.9988050   0.9894954  -0.0113606
    -0.0150823   1.0007337   1.0020473   1.0016263  -0.0234672
     0.9912587  -0.1735355   0.5758355  -0.1693988   0.9947107
     1.0002800   0.2148683   0.9760275   0.2194159   1.0005078
    -0.0149055   0.9952040   0.9965812   0.9960920  -0.0232438
     */

    StringBuilder productString = new StringBuilder(100);
    for (int row = 0; row < product.getRowDimension(); row++) {
      productString.append('\n').append(Arrays.toString(product.getRow(row)));
    }
    log.info("{}", productString);

    assertArrayEquals(
        new double[] {-0.003101527690887451, 0.9885625839233398, 0.9988050013780594, 0.9894953966140747, -0.011360526084899902},
        product.getRow(0));
    assertArrayEquals(
        new double[] {-0.015082359313964844, 1.0007337927818298, 1.002047285437584, 1.0016263127326965, -0.023467183113098145},
        product.getRow(1));
    assertArrayEquals(
        new double[] {0.9912587404251099, -0.17353546619415283, 0.575835570693016, -0.16939878463745117, 0.9947107434272766},
        product.getRow(2));
    assertArrayEquals(
        new double[] {1.0002800524234772, 0.21486833691596985, 0.9760275781154633, 0.21941590309143066, 1.0005077719688416},
        product.getRow(3));
    assertArrayEquals(
        new double[] {-0.01490551233291626, 0.9952040612697601, 0.9965812414884567, 0.9960920214653015, -0.02324378490447998},
        product.getRow(4));

  }

}
