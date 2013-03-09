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

package net.myrrix.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import com.google.common.collect.Lists;
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
   * @param n how many top values to choose
   * @return Initialized {@link Queue} suitable for use in this class
   */
  public static Queue<MutableRecommendedItem> initialQueue(int n) {
    return new PriorityQueue<MutableRecommendedItem>(n + 2, ByValueAscComparator.INSTANCE);
  }

  /**
   * Computes top N values for a stream and puts them into a {@link Queue}.
   *
   * @param topN {@link Queue} to add to
   * @param values stream of values from which to choose
   * @param n how many top values to choose
   */
  public static void selectTopNIntoQueue(Queue<MutableRecommendedItem> topN,
                                         Iterator<RecommendedItem> values,
                                         int n) {
    while (values.hasNext()) {
      RecommendedItem value = values.next();
      if (value != null) {
        long itemID = value.getItemID();
        float valueScore = value.getValue();
        if (topN.size() > n) {
          if (valueScore > topN.peek().getValue()) {
            MutableRecommendedItem recycled = topN.poll();
            recycled.set(itemID, valueScore);
            topN.add(recycled);
          }
        } else {
          topN.add(new MutableRecommendedItem(itemID, valueScore));
        }
      }
    }
  }


  /**
   * Computes top N values for a stream and puts them into a {@link Queue}.
   * Used in the context of multiple threads.
   *
   * @param topN {@link Queue} to add to
   * @param queueLeastValue in/out parameter caching the queue's least value
   * @param values stream of values from which to choose
   * @param n how many top values to choose
   */
  public static void selectTopNIntoQueueMultithreaded(Queue<MutableRecommendedItem> topN,
                                                      float[] queueLeastValue,
                                                      Iterator<RecommendedItem> values,
                                                      int n) {
    // Cache to avoid most synchronization
    float localQueueLeastValue = queueLeastValue[0];

    while (values.hasNext()) {
      RecommendedItem value = values.next();
      if (value != null) {
        long itemID = value.getItemID();
        float valueScore = value.getValue();
        if (valueScore >= localQueueLeastValue) {

          synchronized (topN) {
            if (topN.size() > n) {
              float currentQueueLeastValue = topN.peek().getValue();
              localQueueLeastValue = currentQueueLeastValue;
              if (valueScore > currentQueueLeastValue) {
                MutableRecommendedItem recycled = topN.poll();
                recycled.set(itemID, valueScore);
                topN.add(recycled);
              }
            } else {
              topN.add(new MutableRecommendedItem(itemID, valueScore));
            }
          }
        }
      }
    }

    queueLeastValue[0] = localQueueLeastValue;
  }

  /**
   * @param topN {@link Queue} of items from which to take top n
   * @param n how many top values to choose
   * @return order list of top results
   */
  public static List<RecommendedItem> selectTopNFromQueue(Queue<MutableRecommendedItem> topN, int n) {
    if (topN.isEmpty()) {
      return Collections.emptyList();
    }
    while (topN.size() > n) {
      topN.poll();
    }
    List<RecommendedItem> result = Lists.<RecommendedItem>newArrayList(topN);
    Collections.sort(result, Collections.reverseOrder(ByValueAscComparator.INSTANCE));
    return result;
  }

  /**
   * @param values stream of values from which to choose
   * @param n how many top values to choose
   * @return the top N values (at most), ordered by value descending.
   */
  public static List<RecommendedItem> selectTopN(Iterator<RecommendedItem> values, int n) {
    Queue<MutableRecommendedItem> topN = initialQueue(n);
    selectTopNIntoQueue(topN, values, n);
    return selectTopNFromQueue(topN, n);
  }

}
