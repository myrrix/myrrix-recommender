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

package net.myrrix.online;

import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;

final class SimpleModRescorer implements IDRescorer, Rescorer<LongPair> {
  
  private final int modulus;
  
  SimpleModRescorer(int modulus) {
    this.modulus = modulus;
  }

  @Override
  public double rescore(long itemID, double value) {
    return isFiltered(itemID) ? Double.NaN : value;
  }

  @Override
  public boolean isFiltered(long itemID) {
    return itemID % modulus != 0;
  }

  @Override
  public double rescore(LongPair itemIDs, double value) {
    return isFiltered(itemIDs) ? Double.NaN : value;
  }

  @Override
  public boolean isFiltered(LongPair itemIDs) {
    return itemIDs.getFirst() % modulus != 0 || itemIDs.getSecond() % modulus != 0;
  }

}
