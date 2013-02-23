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
import java.io.IOException;

import com.google.common.collect.Multimap;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.common.MyrrixRecommender;
import net.myrrix.online.RescorerProvider;

/**
 * Interface for implementations which can evaluate a recommender according to some metric or process.
 *
 * @author Sean Owen
 */
public interface Evaluator {
  
  /**
   * Calls {@link #evaluate(MyrrixRecommender, RescorerProvider, Multimap)} without a rescorer.
   */
  EvaluationResult evaluate(MyrrixRecommender recommender,
                            Multimap<Long,RecommendedItem> testData) throws TasteException;
  
  /**
   * Evaluate a given {@link MyrrixRecommender}, already trained with some training data, using the given
   * test data.
   *
   * @param recommender instance to evaluate, which already has training data appropriate for the supplied test
   *  data
   * @param provider optional {@link RescorerProvider} that should be used in evaluation / training data split.
   *  It may or may not be used depending on the test.
   * @param testData test data to use in the evaluation. It is a {@link Multimap} keyed by user ID, pointing
   *  to many {@link RecommendedItem}s, each of which represents a test datum, which would be considered a
   *  good recommendation.
   */
  EvaluationResult evaluate(MyrrixRecommender recommender,
                            RescorerProvider provider,
                            Multimap<Long,RecommendedItem> testData) throws TasteException;
  
  /**
   * Convenience method which sets up a {@link MyrrixRecommender}, splits data in a given location into test/training
   * data, trains the recommender, then invokes {@link #evaluate(MyrrixRecommender, Multimap)}.
   *
   * @param originalDataDir directory containing recommender input data, as (possibly compressed) CSV files
   * @param trainingPercentage percentage of data to train on; the remainder is test data
   * @param evaluationPercentage percentage of all data to consider, before even splitting into test and training
   *  sets. This is useful for quickly evaluating using a subset of a large data set.
   * @param provider optional {@link RescorerProvider} that should be used in evaluation / training data split.
   *  It may or may not be used depending on the test.
   */
  EvaluationResult evaluate(File originalDataDir,
                            double trainingPercentage,
                            double evaluationPercentage,
                            RescorerProvider provider) 
      throws TasteException, IOException, InterruptedException;

}
