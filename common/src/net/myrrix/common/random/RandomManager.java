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

import java.util.Map;
import java.util.WeakHashMap;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

/**
 * Manages random number generation. Allows resetting RNGs to a known state for testing. Mostly
 * adapted from Mahout's {@code RandomUtils}.
 *
 * @author Sean Owen
 * @author Mahout
 */
public final class RandomManager {

  private static final long TEST_SEED = 1234567890L;

  private static final Map<RandomGenerator,Boolean> INSTANCES = new WeakHashMap<RandomGenerator,Boolean>();
  private static boolean useTestSeed = false;

  private RandomManager() {
  }

  public static RandomGenerator getRandom() {
    if (useTestSeed) {
      // No need to track instances anymore
      return new MersenneTwister(TEST_SEED);
    }
    RandomGenerator random = new MersenneTwister();
    synchronized (INSTANCES) {
      INSTANCES.put(random, Boolean.TRUE);
    }
    return random;
  }

  public static void useTestSeed() {
    useTestSeed = true;
    synchronized (INSTANCES) {
      for (RandomGenerator random : INSTANCES.keySet()) {
        random.setSeed(TEST_SEED);
      }
      INSTANCES.clear();
    }
  }

}
