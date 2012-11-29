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

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.myrrix.common.random;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;

/**
 * Helpful methods related to randomness and related functions. Some parts derived from Mahout.
 *
 * @author Sean Owen
 * @author Mahout
 */
public final class RandomUtils {

  /** The largest prime less than 2<sup>31</sup>-1 that is the smaller of a twin prime pair. */
  public static final int MAX_INT_SMALLER_TWIN_PRIME = 2147482949;

  private static final MessageDigest MD5_DIGEST;
  static {
    try {
      MD5_DIGEST = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // Can't happen
      throw new IllegalStateException(e);
    }
  }

  private RandomUtils() {
  }

  /**
   * @param dimensions dimensionality of resulting vector
   * @param random random number generator to use
   * @return a vector of length 1 over the given number of dimensions, whose direction is chosen uniformly
   *   at random (that is: a point chosen uniformly at random on the unit hypersphere)
   */
  public static float[] randomUnitVector(int dimensions, RandomGenerator random) {
    float[] vector = new float[dimensions];
    double total = 0.0;
    for (int i = 0; i < dimensions; i++) {
      double d = random.nextGaussian();
      vector[i] = (float) d;
      total += d * d;
    }
    float normalization = (float) FastMath.sqrt(total);
    for (int i = 0; i < dimensions; i++) {
      vector[i] /= normalization;
    }
    return vector;
  }

  /**
   * Finds next-largest "twin primes": numbers p and p+2 such that both are prime. Finds the smallest such p
   * such that the smaller twin, p, is greater than or equal to n. Returns p+2, the larger of the two twins.
   */
  public static int nextTwinPrime(int n) {
    if (n > MAX_INT_SMALLER_TWIN_PRIME) {
      throw new IllegalArgumentException();
    }
    if (n <= 3) {
      return 5;
    }
    int next = nextPrime(n);
    while (isNotPrime(next + 2)) {
      next = nextPrime(next + 4);
    }
    return next + 2;
  }

  /**
   * Finds smallest prime p such that p is greater than or equal to n.
   */
  private static int nextPrime(int n) {
    if (n <= 2) {
      return 2;
    }
    // Make sure the number is odd. Is this too clever?
    n |= 0x1;
    // There is no problem with overflow since Integer.MAX_INT is prime, as it happens
    while (isNotPrime(n)) {
      n += 2;
    }
    return n;
  }

  /** @return {@code true} iff n is not a prime */
  private static boolean isNotPrime(int n) {
    if (n < 2 || (n & 0x1) == 0) { // < 2 or even
      return n != 2;
    }
    int max = 1 + (int) FastMath.sqrt(n);
    for (int d = 3; d <= max; d += 2) {
      if (n % d == 0) {
        return true;
      }
    }
    return false;
  }

  public static long md5HashToLong(long l) {
    byte[] hash;
    synchronized (MD5_DIGEST) {
      for (int i = 0; i < 8; i++) {
        MD5_DIGEST.update((byte) l);
        l >>= 8;
      }
      hash = MD5_DIGEST.digest();
    }
    long result = 0L;
    // Use bottom 8 bytes
    for (int i = 8; i < 16; i++) {
      result = (result << 4) | (hash[i] & 0xFFL);
    }
    return result;
  }

}
