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

package net.myrrix.common;

import com.google.common.base.Preconditions;

/**
 * General utility methods related to the language, or primitves.
 *
 * @author Sean Owen
 */
public final class LangUtils {

  private LangUtils() {
  }

  /**
   * Parses a {@code float} from a {@link String} as if by {@link Float#valueOf(String)}, but disallows special
   * values like {@link Float#NaN}, {@link Float#POSITIVE_INFINITY} and {@link Float#NEGATIVE_INFINITY}.
   *
   * @param s {@link String} to parse
   * @return floating-point value in the {@link String}
   * @throws NumberFormatException if input does not parse as a floating-point value
   * @throws IllegalArgumentException if input is infinite or {@link Float#NaN}
   * @see #parseDouble(String)
   */
  public static float parseFloat(String s) {
    float value = Float.parseFloat(s);
    Preconditions.checkArgument(isFinite(value), "Bad value: %s", value);
    return value;
  }

  /**
   * @return true if argument is not {@link Float#NaN}, {@link Float#POSITIVE_INFINITY} or
   *  {@link Float#NEGATIVE_INFINITY}
   */
  public static boolean isFinite(float f) {
    return !(Float.isNaN(f) || Float.isInfinite(f));
  }

  /**
   * @see #parseFloat(String)
   */
  public static double parseDouble(String s) {
    double value = Double.parseDouble(s);
    Preconditions.checkArgument(isFinite(value), "Bad value: %s", value);
    return value;
  }

  /**
   * @return true if argument is not {@link Double#NaN}, {@link Double#POSITIVE_INFINITY} or
   *  {@link Double#NEGATIVE_INFINITY}
   */
  public static boolean isFinite(double d) {
    return !(Double.isNaN(d) || Double.isInfinite(d));
  }


  /**
   * Computes {@code l mod m}, such that the result is always in [0,m-1], for any {@code long}
   * value including negative values.
   *
   * @param l long value
   * @param m modulus
   * @return {@code l mod m} if l is nonnegative, {@code (l mod m) + m} otherwise
   */
  public static int mod(long l, int m) {
    return ((int) (l % m) + m) % m;
  }

}
