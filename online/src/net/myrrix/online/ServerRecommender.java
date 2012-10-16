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

package net.myrrix.online;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.ClassUtils;
import net.myrrix.common.MutableRecommendedItem;
import net.myrrix.common.NamedThreadFactory;
import net.myrrix.common.io.IOUtils;
import net.myrrix.common.LangUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;
import net.myrrix.common.SimpleVectorMath;
import net.myrrix.common.TopN;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.online.candidate.CandidateFilter;
import net.myrrix.online.factorizer.MatrixUtils;
import net.myrrix.online.generation.Generation;
import net.myrrix.online.generation.GenerationManager;

/**
 * <p>The core implementation of {@link org.apache.mahout.cf.taste.recommender.Recommender} and furthermore
 * {@link MyrrixRecommender} that lies inside the Serving Layer.</p>
 *
 * <p>It is useful to note here, again, that the API methods {@link #setPreference(long, long)}
  * and {@link #removePreference(long, long)}, retained from Apache Mahout, have a somewhat different meaning
  * than in Mahout. They add to an association strength, rather than replace it. See the javadoc.</p>
 */
public final class ServerRecommender implements MyrrixRecommender, Closeable {
  
  private static final Logger log = LoggerFactory.getLogger(ServerRecommender.class);

  private static final Splitter DELIMITER = Splitter.on(CharMatcher.anyOf(",\t"));

  // Maybe expose this publicly later
  private static final double FOLDIN_LEARN_RATE =
      Double.parseDouble(System.getProperty("model.foldin.learningRate", "1.0"));
  // Only temporary
  private static final double BIG_FOLDIN_THRESHOLD =
      Double.parseDouble(System.getProperty("model.foldin.bigThreshold", "10000.0"));

  private final GenerationManager generationManager;
  private final int numCores;
  private ExecutorService executor;

  /**
   * Calls {@link #ServerRecommender(String, long, File, int, int)} for simple local mode, with no bucket,
   * instance ID 0, and no partitions (partition 0 of 1 total).
   */
  public ServerRecommender(File localInputDir) {
    this(null, 0L, localInputDir, 0, 1);
  }

  /**
   * @param bucket bucket that Serving Layer is using for instances
   * @param instanceID instance ID that the Serving Layer is serving. May be 0 for local mode.
   * @param localInputDir local input and model file directory
   * @param partition partition number in a partitioned distributed mode. 0 if not partitioned.
   * @param numPartitions total partitions in partitioned distributed mode. 1 if not partitioned.
   */
  public ServerRecommender(String bucket,
                           long instanceID,
                           File localInputDir,
                           int partition,
                           int numPartitions) {
    Preconditions.checkArgument(instanceID >= 0L, "Bad instance ID %s", instanceID);
    Preconditions.checkNotNull(localInputDir, "No local dir");
    Preconditions.checkArgument(numPartitions > 0, "Bad num partitions %s", numPartitions);
    Preconditions.checkArgument(partition >= 0 && partition < numPartitions, "Bad partition %s", partition);

    log.info("Creating ServingRecommender for bucket {}, instance {} and with local work dir {}, partition {} of {}",
             bucket, instanceID, localInputDir, partition, numPartitions);

    generationManager =
        ClassUtils.loadInstanceOf("net.myrrix.online.generation.DelegateGenerationManager",
                                  GenerationManager.class,
                                  new Class<?>[] { String.class, long.class, File.class, int.class, int.class },
                                  new Object[] { bucket, instanceID, localInputDir, partition, numPartitions });
    numCores = Runtime.getRuntime().availableProcessors();
    executor = null; // Lazy init
  }

  public String getBucket() {
    return generationManager.getBucket();
  }

  public long getInstanceID() {
    return generationManager.getInstanceID();
  }

  public GenerationManager getGenerationManager() {
    return generationManager;
  }

  @Override
  public void refresh(Collection<Refreshable> alreadyRefreshed) {
    generationManager.refresh(alreadyRefreshed);
  }

