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

package net.myrrix.online.candidate;

import java.util.Collection;
import java.util.Iterator;

import net.myrrix.common.collection.FastByIDMap;

/**
 * <p>Implementations of this interface speed up the recommendation process by pre-selecting a set of items
 * that are the only possible candidates for recommendation, or, are the most likely to be top recommendations.</p>
 * 
 * <p>For example, an implementation might know that most items in the model, while useful as data, 
 * are not recommendable since they are old -- out of date, or at least, undesirable enough as recommendations 
 * to be excluded. It could pre-compute which items are eligible and </p>
 * 
 * <p>This is a form of filtering, but, differs from the filtering provided by 
 * {@link net.myrrix.online.RescorerProvider}. That is a run-time, per-request filter; this class represents
 * a more global, precomputed filtering that is not parameterized by the request.</p>
 *
 * <p>Implementations should define a constructor that accepts a parameter of type {@link FastByIDMap}.
 * This is a reference to the "Y" matrix in the model -- item-feature matrix.
 * Access to Y is protected by a lock, but, the implementation can assume that it is locked for
 * reading (not writing) during the constructor call, and is locked for reading (not writing) during
 * a call to {@link #getCandidateIterator(float[][])} and while the result of that method is used.
 * So, implementations may save and use a reference to Y if it is only used in the context of these
 * methods and only for reading.</p>
 * 
 * @author Sean Owen
 * @since 1.0
 * @see net.myrrix.online.RescorerProvider
 */
public interface CandidateFilter {
  
  // Note that your implementation will need a constructor matching the following, which is how it
  // gets a reference to the set of items:
  
  // public YourCandidateFilter(FastByIDMap<float[]> Y) {
  //   ...
  // }

  /**
   * @param userVectors user feature vector(s) for which recommendations are being made. This may or may not
   *  influence which items are returned.
   * @return a set of items most likely to be a good recommendation for the given users. These are returned
   *  as item ID / vector pairs ({@link FastByIDMap}'s {@code MapEntry}). They are returned as an {@link Iterator} --
   *  and not just one, but potentially many. If several are returned, then the caller will process the
   *  {@link Iterator}s in parallel for speed.
   */
  Collection<Iterator<FastByIDMap.MapEntry<float[]>>> getCandidateIterator(float[][] userVectors);

  /**
   * Note a new item has appeared at run-time.
   *
   * @param itemID ID of new item
   */
  void addItem(long itemID);

}
