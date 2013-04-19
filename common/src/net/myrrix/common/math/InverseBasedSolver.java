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
 * An implementation that solves based on an explicitly computed inverse, A<sup>-1</sup>.
 * 
 * @author Sean Owen
 */
final class InverseBasedSolver implements Solver {
  
  private final RealMatrix Ainv;
  
  InverseBasedSolver(RealMatrix Ainv) {
    this.Ainv = Ainv;
  }
  
  @Override
  public float[] solveDToF(double[] b) {
    double[] x = Ainv.operate(b);
    float[] xCopy = new float[x.length];
    for (int i = 0; i < xCopy.length; i++) {
      xCopy[i] = (float) x[i];
    }
    return xCopy;
  }
  
  @Override
  public double[] solveFToD(float[] b) {
    double[] bCopy = new double[b.length];
    for (int i = 0; i < bCopy.length; i++) {
      bCopy[i] = b[i];
    }
    return Ainv.operate(bCopy);
  }
  
}
