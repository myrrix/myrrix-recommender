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

package net.myrrix.online.eval;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.util.FastMath;

/**
 * Represents a range of values to try for a parameter.
 * 
 * @author Sean Owen
 */
public final class ParameterRange {
  
  private final double min;
  private final double max;
  private final boolean integerOnly;

  /**
   * @param min smallest value to try
   * @param max largest value to try
   * @param integerOnly only supply integer values, not any real value
   */
  public ParameterRange(double min, double max, boolean integerOnly) {
    Preconditions.checkArgument(max >= min, "Max must not be less than min");
    this.min = min;
    this.max = max;
    this.integerOnly = integerOnly;
  }

  /**
   * @param numSteps must be at least 2. The number of value steps to construct
   * @return value steps to try for this parameter value. {@code min} and {@code max} are the first and last
   *  elements. The values are not necessarily distributed uniformly in the range.
   */
  public Number[] buildSteps(int numSteps) {
    Preconditions.checkArgument(numSteps >= 2, "numSteps must be at least 2: {}", numSteps);
    if (min == max) {
      return new Number[] { maybeRound(min) };
    }
    if (integerOnly) {
      int roundedMin = (int) FastMath.round(min);
      int roundedMax = (int) FastMath.round(max);
      int maxResonableSteps = roundedMax - roundedMin + 1;
      if (numSteps >= maxResonableSteps) {
        Number[] sequence = new Number[maxResonableSteps];
        for (int i = 0; i < sequence.length; i++) {
          sequence[i] = roundedMin + i;
        }
        return sequence;
      }
    }

    Number[] stepValues = new Number[numSteps];
    stepValues[0] = maybeRound(min);
    stepValues[stepValues.length - 1] = maybeRound(max);
    double range = max - min;
    for (int i = 1; i < stepValues.length - 1; i++) {
      double fraction = (double) i / (numSteps-1);
      stepValues[i] = maybeRound(min + range * fraction * fraction);
    }
    return stepValues;
  }

  /**
   * @return {@link Long} or {@link Double}
   */
  private Number maybeRound(double value) {
    if (integerOnly) {
      return FastMath.round(value);
    }
    return value;
  }
  
  @Override
  public String toString() {
    return maybeRound(min) + ":" + maybeRound(max);
  }
  
}
