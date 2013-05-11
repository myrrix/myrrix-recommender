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

import java.io.Serializable;

import org.apache.mahout.cf.taste.recommender.RecommendedItem;

/**
 * An implementation of {@link RecommendedItem} which is mutable. This lets implementations
 * reuse one object during its iterations rather than create new objects for each element.
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class MutableRecommendedItem implements RecommendedItem, Serializable {

  private long itemID;
  private float value;

  @Override
  public long getItemID() {
    return itemID;
  }

  @Override
  public float getValue() {
    return value;
  }

  public MutableRecommendedItem() {
    this.itemID = Long.MIN_VALUE;
    this.value = Float.NaN;
  }

  public MutableRecommendedItem(long itemID, float value) {
    this.itemID = itemID;
    this.value = value;
  }
  
  public void set(long itemID, float value) {
    this.itemID = itemID;
    this.value = value;
  }

  @Override
  public String toString() {
    return itemID + ":" + value;
  }

}
