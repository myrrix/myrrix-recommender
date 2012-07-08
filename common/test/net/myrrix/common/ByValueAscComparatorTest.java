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

import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.junit.Test;

public final class ByValueAscComparatorTest extends MyrrixTest {

  @Test
  public void testCompare() {
    RecommendedItem a = new SimpleRecommendedItem(1L, 2.0f);
    RecommendedItem b = new SimpleRecommendedItem(5L, 1.0f);
    assertTrue(ByValueAscComparator.INSTANCE.compare(a, b) > 0);
    assertTrue(ByValueAscComparator.INSTANCE.compare(b, a) < 0);
    assertEquals(0, ByValueAscComparator.INSTANCE.compare(a, a));
  }

}
