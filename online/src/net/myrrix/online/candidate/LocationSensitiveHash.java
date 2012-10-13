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

package net.myrrix.online.candidate;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Random;

import com.google.common.collect.Lists;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.common.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.collection.FastByIDMap;

/**
 * <p>This class implements a form of location sensitive hashing (LSH). This is used to quickly, approximately,
 * find the vectors in the same direction as a given vector in a vector space. This is useful in, for example, making
 * recommendations, where the best recommendations are the item vectors with largest dot product with
 * the user vector. And, in turn, the largest dot products are found from vectors that point in the same direction
 * from the origin as the user vector -- small angle between them.</p>
 *
 * <p>This uses 64 hash functions, where the hash function is based on a short vector in a random direction in
 * the space. It suffices to choose a vector whose elements are, randomly, -1 or 1. This is represented as a
 * {@code boolean[]}. The vector defines a hyperplane through the origin, and produces a hash value of 1 or 0
 * depending on whether the given vector is on one side of the hyperplane or the other. This amounts to
 * evaluating whether the dot product of the random vector and given vector is positive or not.</p>
 *
 * <p>These 64 1/0 hash values are combined into a signature of 64 bits, or a {@code long}.</p>
 *
 * <p>"Close" vectors -- those which form small angles together -- point in nearly the same direction and so
 * should generally fall on the same sides of these hyperplanes. That is, they should match in most bits.</p>
 *
 * <p>As a preprocessing step, all item vector signatures are computed, and these define a sort of
 * hash bucket key for item vectors. Item vectors are put into their buckets.</p>
 *
 * <p>To produce a list of candidate item vectors for a given user vector, the user vector's signature is
 * computed. All buckets whose signature matches in "most" bits are matches, and all item vectors inside
 * are candidates.</p>
 *
 * <p><em>This is experimental, and is disabled unless "model.lsh.sampleRatio" is set to a value less than 1.</em></p>
 *
 * @author Sean Owen
 */
public final class LocationSensitiveHash implements CandidateFilter {

  private static final Logger log = LoggerFactory.getLogger(LocationSensitiveHash.class);

  private static final int NUM_HASHES = 64;
  // Since there are n=64 hashes / bits, and random bits differ with probability p=0.5,
  // the expected number of differences in two random n-bit vectors is np = n/2.
  // The variance is np(1-p) = n/4, so stdev is sqrt(n)/2.
  private static final int EXPECTED_RANDOM_VEC_BITS_DIFFERING = NUM_HASHES / 2;
  private static final int STDEV_RANDOM_VEC_BITS_DIFFERING = (int) Math.sqrt(NUM_HASHES / 4);
  private static final double LSH_SAMPLE_RATIO =
      Double.parseDouble(System.getProperty("model.lsh.sampleRatio", "1.0"));

  private final FastByIDMap<float[]> Y;
  private final boolean[][] randomVectors;
  private final double[] meanVector;
  private final FastByIDMap<FastIDSet> buckets;
  private final FastIDSet newItems;
  private final int maxBitsDiffering;

  /**
   * @param Y item vectors to hash
   */
  public LocationSensitiveHash(FastByIDMap<float[]> Y) {

    this.Y = Y;

    if (LSH_SAMPLE_RATIO >= 1.0) {

      randomVectors = null;
      meanVector = null;
      buckets = null;
      newItems = null;
      maxBitsDiffering = NUM_HASHES;

    } else {

      log.info("Using LSH sampling to sample about {}% of items", LSH_SAMPLE_RATIO * 100.0);

      // Assume number of bits differing is normally distributed -- reasonable since user/item vector values
      // are distributed like random vectors and random vectors' number of differing bits is the sum of 64
      // Bernoulli trials -- nearly normal. Pick a number of bit differences to allow to achieve about the
      // desired sampling rate.
      RealDistribution normalDistribution =
          new NormalDistribution(EXPECTED_RANDOM_VEC_BITS_DIFFERING, STDEV_RANDOM_VEC_BITS_DIFFERING);
      int bitsDiffering = 0;
      while (bitsDiffering < NUM_HASHES &&
             normalDistribution.cumulativeProbability(bitsDiffering) < LSH_SAMPLE_RATIO) {
        bitsDiffering++;
      }
      maxBitsDiffering = bitsDiffering;

      int features = Y.entrySet().iterator().next().getValue().length;

      Random r = RandomUtils.getRandom();
      randomVectors = new boolean[NUM_HASHES][features];
      for (boolean[] randomVector : randomVectors) {
        for (int j = 0; j < features; j++) {
          randomVector[j] = r.nextBoolean();
        }
      }

      meanVector = findMean(Y, features);

      buckets = new FastByIDMap<FastIDSet>();
      int count = 0;
      for (FastByIDMap.MapEntry<float[]> entry : Y.entrySet()) {
        long signature = toBitSignature(entry.getValue());
        FastIDSet ids = buckets.get(signature);
        if (ids == null) {
          ids = new FastIDSet();
          buckets.put(signature, ids);
        }
        ids.add(entry.getKey());
        if (++count % 1000000 == 0) {
          log.info("Bucketed {} items", count);
        }
      }

      log.info("Put {} items into {} buckets", Y.size(), buckets.size());

      // A separate bucket for new items, which will always be considered
      newItems = new FastIDSet();

    }

  }

