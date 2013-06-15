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

import java.io.File;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.LangUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.stats.DoubleWeightedMean;
import net.myrrix.online.RescorerProvider;

/**
 * <p>An alternate evaluation  which computes the average "error" in estimated strength score (see
 * {@link org.apache.mahout.cf.taste.recommender.Recommender#estimatePreference(long, long)}) for each test
 * datum. It simply reports the average -- a weighted average, weighted by the test datum's value -- of the
 * difference between 1.0 and the estimate. An estimate of 1.0 would be good, producing an error of 0.0.
 * We allow the difference to be negative.</p>
 * 
 * <p>This class can be run as a Java program; the single argument is a directory containing test data.
 * The {@link EvaluationResult} is printed to standard out.</p>
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class EstimatedStrengthEvaluator extends AbstractEvaluator {

  private static final Logger log = LoggerFactory.getLogger(EstimatedStrengthEvaluator.class);

  @Override
  protected boolean isSplitTestByPrefValue() {
    return false;
  }

  @Override
  public EvaluationResult evaluate(MyrrixRecommender recommender,
                                   RescorerProvider provider, // ignored
                                   Multimap<Long,RecommendedItem> testData) throws TasteException {
    DoubleWeightedMean score = new DoubleWeightedMean();
    int count = 0;
    for (Map.Entry<Long,RecommendedItem> entry : testData.entries()) {
      long userID = entry.getKey();
      RecommendedItem itemPref = entry.getValue();
      try {
        float estimate = recommender.estimatePreference(userID, itemPref.getItemID());
        Preconditions.checkState(LangUtils.isFinite(estimate));
        score.increment(1.0 - estimate, itemPref.getValue());
      } catch (NoSuchItemException nsie) {
        // continue
      } catch (NoSuchUserException nsue) {
        // continue
      }
      if (++count % 100000 == 0) {
        log.info("Score: {}", score);
      }
    }
    log.info("Score: {}", score);
    return new EvaluationResultImpl(score.getResult());
  }
  
  public static void main(String[] args) throws Exception {
    EstimatedStrengthEvaluator eval = new EstimatedStrengthEvaluator();
    EvaluationResult result = eval.evaluate(new File(args[0]));
    log.info(result.toString());
  }

}
