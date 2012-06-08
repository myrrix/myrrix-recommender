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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.IRStatistics;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.impl.recommender.GenericRecommendedItem;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.common.iterator.FileLineIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.web.Runner;
import net.myrrix.web.RunnerConfiguration;

/**
 * A simple evaluation framework for a recommender. It accepts a simple CSV input file, uses most of the
 * data to train a recommender, then tests with the remainder. It reports precision, recall and other
 * basic statistics.
 *
 * @author Sean Owen
 */
public final class Evaluator {

  private static final Logger log = LoggerFactory.getLogger(Evaluator.class);

  private static final Splitter COMMA_TAB_SPLIT = Splitter.on(CharMatcher.anyOf(",\t")).omitEmptyStrings();
  private static final char DELIMITER = ',';
  //private static final int AT = 10;

  private final File trainingDataDir;
  private final Multimap<Long,Long> testData;

  /**
   * Like {@link #Evaluator(File, File, double, double)} but chooses a system temp directory.
   */
  public Evaluator(File originalDataFile,
                   double trainingPercentage,
                   double evaluationPercentage) throws IOException {
    this(originalDataFile, Files.createTempDir(), trainingPercentage, evaluationPercentage);
  }

  /**
   * @param originalDataFile test data input file, in CSV format
   * @param tempDataDir other temporary data directory to use
   * @param trainingPercentage percentage of data to use for training; the remainder is used for the test
   * @param evaluationPercentage percentage of all data to use for either training or test; used to
   *   make a smaller, faster test over a large data set
   * @throws IOException
   */
  public Evaluator(File originalDataFile,
                   File tempDataDir,
                   double trainingPercentage,
                   double evaluationPercentage) throws IOException {
    Preconditions.checkArgument(trainingPercentage > 0.0 && trainingPercentage < 1.0);
    Preconditions.checkArgument(evaluationPercentage > 0.0 && evaluationPercentage <= 1.0);
    this.trainingDataDir = tempDataDir;
    File trainingFile = new File(tempDataDir, "training.csv");
    testData = split(originalDataFile, trainingFile, trainingPercentage, evaluationPercentage);
  }

  /**
   * Runs a recommender locally on port 8080 and conducts a precision/recall test against it.
   *
   * @return {@link IRStatistics} reporting precision, recall, and nDCG results of the test
   */
  public IRStatistics evaluate() throws TasteException {

    RunnerConfiguration runnerConfig = new RunnerConfiguration();
    runnerConfig.setInstanceID(0L);
    runnerConfig.setPort(8080);
    runnerConfig.setLocalInputDir(trainingDataDir);

    Runner runner = new Runner(runnerConfig);

    int clientPort = runnerConfig.getPort();
    MyrrixClientConfiguration clientConfig = new MyrrixClientConfiguration();
    clientConfig.setHost("localhost");
    clientConfig.setPort(clientPort);

    try {
      runner.call();
      return doEvaluate(new ClientRecommender(clientConfig));
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    } finally {
      runner.close();
    }
  }

