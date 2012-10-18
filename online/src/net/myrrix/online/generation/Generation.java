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

package net.myrrix.online.generation;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;

import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.math.MatrixUtils;
import net.myrrix.online.candidate.CandidateFilter;
import net.myrrix.online.candidate.LocationSensitiveHash;

/**
 * Encapsulates one generation of the underlying recommender's model. The data in this object is quite
 * specific to the underlying Alternating Least Squares algorithm. A generation contains:
 *
 * <ul>
 *   <li>X, the user-feature matrix</li>
 *   <li>Y, the item-feature matrix</li>
 *   <li>XLeftInverse, X's left inverse</li>
 *   <li>YTRightInverse, the right inverse of Y transpose</li>
 *   <li>numFeatures, the column dimension of X and Y</li>
 *   <li>knownItemIDs, the item IDs already associated to each user</li>
 * </ul>
 *
 * @author Sean Owen
 * @see net.myrrix.online.factorizer.MatrixFactorizer
 */
public final class Generation {

  public static final String NO_KNOWN_ITEMS_KEY = "model.noKnownItems";

  private final FastByIDMap<FastIDSet> knownItemIDs;
  private final FastByIDMap<FastIDSet> knownUserIDs;
  private final FastByIDMap<float[]> X;
  private RealMatrix XTXinv;
  private final FastByIDMap<float[]> Y;
  private RealMatrix YTYinv;
  private final CandidateFilter candidateFilter;
  private final ReadWriteLock xLock;
  private final ReadWriteLock yLock;
  private final ReadWriteLock knownItemLock;
  private final ReadWriteLock knownUserLock;

  public Generation() {
    this(new FastByIDMap<FastIDSet>(10000, 1.25f),
         new FastByIDMap<float[]>(10000, 1.25f),
         new FastByIDMap<float[]>(10000, 1.25f));
  }
  
  public Generation(FastByIDMap<FastIDSet> knownItemIDs, FastByIDMap<float[]> X, FastByIDMap<float[]> Y) {
    this(knownItemIDs,
         null, // Not used yet
         X,
         null,
         Y,
         null,
         new LocationSensitiveHash(Y),
         new ReentrantReadWriteLock(),
         new ReentrantReadWriteLock(),
         new ReentrantReadWriteLock(),
         null // not used yet
         );
    recomputeInverses();
  }

  private Generation(FastByIDMap<FastIDSet> knownItemIDs,
                     FastByIDMap<FastIDSet> knownUserIDs,
                     FastByIDMap<float[]> X,
                     RealMatrix XTXinv,
                     FastByIDMap<float[]> Y,
                     RealMatrix YTYinv,
                     CandidateFilter candidateFilter,
                     ReadWriteLock xLock,
                     ReadWriteLock yLock,
                     ReadWriteLock knownItemLock,
                     ReadWriteLock knownUserLock) {
    this.knownItemIDs = knownItemIDs;
    this.knownUserIDs = knownUserIDs;
    this.X = X;
    this.XTXinv = XTXinv;
    this.Y = Y;
    this.YTYinv = YTYinv;
    this.candidateFilter = candidateFilter;
    this.xLock = xLock;
    this.yLock = yLock;
    this.knownItemLock = knownItemLock;
    this.knownUserLock = knownUserLock;
  }

  public Generation buildTransposedGeneration() {
    return new Generation(knownUserIDs,
                          knownItemIDs,
                          Y,
                          YTYinv,
                          X,
                          XTXinv,
                          new LocationSensitiveHash(X),
                          yLock,
                          xLock,
                          knownUserLock,
                          knownItemLock);
  }

  /**
   * Normally only called from specialized {@code DelegateGenerationManager} methods.
   */
  void recomputeInverses() {
    XTXinv = recomputeInverse(X, xLock.readLock());
    YTYinv = recomputeInverse(Y, yLock.readLock());
  }

  private static RealMatrix recomputeInverse(FastByIDMap<float[]> M, Lock readLock) {
    readLock.lock();
    try {
      return MatrixUtils.getTransposeTimesSelfInverse(M);
    } finally {
      readLock.unlock();
    }
  }

  /**
   * @return the number of "users" in the model (rows of {@link #getX()}
   */
  public int getNumUsers() {
    return X.size();
  }

  /**
   * @return the number of "items" in the model (rows of {@link #getY()}
   */
  public int getNumItems() {
    return Y.size();
  }

  /**
   * @return the user-feature matrix, implemented as a map from row number (user ID) to feature array
   */
  public FastByIDMap<float[]> getX() {
    return X;
  }

  /**
   * @return the inverse of X' * X
   */
  public RealMatrix getXTXInverse() {
    return XTXinv;
  }

  /**
   * @return the item-feature matrix, implemented as a map from row number (item ID) to feature array
   */
  public FastByIDMap<float[]> getY() {
    return Y;
  }

  /**
   * @return the inverse of Y' * Y
   */
  public RealMatrix getYTYInverse() {
    return YTYinv;
  }

  /**
   * @return the item IDs already associated to each user, as a map from user IDs to a set of item IDs
   */
  public FastByIDMap<FastIDSet> getKnownItemIDs() {
    return knownItemIDs;
  }

  /**
   * Not used, yet.
   * @return null
   */
  public FastByIDMap<FastIDSet> getKnownUserIDs() {
    return knownUserIDs;
  }

  public CandidateFilter getCandidateFilter() {
    return candidateFilter;
  }

  /**
   * Acquire this read/write lock before using {@link #getX()}.
   */
  public ReadWriteLock getXLock() {
    return xLock;
  }

  /**
   * Acquire this read/write lock before using {@link #getY()}.
   */
  public ReadWriteLock getYLock() {
    return yLock;
  }

  /**
   * Acquire this read/write lock before using {@link #getKnownItemIDs()}.
   */
  public ReadWriteLock getKnownItemLock() {
    return knownItemLock;
  }

  /**
   * Not used, yet.
   * @return null
   */
  public ReadWriteLock getKnownUserLock() {
    return knownUserLock;
  }

}
