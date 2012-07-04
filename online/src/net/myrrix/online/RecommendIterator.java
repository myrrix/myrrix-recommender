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

import com.google.common.base.Preconditions;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.common.MutableRecommendedItem;
import net.myrrix.common.SimpleVectorMath;
import net.myrrix.common.collection.FastByIDMap;

/**
 * An {@link Iterator} that generates and iterates over all possible candidate items to recommend.
 * It is used to generate recommendations. The items with top values are taken as recommendations.
 *
 * @author Sean Owen
 * @see MostSimilarItemIterator
 * @see RecommendedBecauseIterator
 */
final class RecommendIterator implements Iterator<RecommendedItem> {

  private final MutableRecommendedItem delegate;
  private final float[][] features;
  private final Iterator<FastByIDMap<float[]>.MapEntry> Yiterator;
  private final FastIDSet knownItemIDs;
  private final IDRescorer rescorer;

  RecommendIterator(float[][] features,
                    Iterator<FastByIDMap<float[]>.MapEntry> Yiterator,
                    FastIDSet knownItemIDs,
                    IDRescorer rescorer) {
    Preconditions.checkArgument(features.length > 0);
    delegate = new MutableRecommendedItem();
    this.features = features;
    this.Yiterator = Yiterator;
    this.knownItemIDs = knownItemIDs;
    this.rescorer = rescorer;
  }

  @Override
  public boolean hasNext() {
    return Yiterator.hasNext();
  }

  @Override
  public RecommendedItem next() {
    FastByIDMap<float[]>.MapEntry entry = Yiterator.next();
    long itemID = entry.getKey();
    FastIDSet theKnownItemIDs = knownItemIDs;
    if (theKnownItemIDs != null) {
      synchronized (theKnownItemIDs) {
        if (theKnownItemIDs.contains(itemID)) {
          return null;
        }
      }
    }
    IDRescorer rescorer1 = this.rescorer;
    if (rescorer1 != null && rescorer1.isFiltered(itemID)) {
      return null;
    }

    double sum = 0.0;
    int count = 0;
    for (float[] oneUserFeatures : features) {
      double dot = SimpleVectorMath.dot(entry.getValue(), oneUserFeatures);
      if (rescorer1 != null) {
        dot = rescorer1.rescore(itemID, dot);
      }
      sum += dot;
      count++;
    }

    delegate.set(itemID, (float) (sum / count));
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
