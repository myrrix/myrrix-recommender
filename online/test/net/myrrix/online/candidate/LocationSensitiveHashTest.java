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

package net.myrrix.online.candidate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.Maps;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.common.RandomUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.MyrrixTest;
import net.myrrix.common.SimpleVectorMath;
import net.myrrix.common.collection.FastByIDMap;

public final class LocationSensitiveHashTest extends MyrrixTest {

  private static final Logger log = LoggerFactory.getLogger(LocationSensitiveHashTest.class);

  private static final int NUM_FEATURES = 30;
  private static final int NUM_ITEMS = 2000000;
  private static final int NUM_RECS = 20;

  @Test
  public void testLSH() {
    Random r = RandomUtils.getRandom();
    AtomicInteger intersection = new AtomicInteger();
    AtomicInteger items = new AtomicInteger();
    AtomicInteger evaluated = new AtomicInteger();
    AtomicInteger total = new AtomicInteger();

    int iterations = 10000000 / NUM_ITEMS;
    for (int iteration = 0; iteration < iterations; iteration++) {
      FastByIDMap<float[]> Y = new FastByIDMap<float[]>();
      for (int i = 0; i < NUM_ITEMS; i++) {
        Y.put(i, randomVector(r));
      }
      float[] userVec = randomVector(r);
      doTestRandomVecs(Y, userVec, intersection, items, evaluated, total);
    }

    log.info("Considered {}% of all candidates, got {}% recommendations correct",
             100 * evaluated.get() / total.get(),
             100 * intersection.get() / items.get());
    assertTrue(intersection.get() >= 0.99 * items.get());
    assertTrue(evaluated.get() <= 0.33 * total.get());
  }

  private static float[] randomVector(Random r) {
    float[] vec = new float[NUM_FEATURES];
    for (int j = 0; j < NUM_FEATURES; j++) {
      vec[j] = r.nextFloat() - 0.5f;
    }
    return vec;
  }

  private static void doTestRandomVecs(FastByIDMap<float[]> Y,
                                       float[] userVec,
                                       AtomicInteger intersectionSum,
                                       AtomicInteger itemSum,
                                       AtomicInteger evaluatedSum,
                                       AtomicInteger totalSum) {

    CandidateFilter lsh = new LocationSensitiveHash(Y);

    Iterator<FastByIDMap.MapEntry<float[]>> candidatesIterator = lsh.getCandidateIterator(new float[][]{userVec});
    FastIDSet candidates = new FastIDSet();
    while (candidatesIterator.hasNext()) {
      candidates.add(candidatesIterator.next().getKey());
    }

    Collection<Long> topIDs = findTopRecommendations(Y, userVec);

    int intersectionSize = 0;
    for (long id : topIDs) {
      if (candidates.contains(id)) {
        intersectionSize++;
      }
    }

    intersectionSum.addAndGet(intersectionSize);
    itemSum.addAndGet(topIDs.size());

    int evaluated = 0;
    int total = 0;
    for (FastByIDMap.MapEntry<float[]> entry : Y.entrySet()) {
      if (candidates.contains(entry.getKey())) {
        evaluated++;
      }
      total++;
    }

    evaluatedSum.addAndGet(evaluated);
    totalSum.addAndGet(total);
  }

  private static Collection<Long> findTopRecommendations(FastByIDMap<float[]> Y, float[] userVec) {
    SortedMap<Double,Long> allScores = Maps.newTreeMap(Collections.reverseOrder());
    for (FastByIDMap.MapEntry<float[]> entry : Y.entrySet()) {
      double dot = SimpleVectorMath.dot(entry.getValue(), userVec);
      allScores.put(dot, entry.getKey());
    }
    Collection<Long> topRecommendations = new ArrayList<Long>();
    for (Map.Entry<Double,Long> entry : allScores.entrySet()) {
      topRecommendations.add(entry.getValue());
      if (topRecommendations.size() == NUM_RECS) {
        return topRecommendations;
      }
    }
    return topRecommendations;
  }

}
