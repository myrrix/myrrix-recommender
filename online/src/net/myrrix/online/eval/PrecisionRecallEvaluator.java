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

package net.myrrix.online.eval;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.MyrrixRecommender;

/**
 * A simple evaluation framework for a recommender, which calculates precision, recall and other
 * basic statistics.
 *
 * @author Sean Owen
 */
public final class PrecisionRecallEvaluator extends AbstractEvaluator {

  private static final Logger log = LoggerFactory.getLogger(PrecisionRecallEvaluator.class);

  private static final double LN2 = FastMath.log(2.0);

  @Override
  protected boolean isSplitTestByPrefValue() {
    return true;
  }

  @Override
  public EvaluationResult evaluate(MyrrixRecommender recommender,
                                   Multimap<Long,RecommendedItem> testData) throws TasteException {

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

      List<RecommendedItem> recs;
      try {
        recs = recommender.recommend(userID, numValues);
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
      double maxScore = 0.0;
      for (int i = 0; i < numRecs; i++) {
        RecommendedItem rec = recs.get(i);
        double value = LN2 / FastMath.log(2.0 + i); // 1 / log_2(1 + (i+1))
        if (valueIDs.contains(rec.getItemID())) {
          intersectionSize++;
          score += value;
        }
        maxScore += value;
      }

      precision.addDatum(numRecs == 0 ? 0.0 : (double) intersectionSize / numRecs);
      recall.addDatum((double) intersectionSize / numValues);
      ndcg.addDatum(maxScore == 0.0 ? 0.0 : score / maxScore);
      double f1 = 2.0 * precision.getAverage() * recall.getAverage() /
          (precision.getAverage() + recall.getAverage());

      if (++count % 1000 == 0) {
        log.info("Precision: {}; Recall: {}; nDCG: {}; F1: {}", precision, recall, ndcg, f1);
      }
    }
    double f1 = 2.0 * precision.getAverage() * recall.getAverage() /
        (precision.getAverage() + recall.getAverage());
    log.info("Precision: {}; Recall: {}; nDCG: {}; F1: {}", precision, recall, ndcg, f1);

    return new IRStatisticsImpl(precision.getAverage(), recall.getAverage(), ndcg.getAverage());
  }

}
