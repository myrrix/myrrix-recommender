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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.parallel.Paralleler;
import net.myrrix.common.parallel.Processor;
import net.myrrix.online.RescorerProvider;

/**
 * <p>A simple evaluation framework for a recommender, which calculates precision, recall, F1,
 * mean average precision, and other basic statistics.</p>
 * 
 * <p>This class can be run as a Java program; the single argument is a directory containing test data.
  * The {@link EvaluationResult} is printed to standard out.</p>
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class PrecisionRecallEvaluator extends AbstractEvaluator {

  private static final Logger log = LoggerFactory.getLogger(PrecisionRecallEvaluator.class);

  private static final double LN2 = Math.log(2.0);

  @Override
  protected boolean isSplitTestByPrefValue() {
    return true;
  }

  @Override
  public EvaluationResult evaluate(final MyrrixRecommender recommender,
                                   final RescorerProvider provider,
                                   final Multimap<Long,RecommendedItem> testData) throws TasteException {

    final Mean precision = new Mean();
    final Mean recall = new Mean();
    final Mean ndcg = new Mean();
    final Mean meanAveragePrecision = new Mean();
    
    Processor<Long> processor = new Processor<Long>() {
      @Override
      public void process(Long userID, long count) {
        
        Collection<RecommendedItem> values = testData.get(userID);
        int numValues = values.size();
        if (numValues == 0) {
          return;
        }
        
        IDRescorer rescorer = 
            provider == null ? null : provider.getRecommendRescorer(new long[]{userID}, recommender);
  
        List<RecommendedItem> recs;
        try {
          recs = recommender.recommend(userID, numValues, rescorer);
        } catch (NoSuchUserException nsue) {
          // Probably OK, just removed all data for this user from training
          log.warn("User only in test data: {}", userID);
          return;
        } catch (TasteException te) {
          log.warn("Unexpected exception", te);
          return;          
        }
        int numRecs = recs.size();
  
        Collection<Long> valueIDs = Sets.newHashSet();
        for (RecommendedItem rec : values) {
          valueIDs.add(rec.getItemID());
        }
  
        int intersectionSize = 0;
        double score = 0.0;
        double maxScore = 0.0;
        Mean precisionAtI = new Mean();
        double averagePrecision = 0.0;
  
        for (int i = 0; i < numRecs; i++) {
          RecommendedItem rec = recs.get(i);
          double value = LN2 / Math.log(2.0 + i); // 1 / log_2(1 + (i+1))
          if (valueIDs.contains(rec.getItemID())) {
            intersectionSize++;
            score += value;
            precisionAtI.increment(1.0);
            averagePrecision += precisionAtI.getResult();            
          } else {
            precisionAtI.increment(0.0);
          }
          maxScore += value;
        }
        averagePrecision /= numValues;
  
        synchronized (precision) {
          precision.increment(numRecs == 0 ? 0.0 : (double) intersectionSize / numRecs);
          recall.increment((double) intersectionSize / numValues);
          ndcg.increment(maxScore == 0.0 ? 0.0 : score / maxScore);
          meanAveragePrecision.increment(averagePrecision);
          if (count % 10000 == 0) {
            log.info(new IRStatisticsImpl(precision.getResult(),
                                          recall.getResult(),
                                          ndcg.getResult(),
                                          meanAveragePrecision.getResult()).toString());
          }
        }
      }
    };
    
    Paralleler<Long> paralleler = new Paralleler<Long>(testData.keySet().iterator(), processor, "PREval");
    try {
      if (Boolean.parseBoolean(System.getProperty("eval.parallel", "true"))) {
        paralleler.runInParallel();
      } else {
        paralleler.runInSerial();
      }
    } catch (InterruptedException ie) {
      throw new TasteException(ie);
    } catch (ExecutionException e) {
      throw new TasteException(e.getCause());
    }

    EvaluationResult result = new IRStatisticsImpl(precision.getResult(), 
                                                   recall.getResult(), 
                                                   ndcg.getResult(),
                                                   meanAveragePrecision.getResult());
    log.info(result.toString());
    return result;
  }
  
  public static void main(String[] args) throws Exception {
    PrecisionRecallEvaluator eval = new PrecisionRecallEvaluator();
    EvaluationResult result = eval.evaluate(new File(args[0]));
    log.info(result.toString());
  }

}
