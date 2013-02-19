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

import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.common.LangUtils;
import net.myrrix.common.MutableRecommendedItem;
import net.myrrix.common.collection.FastByIDFloatMap;

/**
 * Used by {@link net.myrrix.common.MyrrixRecommender#mostPopularItems(int)}.
 *
 * @author Sean Owen
 */
final class MostPopularItemsIterator implements Iterator<RecommendedItem> {

  private final MutableRecommendedItem delegate;
  private final Iterator<FastByIDFloatMap.MapEntry> countsIterator;
  private final IDRescorer rescorer;

  MostPopularItemsIterator(Iterator<FastByIDFloatMap.MapEntry> countsIterator, IDRescorer rescorer) {
    delegate = new MutableRecommendedItem();
    this.countsIterator = countsIterator;
    this.rescorer = rescorer;
  }

  @Override
  public boolean hasNext() {
    return countsIterator.hasNext();
  }

  @Override
  public RecommendedItem next() {
    FastByIDFloatMap.MapEntry entry = countsIterator.next();
    long id = entry.getKey();
    float value = entry.getValue();
    IDRescorer theRescorer = rescorer;
    if (theRescorer != null) {
      if (theRescorer.isFiltered(id)) {
        return null;
      }
      value = (float) theRescorer.rescore(id, value);
      if (!LangUtils.isFinite(value)) {
        return null;
      }
    }
    delegate.set(id, value);
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
