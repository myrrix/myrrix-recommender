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
 * Encapsulates inverting a matrix. This allows for swapping in other strategies later.
 * 
 * @author Sean Owen
 */
interface MatrixInverter {

  double SINGULARITY_THRESHOLD = Double.parseDouble(System.getProperty("common.matrix.singularityThreshold", "0.001"));
  
  /**
   * @param M a square matrix to invert
   * @return the inverse if it exists
   * @throws org.apache.commons.math3.linear.SingularMatrixException if the matrix is singular
   *  to within a tolerance defined by {@link #SINGULARITY_THRESHOLD}.
   */
  RealMatrix invert(RealMatrix M);
  
}
