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

/**
 * A solver for the system Ax = b, where A is an n x n matrix and x and b are n-element vectors.
 * An implementation of this class encapsulates a solver which implicitly contains A.
 */
public interface Solver {
  
  /**
   * Solves a linear system Ax = b, where {@code A} is implicit in this instance.
   * 
   * @param b vector, as {@code double} array
   * @return x as {@code float} array
   */
  float[] solveDToF(double[] b);
  
  /**
   * Like {@link #solveDToF(double[])} but input is a {@code float} array and output is a 
   * {@code double} array.
   */
  double[] solveFToD(float[] b);
  
}
