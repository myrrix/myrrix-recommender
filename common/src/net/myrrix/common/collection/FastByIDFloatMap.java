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

package net.myrrix.common.collection;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.impl.common.AbstractLongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;

import net.myrrix.common.random.RandomUtils;

/**
 * Based on Mahout's {@code FastByIDMap}; used with {@code float} instead of {@code double}.
 *
 * @author Sean Owen
 * @author Mahout
 * @since 1.0
 */
public final class FastByIDFloatMap implements Serializable, Cloneable {

  private static final float DEFAULT_LOAD_FACTOR = 1.5f;

  /** Dummy object used to represent a key that has been removed. */
  private static final long REMOVED = Long.MAX_VALUE;
  private static final long KEY_NULL = Long.MIN_VALUE;
  private static final float VALUE_NULL = Float.NaN;

  // For faster access:
  long[] keys;
  float[] values;
  private final float loadFactor;
  private int numEntries;
  private int numSlotsUsed;

  /** Creates a new  with default capacity. */
  public FastByIDFloatMap() {
    this(2);
  }

  public FastByIDFloatMap(int size) {
    this(size, DEFAULT_LOAD_FACTOR);
  }

  public FastByIDFloatMap(int size, float loadFactor) {
    Preconditions.checkArgument(size >= 0, "size must be at least 0");
    Preconditions.checkArgument(loadFactor >= 1.0f, "loadFactor must be at least 1.0");
    this.loadFactor = loadFactor;
    int max = (int) (RandomUtils.MAX_INT_SMALLER_TWIN_PRIME / loadFactor);
    Preconditions.checkArgument(size < max, "size must be less than " + max);
    int hashSize = RandomUtils.nextTwinPrime((int) (loadFactor * size));
    keys = new long[hashSize];
    Arrays.fill(keys, KEY_NULL);
    values = new float[hashSize];
    Arrays.fill(values, VALUE_NULL);
  }

  /**
   * @see #findForAdd(long)
   */
  private int find(long key) {
    int theHashCode = (int) key & 0x7FFFFFFF; // make sure it's positive
    long[] keys = this.keys;
    int hashSize = keys.length;
    int jump = 1 + theHashCode % (hashSize - 2);
    int index = theHashCode % hashSize;
    long currentKey = keys[index];
    while (currentKey != KEY_NULL && key != currentKey) {
      index -= index < jump ? jump - hashSize : jump;
      currentKey = keys[index];
    }
    return index;
  }

  /**
   * @see #find(long)
   */
  private int findForAdd(long key) {
    int theHashCode = (int) key & 0x7FFFFFFF; // make sure it's positive
    long[] keys = this.keys;
    int hashSize = keys.length;
    int jump = 1 + theHashCode % (hashSize - 2);
    int index = theHashCode % hashSize;
    long currentKey = keys[index];
    while (currentKey != KEY_NULL && currentKey != REMOVED && key != currentKey) {
      index -= index < jump ? jump - hashSize : jump;
      currentKey = keys[index];
    }
    if (currentKey != REMOVED) {
      return index;
    }
    // If we're adding, it's here, but, the key might have a value already later
    int addIndex = index;
    while (currentKey != KEY_NULL && key != currentKey) {
      index -= index < jump ? jump - hashSize : jump;
      currentKey = keys[index];
    }
    return key == currentKey ? index : addIndex;
  }

  public float get(long key) {
    if (key == KEY_NULL) {
      return VALUE_NULL;
    }
    int index = find(key);
    return values[index];
  }

  // Added:

  public void increment(long key, float delta) {
    Preconditions.checkArgument(key != KEY_NULL && key != REMOVED);
    int index = find(key);
    float currentValue = values[index];
    if (Float.isNaN(currentValue)) {
      put(key, delta);
    } else {
      values[index] = currentValue + delta;
    }
  }

  public int size() {
    return numEntries;
  }

  public boolean isEmpty() {
    return numEntries == 0;
  }