  @Override
  public void ingest(File file) throws TasteException {
    Reader reader = null;
    try {
      reader = new InputStreamReader(IOUtils.openMaybeDecompressing(file), Charsets.UTF_8);
      ingest(reader);
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    } finally {
      Closeables.closeQuietly(reader);
    }
  }

  @Override
  public void ingest(Reader reader) throws TasteException {
    BufferedReader buffered = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
    try {
      int count = 0;
      String line;
      while ((line = buffered.readLine()) != null) {
        if (++count % 1000000 == 0) {
          log.info("Ingested {} lines", count);
        }
        Iterator<String> tokens = DELIMITER.split(line).iterator();
        long userID = Long.parseLong(tokens.next());
        long itemID = Long.parseLong(tokens.next());
        if (tokens.hasNext()) {
          String token = tokens.next().trim();
          if (token.isEmpty()) {
            removePreference(userID, itemID);
          } else {
            setPreference(userID, itemID, LangUtils.parseFloat(token));
          }
          if (tokens.hasNext()) {
            // Allow a 4th timestamp column like Mahout, but don't parse it
            tokens.next();
            Preconditions.checkState(!tokens.hasNext(), "Line has too many columns");
          }
        } else {
          setPreference(userID, itemID);
        }
      }
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

  @Override
  public void close() throws IOException {
    generationManager.close();
    synchronized (this) {
      if (executor != null) {
        executor.shutdownNow();
      }
    }
  }

  /**
   * @throws NotReadyException if {@link GenerationManager#getCurrentGeneration()} returns null
   */
  private Generation getCurrentGeneration() throws NotReadyException {
    Generation generation = generationManager.getCurrentGeneration();
    if (generation == null) {
      throw new NotReadyException();
    }
    return generation;
  }

  /**
   * Like {@link #recommend(long, int, IDRescorer)} but supplies no rescorer.
   */
  @Override
  public List<RecommendedItem> recommend(long userID, int howMany) throws NoSuchUserException, NotReadyException {
    return recommend(userID, howMany, null);
  }

  /**
   * Like {@link #recommend(long, int, boolean, IDRescorer)} and specifies to not consider known items.
   */
  @Override
  public List<RecommendedItem> recommend(long userID, int howMany, IDRescorer rescorer)
      throws NoSuchUserException, NotReadyException {
    return recommend(userID, howMany, false, rescorer);
  }

  /**
   * @param userID user for which recommendations are to be computed
   * @param howMany desired number of recommendations
   * @param considerKnownItems if true, items that the user is already associated to are candidates
   *  for recommendation. Normally this is {@code false}.
   * @param rescorer rescoring function used to modify association strengths before ranking results
   * @return {@link List} of recommended {@link RecommendedItem}s, ordered from most strongly recommend to least
   * @throws NoSuchUserException if the user is not known in the model
   * @throws NotReadyException if the recommender has no model available yet
   */
  @Override
  public List<RecommendedItem> recommend(long userID,
                                         int howMany,
                                         boolean considerKnownItems,
                                         IDRescorer rescorer) throws NoSuchUserException, NotReadyException {
    return recommendToMany(new long[] { userID }, howMany,  considerKnownItems, rescorer);
  }

  @Override
  public List<RecommendedItem> recommendToMany(long[] userIDs,
                                               int howMany,
                                               boolean considerKnownItems,
                                               IDRescorer rescorer) throws NoSuchUserException, NotReadyException {

    Generation generation = getCurrentGeneration();
    FastByIDMap<float[]> X = generation.getX();

    Lock xLock = generation.getXLock().readLock();
    float[][] userFeatures = new float[userIDs.length][];
    xLock.lock();
    try {
      for (int i = 0; i < userIDs.length; i++) {
        userFeatures[i] = X.get(userIDs[i]);
        if (userFeatures[i] == null) {
          throw new NoSuchUserException(userIDs[i]);
        }
      }
    } finally {
      xLock.unlock();
    }

    FastByIDMap<FastIDSet> knownItemIDs = generation.getKnownItemIDs();
    if (knownItemIDs == null && !considerKnownItems) {
      throw new UnsupportedOperationException("Can't ignore known items because no known items available");
    }
    FastIDSet usersKnownItemIDs = null;
    if (!considerKnownItems) {
      Lock knownItemLock = generation.getKnownItemLock().readLock();
      knownItemLock.lock();
      try {
        for (long userID : userIDs) {
          FastIDSet theKnownItemIDs = knownItemIDs.get(userID);
          if (theKnownItemIDs == null) {
            throw new NoSuchUserException(userID);
          }
          if (usersKnownItemIDs == null) {
            usersKnownItemIDs = theKnownItemIDs;
          } else {
            LongPrimitiveIterator it = usersKnownItemIDs.iterator();
            while (it.hasNext()) {
              if (!theKnownItemIDs.contains(it.nextLong())) {
                it.remove();
              }
            }
          }
          if (usersKnownItemIDs.isEmpty()) {
            break;
          }
        }
      } finally {
        knownItemLock.unlock();
      }
    }

    Lock yLock = generation.getYLock().readLock();
    yLock.lock();
    try {
      return multithreadedTopN(userFeatures,
                               usersKnownItemIDs,
                               rescorer, howMany,
                               generation.getCandidateFilter());
    } finally {
      yLock.unlock();
    }

  }

  private List<RecommendedItem> multithreadedTopN(final float[][] userFeatures,
                                                  final FastIDSet userKnownItemIDs,
                                                  final IDRescorer rescorer,
                                                  final int howMany,
                                                  CandidateFilter candidateFilter) {

    Collection<Iterator<FastByIDMap.MapEntry<float[]>>> candidateIterators =
        candidateFilter.getCandidateIterator(userFeatures);

    int numIterators = candidateIterators.size();
    int parallelism = Math.min(numCores, numIterators);

    final Queue<MutableRecommendedItem> topN = TopN.initialQueue(howMany);

    if (parallelism > 1) {

      synchronized (this) {
        if (executor == null) {
          executor = Executors.newFixedThreadPool(2 * numCores, new NamedThreadFactory(false, "ServerRecommender"));
        }
      }

      final Iterator<Iterator<FastByIDMap.MapEntry<float[]>>> candidateIteratorsIterator =
          candidateIterators.iterator();

      List<Future<?>> futures = Lists.newArrayList();
      for (int i = 0; i < numCores; i++) {
        futures.add(executor.submit(new Callable<Void>() {
          @Override
          public Void call() {
            float[] queueLeastValue = { Float.NEGATIVE_INFINITY };
            while (true) {
              Iterator<FastByIDMap.MapEntry<float[]>> candidateIterator;
              synchronized (candidateIteratorsIterator) {
                if (!candidateIteratorsIterator.hasNext()) {
                  break;
                }
                candidateIterator = candidateIteratorsIterator.next();
              }
              Iterator<RecommendedItem> partialIterator =
                  new RecommendIterator(userFeatures, candidateIterator, userKnownItemIDs, rescorer);
              TopN.selectTopNIntoQueueMultithreaded(topN, queueLeastValue, partialIterator, howMany);
            }
            return null;
          }
        }));
      }
      for (Future<?> future : futures) {
        try {
          future.get();
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        } catch (ExecutionException e) {
          throw new IllegalStateException(e.getCause());
        }
      }

    } else {

      for (Iterator<FastByIDMap.MapEntry<float[]>> candidateIterator : candidateIterators) {
        Iterator<RecommendedItem> partialIterator =
            new RecommendIterator(userFeatures, candidateIterator, userKnownItemIDs, rescorer);
        TopN.selectTopNIntoQueue(topN, partialIterator, howMany);
      }

    }

    return TopN.selectTopNFromQueue(topN, howMany);
  }

  @Override
  public List<RecommendedItem> recommendToAnonymous(long[] itemIDs, int howMany)
      throws NotReadyException, NoSuchItemException {

    Generation generation = getCurrentGeneration();

    FastByIDMap<float[]> Y = generation.getY();
    RealMatrix ytyInv = generation.getYTYInverse();
    if (ytyInv == null) {
      throw new NotReadyException();
    }

    float[] anonymousUserFeatures = null;
    Lock yLock = generation.getYLock().readLock();

    for (long itemID : itemIDs) {
      float[] itemFeatures;
      yLock.lock();
      try {
        itemFeatures = Y.get(itemID);
      } finally {
        yLock.unlock();
      }
      if (itemFeatures == null) {
        throw new NoSuchItemException(itemID);
      }
      double[] userFoldIn = MatrixUtils.multiply(ytyInv, itemFeatures);
      if (anonymousUserFeatures == null) {
        anonymousUserFeatures = new float[userFoldIn.length];
      }
      for (int i = 0; i < anonymousUserFeatures.length; i++) {
        anonymousUserFeatures[i] += userFoldIn[i];
      }
    }

    FastIDSet userKnownItemIDs = new FastIDSet(itemIDs.length, 1.25f);
    for (long itemID : itemIDs) {
      userKnownItemIDs.add(itemID);
    }

    float[][] anonymousFeaturesAsArray = { anonymousUserFeatures };

    yLock.lock();
    try {
      return multithreadedTopN(anonymousFeaturesAsArray,
                               userKnownItemIDs,
                               null,
                               howMany,
                               generation.getCandidateFilter());
    } finally {
      yLock.unlock();
    }
  }

  /**
   * @param userID user ID whose preference is to be estimated
   * @param itemID item ID to estimate preference for
   * @return an estimate of the strength of the association between the user and item. These values are the
   *  same as will be returned from {@link #recommend(long, int)}. They are opaque values and have no interpretation
   *  other than that larger means stronger. The values are typically in the range [0,1] but are not guaranteed
   *  to be so. Note that 0 will be returned if the user or item is not known in the data.
   * @throws NoSuchItemException if the item is not known in the model
   * @throws NotReadyException if the recommender has no model available yet
   */
  @Override
  public float estimatePreference(long userID, long itemID) throws NotReadyException {
    return estimatePreferences(userID, itemID)[0];
  }

  @Override
  public float[] estimatePreferences(long userID, long... itemIDs) throws NotReadyException {
    
    Generation generation = getCurrentGeneration();
    FastByIDMap<float[]> X = generation.getX();
    
    float[] userFeatures;
    Lock xLock = generation.getXLock().readLock();
    xLock.lock();
    try {
      userFeatures = X.get(userID);
    } finally {
      xLock.unlock();
    }
    if (userFeatures == null) {
      return new float[itemIDs.length]; // All 0.0f
    }
    
    FastByIDMap<float[]> Y = generation.getY();

    Lock yLock = generation.getYLock().readLock();
    yLock.lock();
    try {
      float[] result = new float[itemIDs.length];
      for (int i = 0; i < itemIDs.length; i++) {
        long itemID = itemIDs[i];
        float[] itemFeatures = Y.get(itemID);
        if (itemFeatures != null) {
          float value = (float) SimpleVectorMath.dot(itemFeatures, userFeatures);
          Preconditions.checkState(LangUtils.isFinite(value), "Bad estimate");
          result[i] = value;
        } // else leave value at 0.0f
      }
      return result;
    } finally {
      yLock.unlock();
    }
  }

  /**
   * Calls {@link #setPreference(long, long, float)} with value 1.0.
   */
  @Override
  public void setPreference(long userID, long itemID) {
    setPreference(userID, itemID, 1.0f);
  }

  @Override
  public void setPreference(long userID, long itemID, float value) {

    // Record datum
    try {
      generationManager.append(userID, itemID, value);
    } catch (IOException ioe) {
      log.warn("Could not append datum; continuing", ioe);
    }

    Generation generation;
    try {
      generation = getCurrentGeneration();
    } catch (NotReadyException nre) {
      // Corner case -- no model ready so all we can do is record (above). Don't fail the request.
      return;
    }

    FastByIDMap<float[]> X = generation.getX();

    float[] userFeatures;
    ReadWriteLock xLock = generation.getXLock();
    Lock xReadLock = xLock.readLock();
    xReadLock.lock();
    try {
      userFeatures = X.get(userID);
      if (userFeatures == null) {
        int numFeatures = countFeatures(X);
        if (numFeatures > 0) {
          userFeatures = new float[numFeatures];
          Lock xWriteLock = xLock.writeLock();
          xReadLock.unlock();
          xWriteLock.lock();
          try {
            X.put(userID, userFeatures);
          } finally {
            xReadLock.lock();
            xWriteLock.unlock();
          }
        }
      }
    } finally {
      xReadLock.unlock();
    }

    FastByIDMap<float[]> Y = generation.getY();

    boolean newItem = false;
    float[] itemFeatures;
    ReadWriteLock yLock = generation.getYLock();
    Lock yReadLock = yLock.readLock();
    yReadLock.lock();
    try {
      itemFeatures = Y.get(itemID);
      if (itemFeatures == null) {
        newItem = true;
        int numFeatures = countFeatures(Y);
        if (numFeatures > 0) {
          itemFeatures = new float[numFeatures];
          Lock yWriteLock = yLock.writeLock();
          yReadLock.unlock();
          yWriteLock.lock();
          try {
            Y.put(itemID, itemFeatures);
          } finally {
            yReadLock.lock();
            yWriteLock.unlock();
          }
        }
      }
    } finally {
      yReadLock.unlock();
    }

    if (newItem) {
      generation.getCandidateFilter().addItem(itemID);
    }

    if (userFeatures != null && itemFeatures != null) {

      // This is analogous to the weight function in the ALS algorithm.
      double cu = 1 + FOLDIN_LEARN_RATE * Math.abs(value);
      // Distance from 1 reduced proportionally with cu
      double foldInWeight = 1.0 - 1.0 / cu;
      // Negative values treated as literally the opposite of positive values in short-term fold in
      // This is not the same as the case of negative overall values in input to ALS.
      double signedFoldInWeight = Math.signum(value) * foldInWeight;

      // Here, we are using userFeatures, which is a row of X, as if it were a column of X'.
      // This is multiplied on the left by (X'*X)^-1. That's our left-inverse of X or at least the one
      // column we need. Which is what the new data point is multiplied on the left by. The result is a column;
      // we scale by 'value' to complete the multiplication of the fold-in and add it in.
      RealMatrix xtxInverse = generation.getXTXInverse();
      double[] itemFoldIn = xtxInverse == null ? null : MatrixUtils.multiply(xtxInverse, userFeatures);

      // Same, but reversed. Multiply itemFeatures, which is a row of Y, on the right by (Y'*Y)^-1.
      // This is the right-inverse for Y', or at least the row we need. Because of the symmetries we can use
      // the same method above to carry out the multiply; the result is conceptually a row vector.
      // The result is scaled by 'value' and added in.
      RealMatrix ytyInverse = generation.getYTYInverse();
      double[] userFoldIn = ytyInverse == null ? null : MatrixUtils.multiply(ytyInverse, itemFeatures);

      if (itemFoldIn != null) {
        if (SimpleVectorMath.norm(itemFoldIn) > Math.sqrt(itemFeatures.length / 2.0) * BIG_FOLDIN_THRESHOLD) {
          log.warn("Item fold in vector is large; bug?");
        }
        for (int i = 0; i < itemFeatures.length; i++) {
          double delta = signedFoldInWeight * itemFoldIn[i];
          Preconditions.checkState(LangUtils.isFinite(delta));
          itemFeatures[i] += (float) delta;
        }
      }
      if (userFoldIn != null) {
        if (SimpleVectorMath.norm(userFoldIn) > Math.sqrt(userFeatures.length / 2.0) * BIG_FOLDIN_THRESHOLD) {
          log.warn("User fold in vector is large; bug?");
        }
        for (int i = 0; i < userFeatures.length; i++) {
          double delta = signedFoldInWeight * userFoldIn[i];
          Preconditions.checkState(LangUtils.isFinite(delta));
          userFeatures[i] += (float) delta;
        }
      }
    }

    FastByIDMap<FastIDSet> knownItemIDs = generation.getKnownItemIDs();
    if (knownItemIDs != null) {
      FastIDSet userKnownItemIDs;
      ReadWriteLock knownItemLock = generation.getKnownItemLock();
      Lock knownItemReadLock = knownItemLock.readLock();
      knownItemReadLock.lock();
      try {
        userKnownItemIDs = knownItemIDs.get(userID);
        if (userKnownItemIDs == null) {
          userKnownItemIDs = new FastIDSet();
          Lock knownItemWriteLock = knownItemLock.writeLock();
          knownItemReadLock.unlock();
          knownItemWriteLock.lock();
          try {
            knownItemIDs.put(userID, userKnownItemIDs);
          } finally {
            knownItemReadLock.lock();
            knownItemWriteLock.unlock();
          }
        }
      } finally {
        knownItemReadLock.unlock();
      }

      synchronized (userKnownItemIDs) {
        userKnownItemIDs.add(itemID);
      }
    }
  }

  private static int countFeatures(FastByIDMap<float[]> M) {
    // assumes the read lock is held
    return M.isEmpty() ? 0 : M.entrySet().iterator().next().getValue().length;
  }

  @Override
  public void removePreference(long userID, long itemID) {

    // Record datum
    try {
      generationManager.remove(userID, itemID);
    } catch (IOException ioe) {
      log.warn("Could not append datum; continuing", ioe);
    }

    Generation generation;
    try {
      generation = getCurrentGeneration();
    } catch (NotReadyException nre) {
      // Corner case -- no model ready so all we can do is record (above). Don't fail the request.
      return;
    }

    ReadWriteLock knownItemLock = generation.getKnownItemLock();

    boolean removeUser = false;
    FastByIDMap<FastIDSet> knownItemIDs = generation.getKnownItemIDs();
    if (knownItemIDs != null) {

      Lock knownItemReadLock = knownItemLock.readLock();
      FastIDSet userKnownItemIDs;
      knownItemReadLock.lock();
      try {
        userKnownItemIDs = knownItemIDs.get(userID);
      } finally {
        knownItemReadLock.unlock();
      }

      if (userKnownItemIDs == null) {
        // Doesn't exist? So ignore this request
        return;
      }

      synchronized (userKnownItemIDs) {
        if (!userKnownItemIDs.remove(itemID)) {
          // Item unknown, so ignore this request
          return;
        }
        removeUser = userKnownItemIDs.isEmpty();
      }
    }

    // We can proceed with the request

    FastByIDMap<float[]> X = generation.getX();

    ReadWriteLock xLock = generation.getXLock();

    if (removeUser) {

      if (knownItemIDs != null) {
        Lock knownItemWriteLock = knownItemLock.writeLock();
        knownItemWriteLock.lock();
        try {
          knownItemIDs.remove(userID);
        } finally {
          knownItemWriteLock.unlock();
        }
      }

      Lock xWriteLock = xLock.writeLock();
      xWriteLock.lock();
      try {
        X.remove(userID);
      } finally {
        xWriteLock.unlock();
      }

    }

  }

  /**
   * One-argument version of {@link #mostSimilarItems(long[], int)}.
   */
  @Override
  public List<RecommendedItem> mostSimilarItems(long itemID, int howMany)
      throws NoSuchItemException, NotReadyException {
    return mostSimilarItems(itemID, howMany, null);
  }

  /**
   * One-argument version of {@link #mostSimilarItems(long[], int, Rescorer)}.
   */
  @Override
  public List<RecommendedItem> mostSimilarItems(long itemID, int howMany, Rescorer<LongPair> rescorer)
      throws NoSuchItemException, NotReadyException {

    Generation generation = getCurrentGeneration();
    FastByIDMap<float[]> Y = generation.getY();

    Lock yLock = generation.getYLock().readLock();
    yLock.lock();
    try {

      float[] itemFeatures = Y.get(itemID);
      if (itemFeatures == null) {
        throw new NoSuchItemException(itemID);
      }

      return TopN.selectTopN(new MostSimilarItemIterator(Y.entrySet().iterator(),
                                                         new long[] { itemID },
                                                         new float[][] { itemFeatures },
                                                         rescorer),
                             howMany);
    } finally {
      yLock.unlock();
    }

  }

  /**
   * Like {@link #mostSimilarItems(long[], int, Rescorer)} but uses no rescorer.
   */
  @Override
  public List<RecommendedItem> mostSimilarItems(long[] itemIDs, int howMany)
      throws NoSuchItemException, NotReadyException {
    return mostSimilarItems(itemIDs, howMany, null);
  }

  /**
   * Computes items most similar to an item or items. The returned items have the highest average similarity
   * to the given items.
   *
   * @param itemIDs items for which most similar items are required
   * @param howMany maximum number of similar items to return; fewer may be returned
   * @param rescorer rescoring function used to modify item-item similarities before ranking results
   * @return {@link RecommendedItem}s representing the top recommendations for the user, ordered by quality,
   *  descending. The score associated to it is an opaque value. Larger means more similar, but no further
   *  interpretation may necessarily be applied.
   * @throws NoSuchItemException if any of the items is not known in the model
   * @throws NotReadyException if the recommender has no model available yet
   */
  @Override
  public List<RecommendedItem> mostSimilarItems(long[] itemIDs, int howMany, Rescorer<LongPair> rescorer)
      throws NoSuchItemException, NotReadyException {

    Generation generation = getCurrentGeneration();
    FastByIDMap<float[]> Y = generation.getY();

    Lock yLock = generation.getYLock().readLock();
    yLock.lock();
    try {

      float[][] itemFeatures = new float[itemIDs.length][];
      for (int i = 0; i < itemIDs.length; i++) {
        long itemID = itemIDs[i];
        float[] features = Y.get(itemID);
        if (features == null) {
          throw new NoSuchItemException(itemID);
        }
        itemFeatures[i] = features;
      }

      return TopN.selectTopN(new MostSimilarItemIterator(Y.entrySet().iterator(), itemIDs, itemFeatures, rescorer),
                             howMany);
    } finally {
      yLock.unlock();
    }
  }

  /**
   * <p>Lists the items that were most influential in recommending a given item to a given user. Exactly how this
   * is determined is left to the implementation, but, generally this will return items that the user prefers
   * and that are similar to the given item.</p>
   *
   * <p>These values by which the results are ordered are opaque values and have no interpretation
   * other than that larger means stronger.</p>
   *
   * @param userID ID of user who was recommended the item
   * @param itemID ID of item that was recommended
   * @param howMany maximum number of items to return
   * @return {@link List} of {@link RecommendedItem}, ordered from most influential in recommended the given
   *  item to least
   * @throws NoSuchUserException if the user is not known in the model
   * @throws NoSuchItemException if the item is not known in the model
   * @throws NotReadyException if the recommender has no model available yet
   */
  @Override
  public List<RecommendedItem> recommendedBecause(long userID, long itemID, int howMany)
      throws NoSuchUserException, NoSuchItemException, NotReadyException {

    Generation generation = getCurrentGeneration();
    FastByIDMap<FastIDSet> knownItemIDs = generation.getKnownItemIDs();
    if (knownItemIDs == null) {
      throw new UnsupportedOperationException("No known item IDs available");
    }

    Lock knownItemLock = generation.getKnownItemLock().readLock();
    FastIDSet userKnownItemIDs;
    knownItemLock.lock();
    try {
      userKnownItemIDs = knownItemIDs.get(userID);
    } finally {
      knownItemLock.unlock();
    }
    if (userKnownItemIDs == null) {
      throw new NoSuchUserException(userID);
    }

    FastByIDMap<float[]> Y = generation.getY();

    Lock yLock = generation.getYLock().readLock();
    yLock.lock();
    try {

      float[] features = Y.get(itemID);
      if (features == null) {
        throw new NoSuchItemException(itemID);
      }
      FastByIDMap<float[]> toFeatures;
      synchronized (userKnownItemIDs) {
        toFeatures = new FastByIDMap<float[]>(userKnownItemIDs.size(), 1.25f);
        LongPrimitiveIterator it = userKnownItemIDs.iterator();
        while (it.hasNext()) {
          long fromItemID = it.nextLong();
          float[] fromFeatures = Y.get(fromItemID);
          toFeatures.put(fromItemID, fromFeatures);
        }
      }

      return TopN.selectTopN(new RecommendedBecauseIterator(toFeatures.entrySet().iterator(), features), howMany);
    } finally {
      yLock.unlock();
    }
  }

  @Override
  public boolean isReady() {
    try {
      getCurrentGeneration();
      return true;
    } catch (NotReadyException nre) {
      return false;
    }
  }

  @Override
  public void await() {
    while (!isReady()) {
      try {
        Thread.sleep(1000L);
      } catch (InterruptedException e) {
        // continue
      }
    }
  }

  @Override
  public FastIDSet getAllUserIDs() throws NotReadyException {
    Generation generation = getCurrentGeneration();
    return getIDsFromKeys(generation.getX(), generation.getXLock().readLock());
  }

  @Override
  public FastIDSet getAllItemIDs() throws NotReadyException {
    Generation generation = getCurrentGeneration();
    return getIDsFromKeys(generation.getY(), generation.getYLock().readLock());
  }

  private static FastIDSet getIDsFromKeys(FastByIDMap<float[]> map, Lock readLock) {
    readLock.lock();
    try {
      FastIDSet ids = new FastIDSet(map.size(), 1.25f);
      LongPrimitiveIterator it = map.keySetIterator();
      while (it.hasNext()) {
        ids.add(it.nextLong());
      }
      return ids;
    } finally {
      readLock.unlock();
    }
  }

  /**
   * @throws UnsupportedOperationException
   * @deprecated do not call
   */
  @Deprecated
  @Override
  public DataModel getDataModel() {
    throw new UnsupportedOperationException();
  }

  /**
   * {@code excludeItemIfNotSimilarToAll} is not applicable in this implementation.
   *
   * @return {@link #mostSimilarItems(long[], int)} if excludeItemIfNotSimilarToAll is false
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #mostSimilarItems(long[], int)} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> mostSimilarItems(long[] itemIDs,
                                                int howMany,
                                                boolean excludeItemIfNotSimilarToAll)
      throws NoSuchItemException, NotReadyException {
    if (excludeItemIfNotSimilarToAll) {
      throw new UnsupportedOperationException();
    }
    return mostSimilarItems(itemIDs, howMany);
  }

  /**
   * {@code excludeItemIfNotSimilarToAll} is not applicable in this implementation.
   *
   * @return {@link #mostSimilarItems(long[], int, Rescorer)} if excludeItemIfNotSimilarToAll is false
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #mostSimilarItems(long[], int, Rescorer)} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> mostSimilarItems(long[] itemIDs,
                                                int howMany,
                                                Rescorer<LongPair> rescorer,
                                                boolean excludeItemIfNotSimilarToAll)
      throws NoSuchItemException, NotReadyException {
    if (excludeItemIfNotSimilarToAll) {
      throw new UnsupportedOperationException();
    }
    return mostSimilarItems(itemIDs, howMany);
  }

}
