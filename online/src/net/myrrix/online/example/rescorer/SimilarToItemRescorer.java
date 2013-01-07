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

package net.myrrix.online.example.rescorer;

import com.google.common.base.Preconditions;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.recommender.IDRescorer;

import net.myrrix.common.MyrrixRecommender;

/**
 * Rescores candidate recommendations based on similarity to a given item. This can be useful to demote items
 * that are less similar to an item currently "in focus". For example, a product recommender might rescore
 * recommendations this way when the user is browsing a product page. Note that this will <em>exclude</em>
 * the given item from recommendations too.
 *
 * @author Sean Owen
 * @see SimilarToItemRescorerProvider
 */
final class SimilarToItemRescorer implements IDRescorer {

  private final long toItemID;
  private final MyrrixRecommender recommender;

  SimilarToItemRescorer(long toItemID, MyrrixRecommender recommender) {
    Preconditions.checkNotNull(recommender);
    this.toItemID = toItemID;
    this.recommender = recommender;
  }

  @Override
  public double rescore(long itemID, double value) {
    if (toItemID == itemID) {
      return Double.NaN;
    }
    try {
      return value * recommender.similarityToItem(toItemID, itemID)[0];
    } catch (TasteException e) {
      return Double.NaN;
    }
  }

  @Override
  public boolean isFiltered(long itemID) {
    return false;
  }

}
