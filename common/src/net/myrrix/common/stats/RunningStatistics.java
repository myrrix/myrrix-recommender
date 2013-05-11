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

import org.apache.mahout.cf.taste.impl.common.FullRunningAverageAndStdDev;
import org.apache.mahout.cf.taste.impl.common.RunningAverageAndStdDev;

/**
 * Like implementations of {@link RunningAverageAndStdDev} but adds more statistics, like min and max.
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class RunningStatistics implements RunningAverageAndStdDev, RunningAverageAndMinMax, Serializable {

  private final RunningAverageAndStdDev delegate;
  private double min;
  private double max;

  public RunningStatistics() {
    this(new FullRunningAverageAndStdDev(), Double.NaN, Double.NaN);
  }

  private RunningStatistics(RunningAverageAndStdDev delegate, double min, double max) {
    this.delegate = delegate;
    this.min = min;
    this.max = max;
  }

  @Override
  public int getCount() {
    return delegate.getCount();
  }

  @Override
  public double getAverage() {
    return delegate.getAverage();
  }

  @Override
  public double getStandardDeviation() {
    return delegate.getStandardDeviation();
  }

  @Override
  public double getMin() {
    return min;
  }

  @Override
  public double getMax() {
    return max;
  }

  @Override
  public void addDatum(double v) {
    delegate.addDatum(v);
    if (Double.isNaN(max) || v > max) {
      max = v;
    }
    if (Double.isNaN(min) || v < min) {
      min = v;
    }
  }

  /**
   * @throws UnsupportedOperationException
   */
  @Override
  public void removeDatum(double v) {
    throw new UnsupportedOperationException();
  }

  /**
   * @throws UnsupportedOperationException
   */
  @Override
  public void changeDatum(double v) {
    throw new UnsupportedOperationException();
  }

  /**
   * @throws UnsupportedOperationException
   */
  @Override
  public RunningAverageAndStdDev inverse() {
    throw new UnsupportedOperationException();
  }

}
