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

import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;

import net.myrrix.common.MyrrixRecommender;

final class SimpleModRescorerProvider extends AbstractRescorerProvider {
  
  private final int modulus;
  
  SimpleModRescorerProvider(int modulus) {
    this.modulus = modulus;
  }

  @Override
  public IDRescorer getRecommendRescorer(long[] userIDs, MyrrixRecommender recommender, String... args) {
    return userIDs[0] % modulus == 0 ? new SimpleModRescorer(modulus) : null;
  }

  @Override
  public IDRescorer getRecommendToAnonymousRescorer(long[] itemIDs, MyrrixRecommender recommender, String... args) {
    return itemIDs[0] % modulus == 0 ? new SimpleModRescorer(modulus) : null;
  }

  @Override
  public IDRescorer getMostPopularItemsRescorer(MyrrixRecommender recommender, String... args) {
    return new SimpleModRescorer(modulus);
  }

  @Override
  public Rescorer<LongPair> getMostSimilarItemsRescorer(MyrrixRecommender recommender, String... args) {
    return new SimpleModRescorer(modulus);
  }

}
