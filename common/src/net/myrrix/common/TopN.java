/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.myrrix.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import org.apache.mahout.cf.taste.recommender.RecommendedItem;

/**
 * Utility methods for finding the top N things from a stream.
 *
 * @author Sean Owen, Mahout
 */
public final class TopN {

  private TopN() {
  }

  /**
   * @param values stream of values from which to choose
   * @param n how many top values to choose
   * @return the top N values (at most), ordered by value descending.
   */
  public static List<RecommendedItem> selectTopN(Iterable<RecommendedItem> values, int n) {

    // Holding n+2 entries, to compute n+1 top items first
    Queue<MutableRecommendedItem> topN =
        new PriorityQueue<MutableRecommendedItem>(n + 2, ByValueAscComparator.INSTANCE);
    for (RecommendedItem value : values) {
      if (value != null) {
        long itemID = value.getItemID();
        if (topN.size() <= n) {
          topN.add(new MutableRecommendedItem(itemID, value.getValue()));
        } else {
          float valueScore = value.getValue();
          if (valueScore > topN.peek().getValue()) {
            MutableRecommendedItem recycled = topN.poll();
            recycled.set(itemID, valueScore);
            topN.add(recycled);
          }
        }
      }
    }

    if (topN.isEmpty()) {
      return Collections.emptyList();
    }

    if (topN.size() > n) {
      RecommendedItem minimum = topN.poll(); // pick off n+1th largest element
      // ... and anything equal
      while (!topN.isEmpty() && ByValueAscComparator.INSTANCE.compare(topN.peek(), minimum) <= 0) {
        topN.poll();
      }
    }

    if (topN.isEmpty()) {
      return Collections.emptyList();
    }

    List<RecommendedItem> result = new ArrayList<RecommendedItem>(topN);
    Collections.sort(result, Collections.reverseOrder(ByValueAscComparator.INSTANCE));
    return result;
  }

}
