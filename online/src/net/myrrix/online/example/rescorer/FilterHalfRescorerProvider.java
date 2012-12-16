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

import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;

import net.myrrix.online.RescorerProvider;

/**
 * <p>A contrived example of a {@link RescorerProvider} which will simply filter out all even-numbered IDs.
 * No even-numbered IDs will be recommended, and even-numbered IDs will get no results. All other values
 * will be multiplied by 10.</p>
 *
 * <p>This isn't useful, it's just an example.</p>
 *
 * <p>To switch to filtering odd-numbered IDs, pass the rescorer the argument "odd".</p>
 *
 * @author Sean Owen
 */
public final class FilterHalfRescorerProvider implements RescorerProvider {

  @Override
  public IDRescorer getRecommendRescorer(final long[] userIDs, String... args) {
    final boolean odd = args.length > 0 && "odd".equalsIgnoreCase(args[0]);
    return new IDRescorer() {
      @Override
      public double rescore(long itemID, double score) {
        for (long userID : userIDs) {
          if (odd == ((userID & 0x01) == 1)) {
            return Double.NaN;
          }
        }
        return isFiltered(itemID) ? Double.NaN : 10.0 * score;
      }
      @Override
      public boolean isFiltered(long itemID) {
        return odd == ((itemID & 0x01) == 1);
      }
    };
  }

  @Override
  public IDRescorer getRecommendToAnonymousRescorer(final long[] itemIDs, String... args) {
    final boolean odd = args.length > 0 && "odd".equalsIgnoreCase(args[0]);
    return new IDRescorer() {
      @Override
      public double rescore(long itemID, double score) {
        for (long anItemID : itemIDs) {
          if (odd == ((anItemID & 0x01) == 1)) {
            return Double.NaN;
          }
        }
        return isFiltered(itemID) ? Double.NaN : 10.0 * score;
      }
      @Override
      public boolean isFiltered(long itemID) {
        return odd == ((itemID & 0x01) == 1);
      }
    };
  }

  @Override
  public Rescorer<LongPair> getMostSimilarItemsRescorer(String... args) {
    final boolean odd = args.length > 0 && Boolean.valueOf(args[0]);
    return new Rescorer<LongPair>() {
      @Override
      public double rescore(LongPair longPair, double score) {
        return isFiltered(longPair) ? Double.NaN : 10.0 * score;
      }
      @Override
      public boolean isFiltered(LongPair longPair) {
        return odd == ((longPair.getFirst() & 0x01) == 1) || odd == ((longPair.getSecond() & 0x01) == 1);
      }
    };
  }

}
