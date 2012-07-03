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

import org.junit.Test;

public final class RunningStatisticsTest extends MyrrixTest {

  @Test
  public void testInstantiate() {
    RunningStatistics stats = new RunningStatistics();
    assertTrue(Double.isNaN(stats.getMin()));
    assertTrue(Double.isNaN(stats.getMax()));
    stats.addDatum(Integer.MIN_VALUE);
    assertEquals(Integer.MIN_VALUE, stats.getMin(), EPSILON);
    assertEquals(Integer.MIN_VALUE, stats.getMax(), EPSILON);
    stats.addDatum(Integer.MAX_VALUE);
    assertEquals(Integer.MIN_VALUE, stats.getMin(), EPSILON);
    assertEquals(Integer.MAX_VALUE, stats.getMax(), EPSILON);
  }

}
