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

  public static double norm(float[] x) {
    double total = 0.0;
    for (float f : x) {
      total += f * f;
    }
    return FastMath.sqrt(total);
  }

  public static double norm(double[] x) {
    double total = 0.0;
    for (double d : x) {
      total += d * d;
    }
    return FastMath.sqrt(total);
  }

  /**
   * @return Pearson's correlation between the series in the two given arrays
   */
  public static double correlation(float[] x, float[] y) {
    double sumX = 0.0;
    double sumY = 0.0;
    double sumX2 = 0.0;
    double sumY2 = 0.0;
    double sumXY = 0.0;
    int n = x.length;
    for (int i = 0; i < n; i++) {
      double xValue = x[i];
      double yValue = y[i];
      sumX += xValue;
      sumY += yValue;
      sumX2 += xValue * xValue;
      sumY2 += yValue * yValue;
      sumXY += xValue * yValue;
    }
    return (n * sumXY - sumX * sumY) / FastMath.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
  }

  public static double cosineMeasure(float[] x, float[] y) {
    return dot(x, y) / (norm(x) * norm(y));
  }

}
