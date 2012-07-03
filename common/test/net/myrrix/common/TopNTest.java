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

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.impl.recommender.GenericRecommendedItem;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.junit.Test;

public final class TopNTest extends MyrrixTest {

  @Test
  public void testEmpty() {
    List<RecommendedItem> empty = Collections.emptyList();
    List<RecommendedItem> top2 = TopN.selectTopN(empty, 2);
    assertNotNull(top2);
    assertEquals(0, top2.size());
  }

  @Test
  public void testTopExactly() {
    List<RecommendedItem> candidates = makeNCandidates(3);
    List<RecommendedItem> top3 = TopN.selectTopN(candidates, 3);
    assertNotNull(top3);
    assertEquals(3, top3.size());
    assertEquals(3L, top3.get(0).getItemID());
    assertEquals(3.0f, top3.get(0).getValue(), EPSILON);
    assertEquals(1L, top3.get(2).getItemID());
    assertEquals(1.0f, top3.get(2).getValue(), EPSILON);
  }

  @Test
  public void testTopPlusOne() {
    List<RecommendedItem> candidates = makeNCandidates(4);
    List<RecommendedItem> top3 = TopN.selectTopN(candidates, 3);
    assertNotNull(top3);
    assertEquals(3, top3.size());
    assertEquals(4L, top3.get(0).getItemID());
    assertEquals(4.0f, top3.get(0).getValue(), EPSILON);
    assertEquals(2L, top3.get(2).getItemID());
    assertEquals(2.0f, top3.get(2).getValue(), EPSILON);
  }

  @Test
  public void testTopOfMany() {
    List<RecommendedItem> candidates = makeNCandidates(20);
    List<RecommendedItem> top3 = TopN.selectTopN(candidates, 3);
    assertNotNull(top3);
    assertEquals(3, top3.size());
    assertEquals(20L, top3.get(0).getItemID());
    assertEquals(20.0f, top3.get(0).getValue(), EPSILON);
    assertEquals(18L, top3.get(2).getItemID());
    assertEquals(18.0f, top3.get(2).getValue(), EPSILON);
  }

  private static List<RecommendedItem> makeNCandidates(int n) {
    List<RecommendedItem> candidates = Lists.newArrayListWithCapacity(n);
    for (int i = 1; i <= n; i++) {
      candidates.add(new GenericRecommendedItem(i, i));
    }
    return candidates;
  }

}