  private static double[] findMean(FastByIDMap<float[]> Y, int features) {
    double[] theMeanVector = new double[features];
    for (FastByIDMap.MapEntry<float[]> entry : Y.entrySet()) {
      float[] vec = entry.getValue();
      for (int i = 0; i < features; i++) {
        theMeanVector[i] += vec[i];
      }
    }
    int size = Y.size();
    for (int i = 0; i < features; i++) {
      theMeanVector[i] /= size;
    }
    return theMeanVector;
  }

  private long toBitSignature(float[] vector) {
    long l = 0L;
    double[] theMeanVector = meanVector;
    for (boolean[] randomVector : randomVectors) {
      // Dot product. true == +1, false == -1
      double total = 0.0;
      for (int i = 0; i < randomVector.length; i++) {
        double delta = vector[i] - theMeanVector[i];
        if (randomVector[i]) {
          total += delta;
        } else {
          total -= delta;
        }
      }
      l <<= 1;
      if (total > 0.0) {
        l |= 1L;
      }
    }
    return l;
  }

  @Override
  public Collection<Iterator<FastByIDMap.MapEntry<float[]>>> getCandidateIterator(float[][] userVectors) {
    if (buckets == null) {
      return Collections.singleton(Y.entrySet().iterator());
    }
    long[] bitSignatures = new long[userVectors.length];
    for (int i = 0; i < userVectors.length; i++) {
      bitSignatures[i] = toBitSignature(userVectors[i]);
    }
    Collection<Iterator<FastByIDMap.MapEntry<float[]>>> inputs = Lists.newArrayList();
    for (FastByIDMap.MapEntry<FastIDSet> entry : buckets.entrySet()) {
      for (long bitSignature : bitSignatures) {
        if (Long.bitCount(bitSignature ^ entry.getKey()) < maxBitsDiffering) { // # bits differing
          inputs.add(new IDToEntryIterator(entry.getValue().iterator()));
          break;
        }
      }
    }

    synchronized (newItems) {
      if (!newItems.isEmpty()) {
        // Have to clone because it's being written to
        inputs.add(new IDToEntryIterator(newItems.clone().iterator()));
      }
    }

    return inputs;
  }

  @Override
  public void addItem(long itemID) {
    if (newItems != null) {
      synchronized (newItems) {
        newItems.add(itemID);
      }
    }
  }

  private final class IDToEntryIterator implements Iterator<FastByIDMap.MapEntry<float[]>> {

    private final LongPrimitiveIterator input;
    private final MutableMapEntry delegate;

    private IDToEntryIterator(LongPrimitiveIterator input) {
      this.input = input;
      this.delegate = new MutableMapEntry();
    }

    @Override
    public boolean hasNext() {
      return input.hasNext();
    }

    @Override
    public FastByIDMap.MapEntry<float[]> next() {
      // Will throw NoSuchElementException if needed:
      long itemID = input.nextLong();
      delegate.set(itemID, Y.get(itemID));
      return delegate;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

  }

  private static final class MutableMapEntry implements FastByIDMap.MapEntry<float[]> {

    private long key;
    private float[] value;

    @Override
    public long getKey() {
      return key;
    }

    @Override
    public float[] getValue() {
      return value;
    }

    public void set(long key, float[] value) {
      this.key = key;
      this.value = value;
    }
  }

}
