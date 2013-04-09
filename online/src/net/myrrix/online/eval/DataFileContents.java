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

package net.myrrix.online.eval;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

/**
 * Represents the contents of input files: user-item data, item tags, and user tags.
 * 
 * @author Sean Owen
 */
final class DataFileContents {
  
  private final Multimap<Long,RecommendedItem> data;
  private final Multimap<String,RecommendedItem> itemTags;
  private final Multimap<String,RecommendedItem> userTags;

  DataFileContents(Multimap<Long,RecommendedItem> data, 
                   Multimap<String,RecommendedItem> itemTags, 
                   Multimap<String,RecommendedItem> userTags) {
    Preconditions.checkNotNull(data);
    Preconditions.checkNotNull(itemTags);
    Preconditions.checkNotNull(userTags);
    this.data = data;
    this.itemTags = itemTags;
    this.userTags = userTags;
  }

  Multimap<Long, RecommendedItem> getData() {
    return data;
  }

  Multimap<String, RecommendedItem> getItemTags() {
    return itemTags;
  }

  Multimap<String, RecommendedItem> getUserTags() {
    return userTags;
  }

}