  private IRStatistics doEvaluate(ClientRecommender client) throws TasteException {

    RunningAverage precision = new FullRunningAverage();
    RunningAverage recall = new FullRunningAverage();
    RunningAverage ndcg = new FullRunningAverage();

    int count = 0;
    for (Long userID : testData.keySet()) {

      Collection<Long> values = testData.get(userID);
      int numValues = values.size();
      if (numValues == 0) {
        continue;
      }
      //if (numValues < 2 * AT) {
      //  continue; // Like in Mahout; too little data to really evaluate
      //}
      List<RecommendedItem> recs;
      try {
        recs = client.recommend(userID, numValues);
      } catch (NoSuchUserException nsue) {
        // Probably OK, just removed all data for this user from training
        log.warn("User only in test data: {}", userID);
        continue;
      }
      int numRecs = recs.size();

      int intersectionSize = 0;
      double score = 0.0;
      for (int i = 0; i < numRecs; i++) {
        RecommendedItem rec = recs.get(i);
        if (values.contains(rec.getItemID())) {
          intersectionSize++;
          score += 1.0 / (1 << (i+1));
        }
      }
      double maxScore = 1.0 - 1.0 / (1 << numRecs);

      precision.addDatum(numRecs == 0 ? 0.0 : (double) intersectionSize / numRecs);
      recall.addDatum((double) intersectionSize / numValues);
      ndcg.addDatum(maxScore == 0.0 ? 0.0 : score / maxScore);

      if (++count % 1000 == 0) {
        log.info("Precision: {}; Recall: {}; nDCG: {}", new Object[] {precision, recall, ndcg});
      }
    }
    log.info("Precision: {}; Recall: {}; nDCG: {}", new Object[] {precision, recall, ndcg});

    return new IRStatisticsImpl(precision.getAverage(), recall.getAverage(), ndcg.getAverage());
  }

  private static Multimap<Long,Long> split(File dataFile,
                                           File trainingFile,
                                           double trainPercentage,
                                           double evaluationPercentage) throws IOException {
    Multimap<Long,RecommendedItem> data = readDataFile(dataFile, evaluationPercentage);
    log.info("Read data for {} users from input; splitting...", data.size());

    Multimap<Long,Long> testData = HashMultimap.create();
    Writer trainingOut =
        new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(trainingFile)), Charsets.UTF_8);
    try {
      for (Map.Entry<Long,Collection<RecommendedItem>> entry : data.asMap().entrySet()) {

        long userID = entry.getKey();
        List<RecommendedItem> userPrefs = Lists.newArrayList(entry.getValue());
        // Sort low to high
        Collections.sort(userPrefs, new Comparator<RecommendedItem>() {
          @Override
          public int compare(RecommendedItem a, RecommendedItem b) {
            return a.getValue() < b.getValue() ? -1 : a.getValue() > b.getValue() ? 1 : 0;
          }
        });

        int numTraining = (int) (trainPercentage * userPrefs.size());
        for (RecommendedItem rec : userPrefs.subList(0, numTraining)) {
          trainingOut.write(String.valueOf(userID) + DELIMITER + rec.getItemID() + DELIMITER + rec.getValue() + '\n');
        }
        // Highest ratings are at end -- take those as test
        for (RecommendedItem rec : userPrefs.subList(numTraining, userPrefs.size())) {
          testData.put(userID, rec.getItemID());
        }

      }
      //trainingOut.flush();
    } finally {
      Closeables.closeQuietly(trainingOut);
    }

    return testData;
  }

  private static Multimap<Long,RecommendedItem> readDataFile(File dataFile, double evaluationPercentage) throws IOException {
    // evaluationPercentage filters per user and item, not per datum, since time scales with users and
    // items. We select sqrt(evaluationPercentage) of users and items to overall select about evaluationPercentage
    // of all data.
    int perMillion = (int) (1000000 * Math.sqrt(evaluationPercentage));

    int count = 0;

    Multimap<Long,RecommendedItem> data = HashMultimap.create();
    for (CharSequence line : new FileLineIterable(dataFile)) {
      Iterator<String> parts = COMMA_TAB_SPLIT.split(line).iterator();
      String userIDString = parts.next();
      if (userIDString.hashCode() % 1000000 <= perMillion) {
        String itemIDString = parts.next();
        if (itemIDString.hashCode() % 1000000 <= perMillion) {
          long userID = Long.parseLong(userIDString);
          long itemID = Long.parseLong(itemIDString);
          float value = parts.hasNext() ? Float.parseFloat(parts.next()) : 1.0f;
          data.put(userID, new GenericRecommendedItem(itemID, value));
        }
      }
      if (++count % 100000 == 0) {
        log.info("Finished {} lines", count);
      }
    }
    return data;
  }

}
