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

import net.myrrix.common.MutableRecommendedItem;
import net.myrrix.common.SimpleVectorMath;
import net.myrrix.common.collection.FastByIDMap;

/**
 * An {@link Iterator} that generates and iterates over all possible candidate items in computation
 * of {@link org.apache.mahout.cf.taste.recommender.ItemBasedRecommender#recommendedBecause(long, long, int)}.
 *
 * @author Sean Owen
 * @see MostSimilarItemIterator
 * @see RecommendIterator
 */
final class RecommendedBecauseIterator implements Iterable<RecommendedItem> {

  private final MutableRecommendedItem delegate;
  private final float[] features;
  private final FastByIDMap<float[]> toFeatures;

  RecommendedBecauseIterator(FastByIDMap<float[]> toFeatures, float[] features) {
    delegate = new MutableRecommendedItem();
    this.features = features;
    this.toFeatures = toFeatures;
  }

  @Override
  public Iterator<RecommendedItem> iterator() {
    return Iterators.transform(toFeatures.entrySet().iterator(), new DotFunction());
  }
  
  private final class DotFunction implements Function<FastByIDMap<float[]>.MapEntry,RecommendedItem> {
    @Override
    public MutableRecommendedItem apply(FastByIDMap<float[]>.MapEntry entry) {
      long itemID = entry.getKey();
      float[] candidateFeatures = entry.getValue();
      double estimate = SimpleVectorMath.correlation(candidateFeatures, features);
      if (Double.isNaN(estimate)) {
        return null;
      }
      delegate.set(itemID, (float) estimate);
      return delegate;
    }
  }


}
