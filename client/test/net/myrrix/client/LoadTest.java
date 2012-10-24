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

package net.myrrix.client;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.PatternFilenameFilter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverageAndStdDev;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.common.iterator.FileLineIterable;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoadTest extends AbstractClientTest {

  private static final Logger log = LoggerFactory.getLogger(LoadTest.class);

  private static final int ITERATIONS = 20000;

  @Override
  protected String getTestDataPath() {
    return "testdata/libimseti";
  }

  @Override
  protected boolean useSecurity() {
    return true;
  }

  @Test
  public void testLoad() throws Exception {

    log.info("Reading IDs...");

    FastIDSet userIDsSet = new FastIDSet();
    FastIDSet itemIDsSet = new FastIDSet();
    Splitter comma = Splitter.on(',');
    for (File f : getTestTempDir().listFiles(new PatternFilenameFilter(".+\\.csv(\\.(zip|gz))?"))) {
      for (CharSequence line : new FileLineIterable(f)) {
        Iterator<String> it = comma.split(line).iterator();
        userIDsSet.add(Long.parseLong(it.next()));
        itemIDsSet.add(Long.parseLong(it.next()));
      }
    }
    long[] uniqueUserIDs = userIDsSet.toArray();
    long[] uniqueItemIDs = itemIDsSet.toArray();

    Random random = RandomUtils.getRandom();
    final ClientRecommender client = getClient();

    ExecutorService executor =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
                                     new ThreadFactoryBuilder().setDaemon(true).setNameFormat("LoadTest-%d").build());
    Collection<Future<?>> futures = Lists.newArrayList();

    final RunningAverage recommendedBecause = new FullRunningAverageAndStdDev();
    final RunningAverage setPreference = new FullRunningAverageAndStdDev();
    final RunningAverage removePreference = new FullRunningAverageAndStdDev();
    final RunningAverage estimatePreference = new FullRunningAverageAndStdDev();
    final RunningAverage mostSimilarItems = new FullRunningAverageAndStdDev();
    final RunningAverage recommendToMany = new FullRunningAverageAndStdDev();
    final RunningAverage recommend = new FullRunningAverageAndStdDev();

    final AtomicInteger count = new AtomicInteger();

    log.info("Starting load test...");

    long start = System.currentTimeMillis();

    for (int i = 0; i < ITERATIONS; i++) {
      final double r = random.nextDouble();
      final long userID = uniqueUserIDs[random.nextInt(uniqueUserIDs.length)];
      final long itemID = uniqueItemIDs[random.nextInt(uniqueItemIDs.length)];
      final float value = (float) random.nextInt(10);
      futures.add(executor.submit(new Callable<Void>() {
        @Override
        public Void call() throws TasteException {

          long stepStart = System.currentTimeMillis();
          if (r < 0.05) {
            client.recommendedBecause(userID, itemID, 10);
            recommendedBecause.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.07) {
            client.setPreference(userID, itemID);
            setPreference.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.1) {
            client.setPreference(userID, itemID, value);
            setPreference.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.11) {
            client.removePreference(userID, itemID);
            removePreference.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.20) {
            client.estimatePreference(userID, itemID);
            estimatePreference.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.25) {
            client.mostSimilarItems(new long[] {itemID}, 10);
            mostSimilarItems.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.30) {
            client.recommendToMany(new long[] { userID, userID }, 10, true, null);
            recommendToMany.addDatum(System.currentTimeMillis() - stepStart);
          } else {
            client.recommend(userID, 10);
            recommend.addDatum(System.currentTimeMillis() - stepStart);
          }

          int stepsFinished = count.incrementAndGet();
          if (stepsFinished % 1000 == 0) {
            log.info("Finished {} load steps", stepsFinished);
          }

          return null;
        }
      }));
    }

    for (Future<?> future : futures) {
      try {
        future.get();
      } catch (ExecutionException ee) {
        log.warn("Error in execution", ee.getCause());
      }
    }

    executor.shutdown();

    long end = System.currentTimeMillis();
    log.info("Finished {} steps in {}ms", ITERATIONS, end - start);

    log.info("recommendedBecause: {}", recommendedBecause);
    log.info("setPreference: {}", setPreference);
    log.info("removePreference: {}", removePreference);
    log.info("estimatePreference: {}", estimatePreference);
    log.info("mostSimilarItems: {}", mostSimilarItems);
    log.info("recommendToMany: {}", recommendToMany);
    log.info("recommend: {}", recommend);

    assertTrue(end - start < 15 * ITERATIONS);
  }

}
