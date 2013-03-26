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

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.PatternFilenameFilter;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverageAndStdDev;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.common.iterator.FileLineIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.collection.CountingIterator;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.parallel.Paralleler;
import net.myrrix.common.parallel.Processor;
import net.myrrix.common.random.RandomManager;

/**
 * Runs a mixed, concurrent load against a given recommender instance. This could be a 
 * {@code ClientRecommender} configured to access a remote instance.
 * 
 * @author Sean Owen
 */
public final class LoadRunner implements Callable<Void> {
  
  private static final Logger log = LoggerFactory.getLogger(LoadRunner.class);
  
  private final MyrrixRecommender client;
  private final long[] uniqueUserIDs;
  private final long[] uniqueItemIDs;
  private final int steps;

  /**
   * @param client recommender to load
   * @param dataDirectory a directory containing data files from which user and item IDs should be read
   * @param steps number of load steps to run
   */
  public LoadRunner(MyrrixRecommender client, File dataDirectory, int steps) throws IOException {
    Preconditions.checkNotNull(client);
    Preconditions.checkNotNull(dataDirectory);
    Preconditions.checkArgument(steps > 0);  
    
    log.info("Reading IDs...");    
    FastIDSet userIDsSet = new FastIDSet();
    FastIDSet itemIDsSet = new FastIDSet();
    Splitter comma = Splitter.on(',');
    for (File f : dataDirectory.listFiles(new PatternFilenameFilter(".+\\.csv(\\.(zip|gz))?"))) {
      for (CharSequence line : new FileLineIterable(f)) {
        Iterator<String> it = comma.split(line).iterator();
        userIDsSet.add(Long.parseLong(it.next()));
        itemIDsSet.add(Long.parseLong(it.next()));
      }
    }
    
    this.client = client;    
    this.uniqueUserIDs = userIDsSet.toArray();
    this.uniqueItemIDs = itemIDsSet.toArray();
    this.steps = steps;
  }
  
  /**
   * @param client recommender to load
   * @param uniqueUserIDs user IDs which may be used in test calls
   * @param uniqueItemIDs item IDs which may be used in test calls
   * @param steps number of load steps to run
   */
  public LoadRunner(MyrrixRecommender client, long[] uniqueUserIDs, long[] uniqueItemIDs, int steps) {
    Preconditions.checkNotNull(client);
    Preconditions.checkNotNull(uniqueItemIDs);
    Preconditions.checkNotNull(uniqueItemIDs);
    Preconditions.checkArgument(steps > 0, "steps must be positive: {}", steps);
    this.client = client;
    this.uniqueUserIDs = uniqueUserIDs;
    this.uniqueItemIDs = uniqueItemIDs;
    this.steps = steps;
  }

  public int getSteps() {
    return steps;
  }

  @Override
  public Void call() throws Exception {
    runLoad();
    return null;
  }
  
  public void runLoad() throws ExecutionException, InterruptedException {

    final RunningAverage recommendedBecause = new FullRunningAverageAndStdDev();
    final RunningAverage setPreference = new FullRunningAverageAndStdDev();
    final RunningAverage removePreference = new FullRunningAverageAndStdDev();
    final RunningAverage setTag = new FullRunningAverageAndStdDev();
    final RunningAverage ingest = new FullRunningAverageAndStdDev();
    final RunningAverage refresh = new FullRunningAverageAndStdDev();
    final RunningAverage estimatePreference = new FullRunningAverageAndStdDev();
    final RunningAverage mostSimilarItems = new FullRunningAverageAndStdDev();
    final RunningAverage similarityToItem = new FullRunningAverageAndStdDev(); 
    final RunningAverage mostPopularItems = new FullRunningAverageAndStdDev();
    final RunningAverage recommendToMany = new FullRunningAverageAndStdDev();
    final RunningAverage recommend = new FullRunningAverageAndStdDev();

    Processor<Integer> processor = new Processor<Integer>() {
      private final RandomGenerator random = RandomManager.getRandom();      
      @Override
      public void process(Integer step, long count) {
        double r;
        long userID;
        long itemID;
        long itemID2;
        float value;
        synchronized (random) {
          r = random.nextDouble();            
          userID = uniqueUserIDs[random.nextInt(uniqueUserIDs.length)];
          itemID = uniqueItemIDs[random.nextInt(uniqueItemIDs.length)];
          itemID2 = uniqueItemIDs[random.nextInt(uniqueItemIDs.length)];
          value = random.nextInt(10);
        }
        long stepStart = System.currentTimeMillis();
        try {
          if (r < 0.05) {
            client.recommendedBecause(userID, itemID, 10);
            recommendedBecause.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.07) {
            client.setPreference(userID, itemID);
            setPreference.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.08) {
            client.setPreference(userID, itemID, value);
            setPreference.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.09) {
            client.setUserTag(userID, Long.toString(itemID));
            setTag.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.10) {
            client.setItemTag(Long.toString(userID), itemID);
            setTag.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.11) {
            client.removePreference(userID, itemID);
            removePreference.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.12) {
            StringReader reader = new StringReader(userID + "," + itemID + ',' + value + '\n');
            client.ingest(reader);
            ingest.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.13) {
            client.refresh();
            refresh.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.14) {
            client.similarityToItem(itemID, itemID2);
            similarityToItem.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.15) {
            client.mostPopularItems(10);
            mostPopularItems.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.19) {
            client.estimatePreference(userID, itemID);
            estimatePreference.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.20) {
            client.estimateForAnonymous(itemID, new long[] {itemID2});
            estimatePreference.addDatum(System.currentTimeMillis() - stepStart);          
          } else if (r < 0.25) {
            client.mostSimilarItems(new long[]{itemID}, 10);
            mostSimilarItems.addDatum(System.currentTimeMillis() - stepStart);
          } else if (r < 0.30) {
            client.recommendToMany(new long[] { userID, userID }, 10, true, null);
            recommendToMany.addDatum(System.currentTimeMillis() - stepStart);
          } else {
            client.recommend(userID, 10);
            recommend.addDatum(System.currentTimeMillis() - stepStart);
          }
        } catch (TasteException te) {
          log.warn("Error during request", te);
        }
        if (count % 1000 == 0) {
          log.info("Finished {} load steps", count);
        }
      }
    };

    log.info("Starting load test...");
    long start = System.currentTimeMillis();
    new Paralleler<Integer>(new CountingIterator(steps), processor, "Load").runInParallel();
    long end = System.currentTimeMillis();

    log.info("Finished {} steps in {}ms", steps, end - start);

    log.info("recommendedBecause: {}", recommendedBecause);
    log.info("setPreference: {}", setPreference);
    log.info("removePreference: {}", removePreference);
    log.info("setTag: {}", setTag);
    log.info("ingest: {}", ingest);
    log.info("refresh: {}", refresh);
    log.info("estimatePreference: {}", estimatePreference);
    log.info("mostSimilarItems: {}", mostSimilarItems);
    log.info("similarityToItem: {}", similarityToItem);
    log.info("mostPopularItems: {}", mostPopularItems);        
    log.info("recommendToMany: {}", recommendToMany);
    log.info("recommend: {}", recommend);    
  }

}
