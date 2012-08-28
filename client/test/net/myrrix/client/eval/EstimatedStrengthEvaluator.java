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
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.WeightedRunningAverage;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.client.ClientRecommender;

/**
 * An alternate evaluation scheme which computes the estimated strength score (see
 * {@link org.apache.mahout.cf.taste.recommender.Recommender#estimatePreference(long, long)}) for each test
 * datum. It simply reports the average -- a weighted average, weighted by the test datum's value.
 *
 * @author Sean Owen
 */
public final class EstimatedStrengthEvaluator extends AbstractEvaluator {

  private static final Logger log = LoggerFactory.getLogger(EstimatedStrengthEvaluator.class);

  private final Multimap<Long,RecommendedItem> testData;

  /**
   * Like {@link #EstimatedStrengthEvaluator(File, File, double, double)} but chooses a system temp directory.
   */
  public EstimatedStrengthEvaluator(File originalDataFile,
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
  public EstimatedStrengthEvaluator(File originalDataFile,
                                    File tempDataDir,
                                    double trainingPercentage,
                                    double evaluationPercentage) throws IOException {
    super(tempDataDir);
    Preconditions.checkArgument(trainingPercentage > 0.0 && trainingPercentage < 1.0);
    Preconditions.checkArgument(evaluationPercentage > 0.0 && evaluationPercentage <= 1.0);
    File trainingFile = new File(tempDataDir, "training.csv");
    testData = split(originalDataFile, trainingFile, trainingPercentage, evaluationPercentage, false);
  }

  @Override
  EvaluationResult doEvaluate(ClientRecommender client) throws TasteException {
    WeightedRunningAverage score = new WeightedRunningAverage();
    int count = 0;
    int unknownItems = 0;
    int unknownUsers = 0;
    for (Map.Entry<Long,RecommendedItem> entry : testData.entries()) {
      long userID = entry.getKey();
      RecommendedItem itemPref = entry.getValue();
      try {
        float estimate = client.estimatePreference(userID, itemPref.getItemID());
        Preconditions.checkState(!Float.isNaN(estimate));
        score.addDatum(estimate, itemPref.getValue());
      } catch (NoSuchItemException nsie) {
        unknownItems++;
        // continue
      } catch (NoSuchUserException nsue) {
        unknownUsers++;
        // continue
      }
      if (++count % 10000 == 0) {
        log.info("Score: {}", score);
      }
    }
    log.info("{} unknown items, {} unknown users", unknownItems, unknownUsers);
    return new EvaluationResultImpl(score.getAverage());
  }

}
