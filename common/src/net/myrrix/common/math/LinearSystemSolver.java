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

import org.apache.commons.math3.linear.RealMatrix;

/**
 * Encapsulates a strategy for solving a linear system Ax = b. 
 * This allows for swapping in other strategies later.
 * 
 * @author Sean Owen
 */
interface LinearSystemSolver {

  /**
   * Threshold below which a value is considered 0 for purposes of deciding that a matrix's singular
   * value is 0 and therefore is singular
   */
  double SINGULARITY_THRESHOLD = 
      Double.parseDouble(System.getProperty("common.matrix.singularityThreshold", Double.toString(1.0e-5)));

  /**
   * @return a {@link Solver} for A, which can solve Ax = b for x, given b. 
   *  Note that this only works for symmetric matrices!
   */
  Solver getSolver(RealMatrix A);
  
  /**
   * @return true if A appears to be invertible ({@link #getSolver(RealMatrix)} would succeed).
   *  Note that this only works for symmetric matrices!
   */
  boolean isNonSingular(RealMatrix A);
  
}
