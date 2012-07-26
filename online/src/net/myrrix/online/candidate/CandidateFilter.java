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

import java.util.Iterator;

import net.myrrix.common.collection.FastByIDMap;

/**
 * Implementations of this interface speed up the recommendation process by pre-selecting a set of items
 * that are most likely to be top recommendations.
 *
 * @author Sean Owen
 */
public interface CandidateFilter {

  /**
   * Produce a set of item IDs most likely to be a good recommendation for the given user (vector).
   *
   * @return {@link Iterator} over item IDs and item vectors
   */
  Iterator<FastByIDMap.MapEntry<float[]>> getCandidateIterator(float[][] userVectors);

}
