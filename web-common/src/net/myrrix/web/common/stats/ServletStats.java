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

package net.myrrix.web.common.stats;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.myrrix.common.stats.RunningStatistics;
import net.myrrix.common.stats.RunningStatisticsPerTime;

public final class ServletStats implements Serializable {

  private final RunningStatistics allTime;
  private final RunningStatisticsPerTime lastHour;
  private final AtomicInteger numClientErrors;
  private final AtomicInteger numServerErrors;

  public ServletStats() {
    allTime = new RunningStatistics();
    lastHour = new RunningStatisticsPerTime(TimeUnit.HOURS);
    numClientErrors = new AtomicInteger();
    numServerErrors = new AtomicInteger();
  }

  public void addTiming(long value) {
    allTime.addDatum(value);
    lastHour.addDatum(value);
  }

  public RunningStatistics getAllTime() {
    return allTime;
  }

  public RunningStatisticsPerTime getLastHour() {
    return lastHour;
  }

  public int getNumClientErrors() {
    return numClientErrors.get();
  }

  public void incrementClientErrors() {
    numClientErrors.incrementAndGet();
  }

  public int getNumServerErrors() {
    return numServerErrors.get();
  }

  public void incrementServerErrors() {
    numServerErrors.incrementAndGet();
  }

}
