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

package net.myrrix.common.stats;

import java.io.Serializable;

import com.google.common.base.Preconditions;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;

/**
 * <p>Ported from Mahout {@code WeightedRunningAverage} but uses {@code int} weights.</p>
 *
 * @author Mahout
 */
public class WeightedRunningAverage implements RunningAverage, Serializable {
  
  private int totalWeight;
  private double average;
  
  public WeightedRunningAverage() {
    totalWeight = 0;
    average = Double.NaN;
  }
  
  @Override
  public synchronized void addDatum(double datum) {
    addDatum(datum, 1);
  }
  
  public synchronized void addDatum(double datum, int weight) {
    int oldTotalWeight = totalWeight;
    totalWeight += weight;
    if (oldTotalWeight <= 0) {
    	average = datum;
    } else {
      average = average * oldTotalWeight / totalWeight + datum * weight / totalWeight;
    }
  }
  
  @Override
  public synchronized void removeDatum(double datum) {
    removeDatum(datum, 1);
  }
  
  public synchronized void removeDatum(double datum, int weight) {
    int oldTotalWeight = totalWeight;
    totalWeight -= weight;
    if (totalWeight <= 0) {
      average = Double.NaN;
      totalWeight = 0;
    } else {
      average = average * oldTotalWeight / totalWeight - datum * weight / totalWeight;
    }
  }
  
  @Override
  public synchronized void changeDatum(double delta) {
    changeDatum(delta, 1);
  }
  
  public synchronized void changeDatum(double delta, int weight) {
    Preconditions.checkArgument(weight <= totalWeight);
    average += delta * weight / totalWeight;
  }
  
  public synchronized double getTotalWeight() {
    return totalWeight;
  }
  
  /** @return {@link #getTotalWeight()} */
  @Override
  public synchronized int getCount() {
    return totalWeight;
  }
  
  @Override
  public synchronized double getAverage() {
    return average;
  }

  /**
   * @throws UnsupportedOperationException
   */
  @Override
  public RunningAverage inverse() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public synchronized String toString() {
    return String.valueOf(average);
  }
  
}
