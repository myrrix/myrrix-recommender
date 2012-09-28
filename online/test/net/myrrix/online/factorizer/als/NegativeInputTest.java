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
     9.8238e-01   9.8428e-01   9.9606e-01  -2.0683e-04
     2.2860e-01   5.3762e-01   9.7808e-01   9.9154e-01
    -4.1319e-01  -1.0845e-01   3.2001e-01   9.8192e-01
     */

    StringBuilder productString = new StringBuilder(100);
    for (int row = 0; row < product.getRowDimension(); row++) {
      productString.append('\n').append(Arrays.toString(product.getRow(row)));
    }
    log.info("{}", productString);

    assertArrayEquals(
        new double[] {0.9823756814002991, 0.9842802286148071, 0.9960648342967033, -2.06679105758667E-4},
        product.getRow(0));
    assertArrayEquals(
        new double[] {0.2285962700843811, 0.5376167446374893, 0.9780800342559814, 0.991536557674408},
        product.getRow(1));
    assertArrayEquals(
        new double[] {-0.41318875923752785, -0.10844806581735611, 0.3200104311108589, 0.981917155906558},
        product.getRow(2));
  }

}