  public boolean containsKey(long key) {
    return key != KEY_NULL && key != REMOVED && keys[find(key)] != KEY_NULL;
  }

  public void put(long key, float value) {
    Preconditions.checkArgument(key != KEY_NULL && key != REMOVED);
    // If less than half the slots are open, let's clear it up
    if (numSlotsUsed * loadFactor >= keys.length) {
      // If over half the slots used are actual entries, let's grow
      if (numEntries * loadFactor >= numSlotsUsed) {
        growAndRehash();
      } else {
        // Otherwise just rehash to clear REMOVED entries and don't grow
        rehash();
      }
    }
    // Here we may later consider implementing Brent's variation described on page 532
    int index = findForAdd(key);
    long keyIndex = keys[index];
    if (keyIndex == key) {
      values[index] = value;
    } else {
      keys[index] = key;
      values[index] = value;
      numEntries++;
      if (keyIndex == KEY_NULL) {
        numSlotsUsed++;
      }
    }
  }

  public void remove(long key) {
    if (key == KEY_NULL || key == REMOVED) {
      return;
    }
    int index = find(key);
    if (keys[index] != KEY_NULL) {
      keys[index] = REMOVED;
      numEntries--;
      values[index] = VALUE_NULL;
    }
  }

  public void clear() {
    numEntries = 0;
    numSlotsUsed = 0;
    Arrays.fill(keys, KEY_NULL);
    Arrays.fill(values, VALUE_NULL);
  }

  public LongPrimitiveIterator keySetIterator() {
    return new KeyIterator();
  }

  public Set<MapEntry> entrySet() {
    return new EntrySet();
  }

  public void rehash() {
    rehash(RandomUtils.nextTwinPrime((int) (loadFactor * numEntries)));
  }

  private void growAndRehash() {
    Preconditions.checkState(keys.length * loadFactor < RandomUtils.MAX_INT_SMALLER_TWIN_PRIME, "Can't grow any more");
    rehash(RandomUtils.nextTwinPrime((int) (loadFactor * keys.length)));
  }

  private void rehash(int newHashSize) {
    long[] oldKeys = keys;
    float[] oldValues = values;
    numEntries = 0;
    numSlotsUsed = 0;
    keys = new long[newHashSize];
    Arrays.fill(keys, KEY_NULL);
    values = new float[newHashSize];
    Arrays.fill(values, VALUE_NULL);
    int length = oldKeys.length;
    for (int i = 0; i < length; i++) {
      long key = oldKeys[i];
      if (key != KEY_NULL && key != REMOVED) {
        put(key, oldValues[i]);
      }
    }
  }

  void iteratorRemove(int lastNext) {
    if (lastNext >= values.length) {
      throw new NoSuchElementException();
    }
    Preconditions.checkState(lastNext >= 0);
    values[lastNext] = VALUE_NULL;
    keys[lastNext] = REMOVED;
    numEntries--;
  }

  @Override
  public FastByIDFloatMap clone() {
    FastByIDFloatMap clone;
    try {
      clone = (FastByIDFloatMap) super.clone();
    } catch (CloneNotSupportedException cnse) {
      throw new AssertionError(cnse);
    }
    clone.keys = keys.clone();
    clone.values = values.clone();
    return clone;
  }

  @Override
  public String toString() {
    if (isEmpty()) {
      return "{}";
    }
    StringBuilder result = new StringBuilder();
    result.append('{');
    for (int i = 0; i < keys.length; i++) {
      long key = keys[i];
      if (key != KEY_NULL && key != REMOVED) {
        result.append(key).append('=').append(values[i]).append(',');
      }
    }
    result.setCharAt(result.length() - 1, '}');
    return result.toString();
  }

