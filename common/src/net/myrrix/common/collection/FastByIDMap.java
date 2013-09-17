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
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.impl.common.AbstractLongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;

import net.myrrix.common.random.RandomUtils;

/**
 * Based on Mahout's {@code FastByIDMap}; adds new methods.
 *
 * @author Sean Owen
 * @author Mahout
 * @since 1.0
 * @param <V> value type of mapping
 */
public final class FastByIDMap<V> implements Serializable, Cloneable {
  
  private static final double DEFAULT_LOAD_FACTOR = 1.25;
  private static final int MAX_SIZE =
      (int) (RandomUtils.MAX_INT_SMALLER_TWIN_PRIME / DEFAULT_LOAD_FACTOR);

  /** Dummy object used to represent a key that has been removed. */
  private static final long REMOVED = Long.MAX_VALUE;
  private static final long NULL = Long.MIN_VALUE;

  // For faster access
  long[] keys;
  V[] values;
  private int numEntries;
  private int numSlotsUsed;

  /** Creates a new  with default capacity. */
  public FastByIDMap() {
    this(2);
  }

  /**
   * Creates a new  whose capacity can accommodate the given number of entries without
   * rehash.
   *
   * @param size desired capacity
   * @throws IllegalArgumentException if size is less than 0, maxSize is less than 1
   *  or at least half of {@link RandomUtils#MAX_INT_SMALLER_TWIN_PRIME}, or
   *  loadFactor is less than 1
   */
  public FastByIDMap(int size) {
    Preconditions.checkArgument(size >= 0, "size must be at least 0");
    Preconditions.checkArgument(size < MAX_SIZE, "size must be less than " + MAX_SIZE);
    int hashSize = RandomUtils.nextTwinPrime((int) (DEFAULT_LOAD_FACTOR * size) + 1);
    keys = new long[hashSize];
    Arrays.fill(keys, NULL);

    @SuppressWarnings("unchecked")
    V[] theValues = (V[]) new Object[hashSize];
    values = theValues;
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
    while (currentKey != NULL && key != currentKey) {
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
    while (currentKey != NULL && currentKey != REMOVED && key != currentKey) {
      index -= index < jump ? jump - hashSize : jump;
      currentKey = keys[index];
    }
    if (currentKey != REMOVED) {
      return index;
    }
    // If we're adding, it's here, but, the key might have a value already later
    int addIndex = index;
    while (currentKey != NULL && key != currentKey) {
      index -= index < jump ? jump - hashSize : jump;
      currentKey = keys[index];
    }
    return key == currentKey ? index : addIndex;
  }
  
  public V get(long key) {
    if (key == NULL) {
      return null;
    }
    int index = find(key);
    return values[index];
  }
  
  public int size() {
    return numEntries;
  }
  
  public boolean isEmpty() {
    return numEntries == 0;
  }
  
  public boolean containsKey(long key) {
    return key != NULL && key != REMOVED && keys[find(key)] != NULL;
  }
  
  public boolean containsValue(Object value) {
    if (value == null) {
      return false;
    }
    for (V theValue : values) {
      if (value.equals(theValue)) {
        return true;
      }
    }
    return false;
  }
  
  public V put(long key, V value) {
    Preconditions.checkArgument(key != NULL && key != REMOVED && value != null);
    // If less than half the slots are open, let's clear it up
    if (numSlotsUsed * DEFAULT_LOAD_FACTOR >= keys.length) {
      // If over half the slots used are actual entries, let's grow
      if (numEntries * DEFAULT_LOAD_FACTOR >= numSlotsUsed) {
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
      V oldValue = values[index];
      values[index] = value;
      return oldValue;
    }
    keys[index] = key;
    values[index] = value;
    numEntries++;
    if (keyIndex == NULL) {
      numSlotsUsed++;
    }
    return null;
  }
  
  public V remove(long key) {
    if (key == NULL || key == REMOVED) {
      return null;
    }
    int index = find(key);
    if (keys[index] == NULL) {
      return null;
    } else {
      keys[index] = REMOVED;
      numEntries--;
      V oldValue = values[index];
      values[index] = null;
      // don't decrement numSlotsUsed
      return oldValue;
    }
    // Could un-set recentlyAccessed's bit but doesn't matter
  }
  
  public void clear() {
    numEntries = 0;
    numSlotsUsed = 0;
    Arrays.fill(keys, NULL);
    Arrays.fill(values, null);
  }
  
  public LongPrimitiveIterator keySetIterator() {
    return new KeyIterator();
  }
  
  public Set<MapEntry<V>> entrySet() {
    return new EntrySet();
  }

  public Collection<V> values() {
    return new ValueCollection();
  }
  
  public void rehash() {
    rehash(RandomUtils.nextTwinPrime((int) (DEFAULT_LOAD_FACTOR * numEntries) + 1));
  }
  
  private void growAndRehash() {
    Preconditions.checkState(keys.length * DEFAULT_LOAD_FACTOR < RandomUtils.MAX_INT_SMALLER_TWIN_PRIME,
                             "Can't grow any more");
    rehash(RandomUtils.nextTwinPrime((int) (DEFAULT_LOAD_FACTOR * keys.length) + 1));
  }
  
  private void rehash(int newHashSize) {
    long[] oldKeys = keys;
    V[] oldValues = values;
    numEntries = 0;
    numSlotsUsed = 0;
    keys = new long[newHashSize];
    Arrays.fill(keys, NULL);

    @SuppressWarnings("unchecked")
    V[] theValues = (V[]) new Object[newHashSize];
    values = theValues;

    int length = oldKeys.length;
    for (int i = 0; i < length; i++) {
      long key = oldKeys[i];
      if (key != NULL && key != REMOVED) {
        put(key, oldValues[i]);
      }
    }
  }
  
  void iteratorRemove(int lastNext) {
    if (lastNext >= values.length) {
      throw new NoSuchElementException();
    }
    Preconditions.checkState(lastNext >= 0);
    values[lastNext] = null;
    keys[lastNext] = REMOVED;
    numEntries--;
  }
  
  @Override
  public FastByIDMap<V> clone() {
    FastByIDMap<V> clone;
    try {
      @SuppressWarnings("unchecked")
      FastByIDMap<V> theClone = (FastByIDMap<V>) super.clone();
      clone = theClone;
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
      if (key != NULL && key != REMOVED) {
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
      if (key != NULL && key != REMOVED) {
        hash = 31 * hash + ((int) (key >> 32) ^ (int) key);
        hash = 31 * hash + values[i].hashCode();
      }
    }
    return hash;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof FastByIDMap)) {
      return false;
    }
    @SuppressWarnings("unchecked")
    FastByIDMap<V> otherMap = (FastByIDMap<V>) other;
    long[] otherKeys = otherMap.keys;
    V[] otherValues = otherMap.values;
    int length = keys.length;
    int otherLength = otherKeys.length;
    int max = FastMath.min(length, otherLength);

    int i = 0;
    while (i < max) {
      long key = keys[i];
      long otherKey = otherKeys[i];
      if (key == NULL || key == REMOVED) {
        if (otherKey != NULL && otherKey != REMOVED) {
          return false;
        }
      } else {
        if (key != otherKey || !values[i].equals(otherValues[i])) {
          return false;
        }
      }
      i++;
    }
    while (i < length) {
      long key = keys[i];
      if (key != NULL && key != REMOVED) {
        return false;
      }
      i++;
    }
    while (i < otherLength) {
      long key = otherKeys[i];
      if (key != NULL && key != REMOVED) {
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
      while (position < length && values[position] == null) {
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
  
  private final class EntrySet extends AbstractSet<MapEntry<V>> {
    
    @Override
    public int size() {
      return FastByIDMap.this.size();
    }
    
    @Override
    public boolean isEmpty() {
      return FastByIDMap.this.isEmpty();
    }
    
    @Override
    public boolean contains(Object o) {
      return containsKey((Long) o);
    }
    
    @Override
    public Iterator<MapEntry<V>> iterator() {
      return new EntryIterator();
    }
    
    @Override
    public boolean add(MapEntry<V> t) {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public boolean addAll(Collection<? extends MapEntry<V>> ts) {
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
      FastByIDMap.this.clear();
    }
    
  }

  public interface MapEntry<V> {
    long getKey();
    V getValue();
  }
  
  private final class MapEntryImpl implements MapEntry<V> {
      
    private int index;
    
    void setIndex(int index) {
      this.index = index;
    }
    
    @Override
    public long getKey() {
      return keys[index];
    }
    
    @Override
    public V getValue() {
      return values[index];
    }
    
    public V setValue(V value) {
      V oldValue = values[index];
      values[index] = value;
      return oldValue;
    }

    @Override
    public String toString() {
      return getKey() + "=" + getValue();
    }

  }
  
  private final class EntryIterator implements Iterator<MapEntry<V>> {
    
    private int position;
    private int lastNext = -1;
    private final MapEntryImpl entry = new MapEntryImpl();
    
    @Override
    public boolean hasNext() {
      goToNext();
      return position < keys.length;
    }
    
    @Override
    public MapEntry<V> next() {
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
      while (position < length && values[position] == null) {
        position++;
      }
    }
    
    @Override
    public void remove() {
      iteratorRemove(lastNext);
    }
  }

  private final class ValueCollection extends AbstractCollection<V> {

    @Override
    public int size() {
      return FastByIDMap.this.size();
    }

    @Override
    public boolean isEmpty() {
      return FastByIDMap.this.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
      return containsValue(o);
    }

    @Override
    public Iterator<V> iterator() {
      return new ValueIterator();
    }

    @Override
    public boolean add(V v) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends V> vs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> objects) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> objects) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      FastByIDMap.this.clear();
    }

    private final class ValueIterator implements Iterator<V> {

      private int position;
      private int lastNext = -1;

      @Override
      public boolean hasNext() {
        goToNext();
        return position < values.length;
      }

      @Override
      public V next() {
        goToNext();
        lastNext = position;
        if (position >= values.length) {
          throw new NoSuchElementException();
        }
        return values[position++];
      }

      private void goToNext() {
        int length = values.length;
        while (position < length && values[position] == null) {
          position++;
        }
      }

      @Override
      public void remove() {
        iteratorRemove(lastNext);
      }

    }

  }
}
