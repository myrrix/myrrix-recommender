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

package net.myrrix.online;

import java.util.Iterator;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;

import net.myrrix.common.MutableRecommendedItem;
import net.myrrix.common.SimpleVectorMath;
import net.myrrix.common.collection.FastByIDMap;

/**
 * An {@link Iterator} that generates and iterates over all possible candidate items in computation
 * of {@link org.apache.mahout.cf.taste.recommender.ItemBasedRecommender#mostSimilarItems(long, int)}.
 *
 * @author Sean Owen
 * @see RecommendedBecauseIterator
 * @see RecommendIterator
 */
final class MostSimilarItemIterator implements Iterable<RecommendedItem> {

  private final MutableRecommendedItem delegate;
  private final float[][] itemFeatures;
  private final FastByIDMap<float[]> Y;
  private final long[] toItemIDs;
  private final Rescorer<LongPair> rescorer;

  MostSimilarItemIterator(FastByIDMap<float[]> Y,
                          long[] toItemIDs,
                          float[][] itemFeatures,
                          Rescorer<LongPair> rescorer) {
    delegate = new MutableRecommendedItem();
    this.toItemIDs = toItemIDs;
    this.itemFeatures = itemFeatures;
    this.Y = Y;
    this.rescorer = rescorer;
  }

  @Override
  public Iterator<RecommendedItem> iterator() {
    return Iterators.transform(Y.entrySet().iterator(), new DotFunction());
  }

  private final class DotFunction implements Function<FastByIDMap<float[]>.MapEntry,RecommendedItem> {
    @Override
    public MutableRecommendedItem apply(FastByIDMap<float[]>.MapEntry entry) {
      long itemID = entry.getKey();
      for (long l : toItemIDs) {
        if (l == itemID) {
          return null;
        }
      }

      Rescorer<LongPair> rescorer = MostSimilarItemIterator.this.rescorer;
      float[] candidateFeatures = entry.getValue();
      double total = 0.0;

      int length = itemFeatures.length;
      for (int i = 0; i < length; i++) {
        long toItemID = toItemIDs[i];
        if (rescorer != null && rescorer.isFiltered(new LongPair(itemID, toItemID))) {
          return null;
        }
        float[] features = itemFeatures[i];
        double correlation = SimpleVectorMath.correlation(candidateFeatures, features);
        if (Double.isNaN(correlation)) {
          return null;
        }
        if (rescorer != null) {
          correlation = rescorer.rescore(new LongPair(itemID, toItemID), correlation);
          if (Double.isNaN(correlation)) {
            return null;
          }
        }
        total += correlation;
      }

      double estimate = total / length;
      delegate.set(itemID, (float) estimate);
      return delegate;
    }
  }

}
