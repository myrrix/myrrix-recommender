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

package net.myrrix.client.eval;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.client.ClientRecommender;

/**
 * A simple evaluation framework for a recommender. It accepts a simple CSV input file, uses most of the
 * data to train a recommender, then tests with the remainder. It reports precision, recall and other
 * basic statistics.
 *
 * @author Sean Owen
 */
public final class PrecisionRecallEvaluator extends AbstractEvaluator {

  private static final Logger log = LoggerFactory.getLogger(PrecisionRecallEvaluator.class);

  private final Multimap<Long,RecommendedItem> testData;

  /**
   * Like {@link #PrecisionRecallEvaluator(File, File, double, double)} but chooses a system temp directory.
   */
  public PrecisionRecallEvaluator(File originalDataFile,
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
   */
  public PrecisionRecallEvaluator(File originalDataFile,
                                  File tempDataDir,
                                  double trainingPercentage,
                                  double evaluationPercentage) throws IOException {
    super(tempDataDir);
    Preconditions.checkArgument(trainingPercentage > 0.0 && trainingPercentage < 1.0);
    Preconditions.checkArgument(evaluationPercentage > 0.0 && evaluationPercentage <= 1.0);
    File trainingFile = new File(tempDataDir, "training.csv");
    testData = split(originalDataFile, trainingFile, trainingPercentage, evaluationPercentage, true);
  }

  @Override
  EvaluationResult doEvaluate(ClientRecommender client) throws TasteException {

    RunningAverage precision = new FullRunningAverage();
    RunningAverage recall = new FullRunningAverage();
    RunningAverage ndcg = new FullRunningAverage();

    int count = 0;
    for (Long userID : testData.keySet()) {

      Collection<RecommendedItem> values = testData.get(userID);
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

      Collection<Long> valueIDs = Sets.newHashSet();
      for (RecommendedItem rec : values) {
        valueIDs.add(rec.getItemID());
      }

      int intersectionSize = 0;
      double score = 0.0;
      for (int i = 0; i < numRecs; i++) {
        RecommendedItem rec = recs.get(i);
        if (valueIDs.contains(rec.getItemID())) {
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

}
