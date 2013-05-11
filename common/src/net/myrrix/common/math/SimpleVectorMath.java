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

import org.apache.commons.math3.util.FastMath;

/**
 * Simple utility methods related to vectors represented as simple {@code float[]}s.
 * 
 * @author Sean Owen
 * @since 1.0
 */
public final class SimpleVectorMath {

  private SimpleVectorMath() {}

  /**
   * @return dot product of the two given arrays
   */
  public static double dot(float[] x, float[] y) {
    int length = x.length;
    double dot = 0.0;
    for (int i = 0; i < length; i++) {
      dot += x[i] * y[i];
    }
    return dot;
  }

  /**
   * @return the L2 norm of vector x
   */
  public static double norm(float[] x) {
    double total = 0.0;
    for (float f : x) {
      total += f * f;
    }
    return FastMath.sqrt(total);
  }

  /**
   * @return the L2 norm of vector x
   */
  public static double norm(double[] x) {
    double total = 0.0;
    for (double d : x) {
      total += d * d;
    }
    return FastMath.sqrt(total);
  }

  /**
   * @return the square of the distance between the vectors {@code x} and {@code y}
   */
  public static double distanceSquared(float[] x, float[] y) {
    int length = x.length;
    double total = 0.0;
    for (int i = 0; i < length; i++) {
      double d = x[i] - y[i];
      total += d * d;
    }
    return total;
  }

  /**
   * @param x vector that will modified to have unit length
   */
  public static void normalize(float[] x) {
    float norm = (float) norm(x);
    for (int i = 0; i < x.length; i++) {
      x[i] /= norm;
    }
  }

}
