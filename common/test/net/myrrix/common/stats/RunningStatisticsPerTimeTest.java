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

package net.myrrix.common.stats;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import net.myrrix.common.MyrrixTest;

public final class RunningStatisticsPerTimeTest extends MyrrixTest {

  @Test
  public void testInit() {
    RunningStatisticsPerTime perTime = new RunningStatisticsPerTime(TimeUnit.MINUTES);
    assertTrue(Double.isNaN(perTime.getMin()));
    assertTrue(Double.isNaN(perTime.getMax()));
    assertTrue(Double.isNaN(perTime.getMean()));
    assertEquals(0L, perTime.getCount());
  }

  @Test
  public void testOneBucket() {
    RunningStatisticsPerTime perTime = new RunningStatisticsPerTime(TimeUnit.MINUTES);
    perTime.increment(1.2);
    assertEquals(1.2, perTime.getMin());
    assertEquals(1.2, perTime.getMax());
    assertEquals(1.2, perTime.getMean());
    assertEquals(1L, perTime.getCount());
  }

  @Test
  public void testRoll() throws Exception {
    RunningStatisticsPerTime perTime = new RunningStatisticsPerTime(TimeUnit.MINUTES);
    perTime.increment(1.2);

    assertEquals(1.2, perTime.getMin());
    assertEquals(1.2, perTime.getMax());
    assertEquals(1.2, perTime.getMean());
    assertEquals(1L, perTime.getCount());

    Thread.sleep(2000L);
    perTime.increment(2.0);

    assertEquals(1.2, perTime.getMin());
    assertEquals(2.0, perTime.getMax());
    assertEquals(1.6, perTime.getMean());
    assertEquals(2L, perTime.getCount());

    Thread.sleep(58000L);
    perTime.refresh();

    assertEquals(2.0, perTime.getMin());
    assertEquals(2.0, perTime.getMax());
    assertEquals(2.0, perTime.getMean());
    assertEquals(1L, perTime.getCount());

    Thread.sleep(2000L);
    perTime.refresh();

    assertTrue(Double.isNaN(perTime.getMin()));
    assertTrue(Double.isNaN(perTime.getMax()));
    assertTrue(Double.isNaN(perTime.getMean()));
    assertEquals(0L, perTime.getCount());
  }


}
