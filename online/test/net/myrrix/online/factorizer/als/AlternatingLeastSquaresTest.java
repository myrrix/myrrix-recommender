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
import java.util.concurrent.ExecutionException;

import org.apache.commons.math3.linear.RealMatrix;
import org.junit.After;
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
    RealMatrix product = buildTestXYTProduct(false);

    assertArrayEquals(
        new float[] {-0.030258f, 0.852781f, 1.004839f, 1.024087f, -0.036206f},
        product.getRow(0));
    assertArrayEquals(
        new float[] {0.077046f, 0.751232f, 0.949796f, 0.910322f, 0.073047f},
        product.getRow(1));
    assertArrayEquals(
        new float[] {0.916777f, -0.196005f, 0.335926f, -0.163591f, 0.929028f},
        product.getRow(2));
    assertArrayEquals(
        new float[] {0.987400f, 0.130943f, 0.772403f, 0.235522f, 0.998354f},
        product.getRow(3));
    assertArrayEquals(
        new float[] {-0.028683f, 0.850540f, 1.003130f, 1.021514f, -0.034598f},
        product.getRow(4));
  }
  
  @Test
  public void testALSPredictingR() throws Exception {
    RealMatrix product = buildTestXYTProduct(true);

    assertArrayEquals(
        new float[] {0.0678369f, 0.6574759f, 2.1020291f, 2.0976211f, 0.1115919f},
        product.getRow(0));
    assertArrayEquals(
        new float[] {-0.0176293f, 1.3062225f, 4.1365933f, 4.1739127f, -0.0380586f},
        product.getRow(1));
    assertArrayEquals(
        new float[] {1.0854513f, -0.0344434f, 0.1725342f, -0.1564803f, 1.8502977f},
        product.getRow(2));
    assertArrayEquals(
        new float[] {2.8377915f, 0.0528524f, 0.9041158f, 0.0474437f, 4.8365208f},
        product.getRow(3));
    assertArrayEquals(
        new float[] {-0.0057799f, 0.6608552f, 2.0936351f, 2.1115670f, -0.0139042f,},
        product.getRow(4));
  }
  
  @Override
  @After
  public void tearDown() throws Exception {
    System.clearProperty("model.reconstructRMatrix");    
    super.tearDown();
  }

  private static RealMatrix buildTestXYTProduct(boolean reconstructR) throws ExecutionException, InterruptedException {
    System.setProperty("model.reconstructRMatrix", Boolean.toString(reconstructR));
    
    FastByIDMap<FastByIDFloatMap> byRow = new FastByIDMap<FastByIDFloatMap>();
    FastByIDMap<FastByIDFloatMap> byCol = new FastByIDMap<FastByIDFloatMap>();
    // Octave: R = [ 0 2 3 1 0 ; 0 0 4 5 0 ; 1 0 0 0 2 ; 3 0 1 0 5 ; 0 2 2 2 0 ]
    MatrixUtils.addTo(0, 1, 2.0f, byRow, byCol);
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
    
    return product;
  }

}
