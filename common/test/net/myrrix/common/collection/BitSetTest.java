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

import org.apache.commons.math3.random.RandomGenerator;
import org.junit.Test;

import net.myrrix.common.MyrrixTest;
import net.myrrix.common.random.RandomManager;

/**
 * @author Sean Owen, Mahout
 */
public final class BitSetTest extends MyrrixTest {

  private static final int NUM_BITS = 100;

  @Test
  public void testGetSet() {
    BitSet bitSet = new BitSet(NUM_BITS);
    for (int i = 0; i < NUM_BITS; i++) {
      assertFalse(bitSet.get(i));
    }
    bitSet.set(0);
    bitSet.set(NUM_BITS-1);
    assertTrue(bitSet.get(0));
    assertTrue(bitSet.get(NUM_BITS-1));
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void testBounds1() {
    BitSet bitSet = new BitSet(NUM_BITS);
    bitSet.set(1000);
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void testBounds2() {
    BitSet bitSet = new BitSet(NUM_BITS);
    bitSet.set(-1);
  }

  @Test
  public void testClear() {
    BitSet bitSet = new BitSet(NUM_BITS);
    for (int i = 0; i < NUM_BITS; i++) {
      bitSet.set(i);
    }
    for (int i = 0; i < NUM_BITS; i++) {
      assertTrue(bitSet.get(i));
    }
    bitSet.clear();
    for (int i = 0; i < NUM_BITS; i++) {
      assertFalse(bitSet.get(i));
    }
  }

  @Test
  public void testSize() {
    BitSet bitSet = new BitSet(NUM_BITS);
    assertEquals(128, bitSet.size());
  }

  @Test
  public void testCardinality() {
    BitSet bitSet = new BitSet(NUM_BITS);
    assertEquals(0, bitSet.cardinality());
    bitSet.set(0);
    assertEquals(1, bitSet.cardinality());
    bitSet.set(0);
    assertEquals(1, bitSet.cardinality());
    bitSet.set(1);
    assertEquals(2, bitSet.cardinality());
    bitSet.set(63);
    assertEquals(3, bitSet.cardinality());
    bitSet.set(64);
    assertEquals(4, bitSet.cardinality());
    bitSet.set(NUM_BITS - 1);
    assertEquals(5, bitSet.cardinality());
    bitSet.clear(0);
    assertEquals(4, bitSet.cardinality());
  }

  @Test
  public void testNextSetBit() {
    BitSet bitSet = new BitSet(NUM_BITS);
    assertEquals(-1, bitSet.nextSetBit(0));

    bitSet.set(3);
    assertEquals(3, bitSet.nextSetBit(0));
    assertEquals(3, bitSet.nextSetBit(2));
    assertEquals(3, bitSet.nextSetBit(3));
    assertEquals(-1, bitSet.nextSetBit(4));

    bitSet.set(5);
    bitSet.set(7);
    assertEquals(5, bitSet.nextSetBit(4));
    assertEquals(5, bitSet.nextSetBit(5));
    assertEquals(7, bitSet.nextSetBit(6));

    bitSet.set(64);
    assertEquals(64, bitSet.nextSetBit(8));
    assertEquals(64, bitSet.nextSetBit(63));
    assertEquals(64, bitSet.nextSetBit(64));
    assertEquals(-1, bitSet.nextSetBit(65));
    assertEquals(-1, bitSet.nextSetBit(NUM_BITS - 1));
  }

  @Test
  public void testNextBitSetRandom() {
    RandomGenerator random = RandomManager.getRandom();
    for (int i = 0; i < 100; i++) {
      BitSet bitSet = new BitSet(NUM_BITS);
      for (int j = 0; j < 20 + random.nextInt(50); j++) {
        bitSet.set(random.nextInt(NUM_BITS));
      }
      int from = random.nextInt(NUM_BITS);
      int nextSet = bitSet.nextSetBit(from);
      if (nextSet == -1) {
        for (int j = from; j < NUM_BITS; j++) {
          assertFalse(bitSet.get(j));
        }
      } else {
        for (int j = from; j < nextSet; j++) {
          assertFalse(bitSet.get(j));
        }
        assertTrue(bitSet.get(nextSet));
      }
    }
  }

  @Test
  public void testClone() {
    BitSet bitSet = new BitSet(NUM_BITS);
    bitSet.set(NUM_BITS-1);
    bitSet = bitSet.clone();
    assertTrue(bitSet.get(NUM_BITS-1));
  }

}