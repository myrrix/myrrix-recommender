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

package net.myrrix.client;

import com.google.common.base.Preconditions;
import org.apache.mahout.cf.taste.eval.IRStatistics;

final class IRStatisticsImpl implements IRStatistics {

  private final double precision;
  private final double recall;
  private final double nDCG;

  IRStatisticsImpl(double precision, double recall, double nDCG) {
    Preconditions.checkArgument(precision >= 0.0 && precision <= 1.0, "Illegal precision: " + precision);
    Preconditions.checkArgument(recall >= 0.0 && recall <= 1.0, "Illegal recall: " + recall);
    Preconditions.checkArgument(nDCG >= 0.0 && nDCG <= 1.0, "Illegal nDCG: " + nDCG);
    this.precision = precision;
    this.recall = recall;
    this.nDCG = nDCG;
  }

  @Override
  public double getPrecision() {
    return precision;
  }

  @Override
  public double getRecall() {
    return recall;
  }

  /**
   * @throws UnsupportedOperationException
   */
  @Override
  public double getFallOut() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double getF1Measure() {
    return getFNMeasure(1.0);
  }

  @Override
  public double getFNMeasure(double b) {
    double b2 = b * b;
    double sum = b2 * precision + recall;
    return sum == 0.0 ? Double.NaN : (1.0 + b2) * precision * recall / sum;
  }

  @Override
  public double getNormalizedDiscountedCumulativeGain() {
    return nDCG;
  }

  /**
   * @throws UnsupportedOperationException
   */
  @Override
  public double getReach() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return "Precision: " + precision + "; Recall: " + recall + "; nDCG: " + nDCG;
  }

}
