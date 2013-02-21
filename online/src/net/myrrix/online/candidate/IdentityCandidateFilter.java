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
import java.util.Collections;
import java.util.Iterator;

import net.myrrix.common.collection.FastByIDMap;

/**
 * Does no filtering.
 * 
 * @author Sean Owen
 */
final class IdentityCandidateFilter implements CandidateFilter {
  
  private final FastByIDMap<float[]> Y;

  /**
   * @param Y item vectors to hash
   */
  IdentityCandidateFilter(FastByIDMap<float[]> Y) {
    this.Y = Y;
  }

  @Override
  public Collection<Iterator<FastByIDMap.MapEntry<float[]>>> getCandidateIterator(float[][] userVectors) {
    return Collections.singleton(Y.entrySet().iterator());
  }

  @Override
  public void addItem(long itemID) {
    // do nothing
  }

}
