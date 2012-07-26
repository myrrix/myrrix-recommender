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

import org.apache.mahout.cf.taste.impl.recommender.NullRescorer;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;

/**
 * Dummy implementation which just returns no-op {@link NullRescorer}.
 */
public final class NullRescorerProvider implements RescorerProvider {

  /**
   * @return {@link NullRescorer#getUserInstance()}
   */
  @Override
  public IDRescorer getRecommendRescorer(long[] userIDs, String... args) {
    return NullRescorer.getUserInstance();
  }

  /**
   * @return {@link NullRescorer#getItemItemPairInstance()}
   */
  @Override
  public Rescorer<LongPair> getMostSimilarItemsRescorer(String... args) {
    return NullRescorer.getItemItemPairInstance();
  }

}
