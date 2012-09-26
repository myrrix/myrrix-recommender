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
      -0.0028899     0.98847      0.9988     0.98936   -0.011275
       -0.015076      1.0008       1.002      1.0016   -0.023591
         0.99115    -0.17332     0.57568    -0.16924     0.99466
          1.0003     0.21488      0.9757     0.21936      1.0005
        -0.01479     0.99519     0.99658     0.99603   -0.023257
     */

    StringBuilder productString = new StringBuilder(100);
    for (int row = 0; row < product.getRowDimension(); row++) {
      productString.append('\n').append(Arrays.toString(product.getRow(row)));
    }
    log.info("{}", productString);

    assertArrayEquals(
        new double[] {-0.0028899312019348145, 0.9884746670722961, 0.9987993985414505, 0.9893647730350494, -0.011275291442871094},
        product.getRow(0));
    assertArrayEquals(
        new double[] {-0.015075623989105225, 1.0007916688919067, 1.0020367503166199, 1.0016408264636993, -0.023590564727783203},
        product.getRow(1));
    assertArrayEquals(
        new double[] {0.9911524653434753, -0.1733236014842987, 0.5756785199046135, -0.16923746466636658, 0.99466273188591},
        product.getRow(2));
    assertArrayEquals(
        new double[] {1.0002689361572266, 0.214875727891922, 0.9757022634148598, 0.21935537457466125, 1.0005071461200714},
        product.getRow(3));
    assertArrayEquals(
        new double[] {-0.014790475368499756, 0.9951860904693604, 0.9965763241052628, 0.9960313737392426, -0.02325725555419922},
        product.getRow(4));

  }

}
