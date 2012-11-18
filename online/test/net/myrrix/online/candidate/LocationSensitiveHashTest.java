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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.MyrrixTest;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.math.SimpleVectorMath;
import net.myrrix.common.random.RandomManager;
import net.myrrix.common.random.RandomUtils;

public final class LocationSensitiveHashTest extends MyrrixTest {

  private static final Logger log = LoggerFactory.getLogger(LocationSensitiveHashTest.class);

  private static final int NUM_FEATURES = 50;
  private static final int NUM_ITEMS = 2000000;
  private static final int NUM_RECS = 10;
  private static final int ITERATIONS = 20;
  private static final double LN2 = FastMath.log(2.0);

  @Test
  public void testLSH() {
    System.setProperty("model.lsh.sampleRatio", "0.1");
    System.setProperty("model.lsh.numHashes", "20");
    RandomGenerator random = RandomManager.getRandom();

    RunningAverage avgPercentTopRecsConsidered = new FullRunningAverage();
    RunningAverage avgNDCG = new FullRunningAverage();
    RunningAverage avgPercentAllItemsConsidered= new FullRunningAverage();

    for (int iteration = 0; iteration < ITERATIONS; iteration++) {

      FastByIDMap<float[]> Y = new FastByIDMap<float[]>();
      for (int i = 0; i < NUM_ITEMS; i++) {
        Y.put(i, RandomUtils.randomUnitVector(NUM_FEATURES, random));
      }
      float[] userVec = RandomUtils.randomUnitVector(NUM_FEATURES, random);

      double[] results = doTestRandomVecs(Y, userVec);
      double percentTopRecsConsidered = results[0];
      double ndcg = results[1];
      double percentAllItemsConsidered = results[2];

      log.info("Considered {}% of all candidates, {} nDCG, got {}% recommendations correct",
               100 * percentAllItemsConsidered,
               ndcg,
               100 * percentTopRecsConsidered);

      avgPercentTopRecsConsidered.addDatum(percentTopRecsConsidered);
      avgNDCG.addDatum(ndcg);
      avgPercentAllItemsConsidered.addDatum(percentAllItemsConsidered);
    }

    log.info(avgPercentTopRecsConsidered.toString());
    log.info(avgNDCG.toString());
    log.info(avgPercentAllItemsConsidered.toString());

    assertTrue(avgPercentTopRecsConsidered.getAverage() > 0.65);
    assertTrue(avgNDCG.getAverage() > 0.65);
    assertTrue(avgPercentAllItemsConsidered.getAverage() < 0.07);
  }

  private static double[] doTestRandomVecs(FastByIDMap<float[]> Y, float[] userVec) {

    CandidateFilter lsh = new LocationSensitiveHash(Y);

    FastIDSet candidates = new FastIDSet();
    float[][] userVecs = { userVec };
    for (Iterator<FastByIDMap.MapEntry<float[]>> candidatesIterator : lsh.getCandidateIterator(userVecs)) {
      while (candidatesIterator.hasNext()) {
        candidates.add(candidatesIterator.next().getKey());
      }
    }

    List<Long> topIDs = findTopRecommendations(Y, userVec);

    double score = 0.0;
    double maxScore = 0.0;
    int intersectionSize = 0;
    for (int i = 0; i < topIDs.size(); i++) {
      double value = LN2 / FastMath.log(2.0 + i);
      long id = topIDs.get(i);
      if (candidates.contains(id)) {
        intersectionSize++;
        score += value;
      }
      maxScore += value;
    }

    double percentTopRecsConsidered = (double) intersectionSize / topIDs.size();
    double ndcg = maxScore == 0.0 ? 0.0 : score / maxScore;
    double percentAllItemsConsidered = (double) candidates.size() / Y.size();

    return new double[] {percentTopRecsConsidered, ndcg, percentAllItemsConsidered};
  }

  private static List<Long> findTopRecommendations(FastByIDMap<float[]> Y, float[] userVec) {
    SortedMap<Double,Long> allScores = Maps.newTreeMap(Collections.reverseOrder());
    for (FastByIDMap.MapEntry<float[]> entry : Y.entrySet()) {
      double dot = SimpleVectorMath.dot(entry.getValue(), userVec);
      allScores.put(dot, entry.getKey());
    }
    List<Long> topRecommendations = Lists.newArrayList();
    for (Map.Entry<Double,Long> entry : allScores.entrySet()) {
      topRecommendations.add(entry.getValue());
      if (topRecommendations.size() == NUM_RECS) {
        return topRecommendations;
      }
    }
    return topRecommendations;
  }

}
