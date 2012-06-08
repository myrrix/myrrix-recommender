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

package net.myrrix.common.collection;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Simply compares {@link Comparable} objects by their natural ordering
 * (defined by {@link Comparable#compareTo(Object)}.
 *
 * @author Sean Owen
 */
public final class NaturalOrderComparator<T extends Comparable<? super T>> implements Comparator<T>, Serializable {

  @Override
  public int compare(T a, T b) {
    return a.compareTo(b);
  }

}
