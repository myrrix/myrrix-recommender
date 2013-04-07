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

import com.google.common.primitives.Floats;
import com.google.common.primitives.Longs;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

/**
 * Simple and complete implementation of {@link RecommendedItem} which can be compared with itself.
 *
 * @author Sean Owen
 */
public final class SimpleRecommendedItem implements RecommendedItem, Comparable<SimpleRecommendedItem> {

  private final long itemID;
  private final float value;

  public SimpleRecommendedItem(long itemID, float value) {
    this.itemID = itemID;
    this.value = value;
  }

  @Override
  public long getItemID() {
    return itemID;
  }

  @Override
  public float getValue() {
    return value;
  }

  @Override
  public int hashCode() {
    return Longs.hashCode(itemID) ^ Floats.hashCode(value);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SimpleRecommendedItem)) {
      return false;
    }
    SimpleRecommendedItem other = (SimpleRecommendedItem) o;
    return other.itemID == itemID && other.value == value;
  }

  @Override
  public String toString() {
    return itemID + ":" + value;
  }

  @Override
  public int compareTo(SimpleRecommendedItem other) {
    return Float.compare(other.value, value);
  }

}
