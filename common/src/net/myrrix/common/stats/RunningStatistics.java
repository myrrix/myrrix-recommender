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

import java.io.Serializable;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Min;

/**
 * Encapsulates statistics like mean, min and max.
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class RunningStatistics implements Serializable {

  private final Mean mean;
  private final Min min;
  private final Max max;

  public RunningStatistics() {
    this.mean = new Mean();
    this.min = new Min();
    this.max = new Max();
  }

  public int getCount() {
    return (int) mean.getN();
  }

  public double getAverage() {
    return mean.getResult();
  }

  public double getMin() {
    return min.getResult();
  }

  public double getMax() {
    return max.getResult();
  }

  public void addDatum(double v) {
    mean.increment(v);
    min.increment(v);
    max.increment(v);
  }

}