  @Override
  public int hashCode() {
    int hash = 0;
    long[] keys = this.keys;
    int max = keys.length;
    for (int i = 0; i < max; i++) {
      long key = keys[i];
      if (key != KEY_NULL && key != REMOVED) {
        hash = 31 * hash + ((int) (key >> 32) ^ (int) key);
        hash = 31 * hash + Doubles.hashCode(values[i]);
      }
    }
    return hash;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof FastByIDFloatMap)) {
      return false;
    }
    FastByIDFloatMap otherMap = (FastByIDFloatMap) other;
    long[] otherKeys = otherMap.keys;
    float[] otherValues = otherMap.values;
    int length = keys.length;
    int otherLength = otherKeys.length;
    int max = FastMath.min(length, otherLength);

    int i = 0;
    while (i < max) {
      long key = keys[i];
      long otherKey = otherKeys[i];
      if (key == KEY_NULL || key == REMOVED) {
        if (otherKey != KEY_NULL && otherKey != REMOVED) {
          return false;
        }
      } else {
        if (key != otherKey || values[i] != otherValues[i]) {
          return false;
        }
      }
      i++;
    }
    while (i < length) {
      long key = keys[i];
      if (key != KEY_NULL && key != REMOVED) {
        return false;
      }
      i++;
    }
    while (i < otherLength) {
      long key = otherKeys[i];
      if (key != KEY_NULL && key != REMOVED) {
        return false;
      }
      i++;
    }
    return true;
  }

  private final class KeyIterator extends AbstractLongPrimitiveIterator {

    private int position;
    private int lastNext = -1;

    @Override
    public boolean hasNext() {
      goToNext();
      return position < keys.length;
    }

    @Override
    public long nextLong() {
      goToNext();
      lastNext = position;
      if (position >= keys.length) {
        throw new NoSuchElementException();
      }
      return keys[position++];
    }

    @Override
    public long peek() {
      goToNext();
      if (position >= keys.length) {
        throw new NoSuchElementException();
      }
      return keys[position];
    }

    private void goToNext() {
      int length = values.length;
      while (position < length && Float.isNaN(values[position])) {
        position++;
      }
    }

    @Override
    public void remove() {
      iteratorRemove(lastNext);
    }

    @Override
    public void skip(int n) {
      position += n;
    }

  }

  public interface MapEntry {
    long getKey();
    float getValue();
    float setValue(float value);
  }

  private final class MapEntryImpl implements MapEntry {

    private int index;

    void setIndex(int index) {
      this.index = index;
    }

    @Override
    public long getKey() {
      return keys[index];
    }

    @Override
    public float getValue() {
      return values[index];
    }

    @Override
    public float setValue(float value) {
      float oldValue = values[index];
      values[index] = value;
      return oldValue;
    }

    @Override
    public String toString() {
      return getKey() + "=" + getValue();
    }

  }

  private final class EntrySet extends AbstractSet<MapEntry> {

    @Override
    public int size() {
      return FastByIDFloatMap.this.size();
    }

    @Override
    public boolean isEmpty() {
      return FastByIDFloatMap.this.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
      return containsKey((Long) o);
    }

    @Override
    public Iterator<MapEntry> iterator() {
      return new EntryIterator();
    }

    @Override
    public boolean add(MapEntry t) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends MapEntry> ts) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> objects) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> objects) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      FastByIDFloatMap.this.clear();
    }

  }

  private final class EntryIterator implements Iterator<MapEntry> {

    private int position;
    private int lastNext = -1;
    private final MapEntryImpl entry = new MapEntryImpl();

    @Override
    public boolean hasNext() {
      goToNext();
      return position < keys.length;
    }

    @Override
    public MapEntry next() {
      goToNext();
      lastNext = position;
      if (position >= keys.length) {
        throw new NoSuchElementException();
      }
      entry.setIndex(position++);
      return entry;
    }

    private void goToNext() {
      int length = values.length;
      while (position < length && Float.isNaN(values[position])) {
        position++;
      }
    }

    @Override
    public void remove() {
      iteratorRemove(lastNext);
    }
  }

}
