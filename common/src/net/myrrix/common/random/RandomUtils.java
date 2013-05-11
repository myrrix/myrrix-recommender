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
import java.util.List;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.primes.Primes;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;

import net.myrrix.common.LangUtils;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.collection.SamplingLongPrimitiveIterator;
import net.myrrix.common.math.SimpleVectorMath;

/**
 * Helpful methods related to randomness and related functions. Some parts derived from Mahout.
 *
 * @author Sean Owen
 * @author Mahout
 * @since 1.0
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
    doRandomUnitVector(vector, random);
    return vector;
  }
  
  private static void doRandomUnitVector(float[] vector, RandomGenerator random) {
    int dimensions = vector.length;
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
  }

  /**
   * @param dimensions dimensionality of resulting vector
   * @param farFrom vectors that the chosen vector should be "far from" -- not in the same direction as
   * @param random random number generator to use
   * @return a vector of length 1 over the given number of dimensions, whose direction is chosen uniformly
   *   at random (that is: a point chosen uniformly at random on the unit hypersphere), but preferring
   *   those not in the same direction as a set of existing vectors
   */
  public static float[] randomUnitVectorFarFrom(int dimensions,
                                                List<float[]> farFrom,
                                                RandomGenerator random) {
    int size = farFrom.size();
    int numSamples = FastMath.min(100, size);
    float[] vector = new float[dimensions];
    boolean accepted = false;
    while (!accepted) {
      doRandomUnitVector(vector, random);
      double smallestDistSquared = Double.POSITIVE_INFINITY;
      for (int sample = 0; sample < numSamples; sample++) {
        float[] other = farFrom.get(size == numSamples ? sample : random.nextInt(size));
        // dot is the cosine here since both are unit vectors
        double distSquared = 2.0 - 2.0 * SimpleVectorMath.dot(vector, other);
        if (LangUtils.isFinite(distSquared) && distSquared < smallestDistSquared) {
          smallestDistSquared = distSquared;
        }
      }
      // Second condition covers 1-D case, where there are only 2 distinct unit vectors. If both have
      // been generated, keep accepting either of them.
      if (LangUtils.isFinite(smallestDistSquared) && !(dimensions == 1 && smallestDistSquared == 0.0)) {
        // Choose with probability proportional to squared distance, a la kmeans++ centroid selection
        double acceptProbability = smallestDistSquared / 4.0; // dist squared is in [0,4]
        accepted = random.nextDouble() < acceptProbability;
      } else {
        // kind of a default
        accepted = true;
      }
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
    int next = Primes.nextPrime(n);
    while (!Primes.isPrime(next + 2)) {
      next = Primes.nextPrime(next + 4);
    }
    return next + 2;
  }

  /**
   * @param l long to MD5 hash
   * @return the bottom 8 bytes, as a {@link long}, of the MD5 hash of the given {@link long},
   *  which is itself treated as a big-endian sequence of 8 bytes
   */
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

  /**
   * @param set to choose from
   * @param random random number generator
   * @return element of the set chosen uniformly at random
   */
  public static int randomFrom(FastIDSet set, RandomGenerator random) {
    int size = set.size();
    Preconditions.checkArgument(size > 0, "Empty set");
    LongPrimitiveIterator it = set.iterator();
    it.skip(random.nextInt(size));
    return (int) it.nextLong();
  }

  /**
   * @param n approximate number of items to choose
   * @param stream stream to choose from randomly
   * @param streamSize (approximate) stream size
   * @param random random number generator
   * @return up to n elements chosen uninformly at random from the stream
   */
  public static long[] chooseAboutNFromStream(int n, 
                                              LongPrimitiveIterator stream,
                                              int streamSize, 
                                              RandomGenerator random) {
    LongPrimitiveIterator it;
    if (n < streamSize) {
      it = new SamplingLongPrimitiveIterator(random, stream, (double) n / streamSize);      
    } else {
      it = stream;
    }
    FastIDSet chosen = new FastIDSet(n);    
    while (it.hasNext()) {
      chosen.add(it.nextLong());
    }
    return chosen.toArray();
  }

}
