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

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.commons.math3.linear.RealMatrix;

import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.math.MatrixUtils;
import net.myrrix.online.candidate.CandidateFilter;
import net.myrrix.online.candidate.CandidateFilterFactory;

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
 *   <li>itemTagIDs, IDs of "users" that are really item tags</li> 
 *   <li>userTagIDs, IDs of "items" that are really user tags</li> 
 *   <li>clusters of item IDs (in distributed mode), with centroids</li>
 *   <li>clusters of user IDs (in distributed mode), with centroids</li>
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
  private final FastIDSet itemTagIDs;
  private final FastIDSet userTagIDs;
  private final List<IDCluster> userClusters;
  private final List<IDCluster> itemClusters;
  private CandidateFilter candidateFilter;
  private final ReadWriteLock xLock;
  private final ReadWriteLock yLock;
  private final ReadWriteLock knownItemLock;
  private final ReadWriteLock knownUserLock;
  private final ReadWriteLock userClustersLock;
  private final ReadWriteLock itemClustersLock;
  
  @Deprecated
  public Generation(FastByIDMap<FastIDSet> knownItemIDs, 
                    FastByIDMap<float[]> X, 
                    FastByIDMap<float[]> Y) {
    this(knownItemIDs, X, Y, new FastIDSet(1000, 1.25f), new FastIDSet(1000, 1.25f));
  }

  public Generation(FastByIDMap<FastIDSet> knownItemIDs, 
                    FastByIDMap<float[]> X, 
                    FastByIDMap<float[]> Y,
                    FastIDSet itemTagIDs,
                    FastIDSet userTagIDs) {
    this(knownItemIDs, X, Y, itemTagIDs, userTagIDs, Lists.<IDCluster>newArrayList(), Lists.<IDCluster>newArrayList());
  }

  public Generation(FastByIDMap<FastIDSet> knownItemIDs,
                    FastByIDMap<float[]> X,
                    FastByIDMap<float[]> Y,
                    FastIDSet itemTagIDs,
                    FastIDSet userTagIDs,
                    List<IDCluster> userClusters,
                    List<IDCluster> itemClusters) {
    // knownItemIDs may be null, specially
    Preconditions.checkNotNull(X);
    Preconditions.checkNotNull(Y);
    Preconditions.checkNotNull(itemTagIDs);
    Preconditions.checkNotNull(userTagIDs);
    Preconditions.checkNotNull(userClusters);
    Preconditions.checkNotNull(itemClusters);
    
    this.knownItemIDs = knownItemIDs;
    this.knownUserIDs = null; // Not used yet
    this.X = X;
    this.XTXinv = null;
    this.Y = Y;
    this.YTYinv = null;
    this.itemTagIDs = itemTagIDs;
    this.userTagIDs = userTagIDs;
    this.userClusters = userClusters;
    this.itemClusters = itemClusters;
    this.candidateFilter = null;
    this.xLock = new ReentrantReadWriteLock();
    this.yLock = new ReentrantReadWriteLock();
    this.knownItemLock = new ReentrantReadWriteLock();
    this.knownUserLock = null; // Not used yet
    this.userClustersLock = new ReentrantReadWriteLock();
    this.itemClustersLock = new ReentrantReadWriteLock();
    recomputeState();
  }

  void recomputeState() {
    XTXinv = recomputeInverse(X, xLock.readLock());
    YTYinv = recomputeInverse(Y, yLock.readLock());
    candidateFilter = CandidateFilterFactory.buildCandidateFilter(Y, yLock.readLock());
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

  /**
   * @return "user" IDs which are actually item tags
   */
  public FastIDSet getItemTagIDs() {
    return itemTagIDs;
  }

  /**
   * @return "item" IDs which are actually user tags
   */
  public FastIDSet getUserTagIDs() {
    return userTagIDs;
  }

  /**
   * @return clusters of user IDs, or {@code null} if not in distributed mode
   */
  public List<IDCluster> getUserClusters() {
    return userClusters;
  }

  /**
   * @return clusters of item IDs, or {@code null} if not in distributed mode
   */
  public List<IDCluster> getItemClusters() {
    return itemClusters;
  }

  public CandidateFilter getCandidateFilter() {
    return candidateFilter;
  }

  /**
   * Acquire this read/write lock before using {@link #getX()} or {@link #getItemTagIDs()}.
   */
  public ReadWriteLock getXLock() {
    return xLock;
  }

  /**
   * Acquire this read/write lock before using {@link #getY()} or {@link #getUserTagIDs()}.
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

  /**
   * Acquire this read/write lock before using {@link #getUserClusters()}.
   */
  public ReadWriteLock getUserClustersLock() {
    return userClustersLock;
  }

  /**
   * Acquire this read/write lock before using {@link #getItemClusters()}.
   */
  public ReadWriteLock getItemClustersLock() {
    return itemClustersLock;
  }

}
