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

package net.myrrix.common.math;

import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.random.RandomGenerator;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.MyrrixTest;
import net.myrrix.common.random.RandomManager;

/**
 * Tests performance of {@link LinearSystemSolver#getSolver(RealMatrix)}.
 * 
 * @author Sean Owen
 */
public final class SolverLoadTest extends MyrrixTest {
  
  private static final Logger log = LoggerFactory.getLogger(SolverLoadTest.class);
  
  @Test
  public void testLoad() {
    RealMatrix symmetric = randomSymmetricMatrix(500);
    Stopwatch stopwatch = new Stopwatch().start();
    int iterations = 100;
    for (int i = 0; i < iterations; i++) {
      MatrixUtils.getSolver(symmetric);
    }
    stopwatch.stop();
    long elapsedMS = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    log.info("{}ms elapsed", elapsedMS);
    assertTrue(elapsedMS < 300 * iterations);
  }
  
  private static RealMatrix randomSymmetricMatrix(int dimension) {
    RandomGenerator random = RandomManager.getRandom();
    RealMatrix symmetric = new Array2DRowRealMatrix(dimension, dimension);
    for (int j = 0; j < dimension; j++) {
      // Diagonal
      symmetric.setEntry(j, j, random.nextDouble());
      for (int k = j + 1; k < dimension; k++) {
        // Off-diagonal
        double d = random.nextDouble();
        symmetric.setEntry(j, k, d);
        symmetric.setEntry(k, j, d);          
      }
    }
    return symmetric;
  }
  
}
