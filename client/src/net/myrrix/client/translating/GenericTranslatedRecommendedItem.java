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

package net.myrrix.client.translating;

import java.io.Serializable;

import com.google.common.base.Preconditions;
import org.apache.mahout.common.RandomUtils;

/**
 * <p>A simple implementation of {@link TranslatedRecommendedItem}.</p>
 *
 * @author Sean Owen
 */
public final class GenericTranslatedRecommendedItem implements TranslatedRecommendedItem, Serializable {

  private final String itemID;
  private final float value;

  /**
   * @throws IllegalArgumentException if item is null or value is NaN or infinite
   */
  public GenericTranslatedRecommendedItem(String itemID, float value) {
    Preconditions.checkNotNull(itemID);
    Preconditions.checkArgument(!Float.isNaN(value) && !Float.isInfinite(value));
    this.itemID = itemID;
    this.value = value;
  }
  
  @Override
  public String getItemID() {
    return itemID;
  }
  
  @Override
  public float getValue() {
    return value;
  }

  @Override
  public String toString() {
    return "GenericTranslatedRecommendedItem[item:" + itemID + ", value:" + value + ']';
  }

  @Override
  public int hashCode() {
    return itemID.hashCode() ^ RandomUtils.hashFloat(value);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof GenericTranslatedRecommendedItem)) {
      return false;
    }
    GenericTranslatedRecommendedItem other = (GenericTranslatedRecommendedItem) o;
    return itemID.equals(other.getItemID()) && value == other.getValue();
  }
  
}
