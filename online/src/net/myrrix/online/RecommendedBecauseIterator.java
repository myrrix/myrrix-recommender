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

import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.common.LangUtils;
import net.myrrix.common.MutableRecommendedItem;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.math.SimpleVectorMath;

/**
 * An {@link Iterator} that generates and iterates over all possible candidate items in computation
 * of {@link org.apache.mahout.cf.taste.recommender.ItemBasedRecommender#recommendedBecause(long, long, int)}.
 *
 * @author Sean Owen
 * @see MostSimilarItemIterator
 * @see RecommendIterator
 */
final class RecommendedBecauseIterator implements Iterator<RecommendedItem> {

  private final MutableRecommendedItem delegate;
  private final float[] features;
  private final double featuresNorm;
  private final Iterator<FastByIDMap.MapEntry<float[]>> toFeaturesIterator;

  RecommendedBecauseIterator(Iterator<FastByIDMap.MapEntry<float[]>> toFeaturesIterator, float[] features) {
    delegate = new MutableRecommendedItem();
    this.features = features;
    this.featuresNorm = SimpleVectorMath.norm(features);
    this.toFeaturesIterator = toFeaturesIterator;
  }

  @Override
  public boolean hasNext() {
    return toFeaturesIterator.hasNext();
  }

  @Override
  public RecommendedItem next() {
    FastByIDMap.MapEntry<float[]> entry = toFeaturesIterator.next();
    long itemID = entry.getKey();
    float[] candidateFeatures = entry.getValue();
    double candidateFeaturesNorm = SimpleVectorMath.norm(candidateFeatures);
    double estimate = SimpleVectorMath.dot(candidateFeatures, features) / (candidateFeaturesNorm * featuresNorm);
    if (!LangUtils.isFinite(estimate)) {
      return null;
    }
    delegate.set(itemID, (float) estimate);
    return delegate;
  }

  /**
   * @throws UnsupportedOperationException
   */
  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

}
