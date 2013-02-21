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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.ExecutorUtils;
import net.myrrix.common.ReloadingReference;
import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.io.IOUtils;
import net.myrrix.online.factorizer.MatrixFactorizer;
import net.myrrix.online.factorizer.als.AlternatingLeastSquares;

/**
 * <p>Manages one generation of the underlying recommender model. Input is read from a local file system,
 * written to a local file system, and the intermediate model is stored on a local file system.</p>
 *
 * @author Sean Owen
 */
public final class DelegateGenerationManager implements GenerationManager {

  private static final Logger log = LoggerFactory.getLogger(DelegateGenerationManager.class);

  private static final int WRITES_BETWEEN_REBUILD;
  static {
    WRITES_BETWEEN_REBUILD =
          Integer.parseInt(System.getProperty("model.local.writesBetweenRebuild", "100000"));
    Preconditions.checkArgument(WRITES_BETWEEN_REBUILD > 0,
                                "Bad model.local.writesBetweenRebuild: %s", WRITES_BETWEEN_REBUILD);
  }

  private final File inputDir;
  private final File modelFile;
  private final File appendFile;
  private Writer appender;
  private Generation currentGeneration;
  private final FastIDSet recentlyActiveUsers;
  private final FastIDSet recentlyActiveItems;
  private int countdownToRebuild;
  private final ExecutorService refreshExecutor;
  private final Semaphore refreshSemaphore;

  public DelegateGenerationManager(File localInputDir) throws IOException {
    this(null, null, localInputDir, 0, null, null);
  }

  /**
   * @param bucket not used in local mode; required for API compatibility internally
   * @param instanceID not used in local mode; required for API compatibility internally
   * @param localInputDir local work directory from which input is read,
   *  and to which additional input is written. The model file is stored here too. Input in CSV format can
   *  be placed in this directory at any time. The file name should end in ".csv" and the file should
   *  contain lines of the form "userID,itemID(,value)".
   * @param partition not used; required for API compatibility internally
   * @param allPartitions not used; required for API compatibility internally
   * @param licenseFile not used; required for API compatibility internally
   */
  public DelegateGenerationManager(String bucket,
                                   String instanceID,
                                   File localInputDir,
                                   int partition,
                                   ReloadingReference<List<?>> allPartitions,
                                   File licenseFile) throws IOException {
    // Most arguments above are unused but here for compatibility with other DelegateGenerationManager
    log.info("Using local computation, and data in {}", localInputDir);
    inputDir = localInputDir;
    if (!inputDir.exists() || !inputDir.isDirectory()) {
      throw new FileNotFoundException(inputDir.toString());
    }

    modelFile = new File(inputDir, "model.bin.gz");
    appendFile = new File(inputDir, "append.bin.gz");

    recentlyActiveUsers = new FastIDSet();
    recentlyActiveItems = new FastIDSet();

    countdownToRebuild = WRITES_BETWEEN_REBUILD;
    refreshExecutor = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("LocalGenerationManager-%d").build());
    refreshSemaphore = new Semaphore(1);
    refresh();
  }

  /**
   * Not used.
   * @return null
   */
  @Override
  public String getBucket() {
    return null;
  }

  /**
   * Not used.
   * @return null
   */
  @Override
  public String getInstanceID() {
    return null;
  }

  @Override
  public void append(long userID, long itemID, float value, boolean bulk) throws IOException {
    StringBuilder line = new StringBuilder(32);
    line.append(userID).append(',').append(itemID).append(',').append(value).append('\n');
    if (appender != null) {
      synchronized (this) {
        appender.append(line);
      }
    }
    maybeRefresh(bulk);
  }

  @Override
  public void remove(long userID, long itemID, boolean bulk) throws IOException {
    StringBuilder line = new StringBuilder(24);
    line.append(userID).append(',').append(itemID).append(",\n");
    synchronized (this) {
      appender.append(line);
      recentlyActiveUsers.add(userID);
      recentlyActiveItems.add(itemID);
    }
    maybeRefresh(bulk);
  }

  @Override
  public void bulkDone() throws IOException {
    synchronized (this) {
      appender.flush();
    }
    maybeRefresh(false);
  }

  private void maybeRefresh(boolean bulk) {
    if (--countdownToRebuild <= 0 && !bulk) {
      countdownToRebuild = WRITES_BETWEEN_REBUILD;
      refresh();
    }
  }

  private synchronized void closeAppender() throws IOException {
    if (appender != null) {
      try {
        appender.close(); // Want to know of output stream close failed -- maybe failed to write
      } catch (IOException ioe) {
        // But proceed anyway with what was written
        log.warn("Failed to close appender cleanly", ioe);
      }
      if (appendFile.exists()) {
        if (appendFile.length() > 20) { // 20 is size of gzip header; <= 20 means empty
          Files.move(appendFile, new File(inputDir, System.currentTimeMillis() + ".csv.gz"));
        } else {
          log.info("File appears to have no data, deleting: {}", appendFile);
          if (!appendFile.delete()) {
            log.warn("Could not delete {}", appendFile);
          }
        }
      }
    }
  }

  @Override
  public void close() throws IOException {
    ExecutorUtils.shutdownNowAndAwait(refreshExecutor);
    closeAppender();
  }

  @Override
  public void refresh() {
    
    Writer theAppender = appender;
    if (theAppender != null) {
      try {
        synchronized (this) {
          theAppender.flush();
        }
      } catch (IOException e) {
        log.warn("Exception while flushing", e);
      }
    }
    
    if (refreshSemaphore.tryAcquire()) {
      refreshExecutor.submit(new Callable<Void>() {
        @Override
        public Void call() {
          try {

            synchronized (DelegateGenerationManager.this) {
              closeAppender();
              // A small buffer is needed here, but GZIPOutputStream already provides a substantial native buffer
              appender = IOUtils.buildGZIPWriter(appendFile);
            }

            try {
              Generation newGeneration = null;
              if (currentGeneration == null && modelFile.exists()) {
                newGeneration = readModel(modelFile);
              }
              boolean computedModel = false;
              if (newGeneration == null) {
                newGeneration = computeModel(inputDir, currentGeneration);
                computedModel = true;
              }
              if (newGeneration == null) {
                log.info("No data yet");
              } else {
                if (computedModel) {
                  saveModel(newGeneration, modelFile);
                }
                log.info("New generation has {} users, {} items",
                         newGeneration.getNumUsers(), newGeneration.getNumItems());
                currentGeneration = newGeneration;
              }
            } catch (OutOfMemoryError oome) {
              log.warn("Increase heap size with -Xmx, decrease new generation size with larger " +
                       "-XX:NewRatio value, and/or use -XX:+UseCompressedOops");
              currentGeneration = null;
              throw oome;
            } catch (SingularMatrixException sme) {
              log.warn("Unable to compute a valid generation yet; waiting for more data");
              currentGeneration = null;
            }

          } catch (Throwable t) {
            log.warn("Unexpected exception while refreshing", t);

          } finally {
            refreshSemaphore.release();
          }
          return null;
        }

      });
    } else {
      log.info("Refresh already in progress");
    }
  }

  @Override
  public Generation getCurrentGeneration() {
    return currentGeneration;
  }

  /**
   * Reads an existing model file from a file, or null if it is valid but out of date, needing rebuild.
   *
   * @see #saveModel(Generation, File)
   */
  private static Generation readModel(File modelFile) throws IOException {
    log.info("Reading model from {}", modelFile);
    try {
      return GenerationSerializer.readGeneration(modelFile);
    } catch (ObjectStreamException ose) {
      log.warn("Model file was not readable, rebuilding ({})", ose);
      return null;
    }
  }

  /**
   * Saves a model (as a {@link Generation} to a given file.
   *
   * @see #readModel(File)
   */
  private static void saveModel(Generation generation, File modelFile) throws IOException {

    File newModelFile = File.createTempFile(DelegateGenerationManager.class.getSimpleName(), ".bin.gz");
    log.info("Writing model to {}", newModelFile);

    try {
      GenerationSerializer.writeGeneration(generation, newModelFile);
    } catch (IOException ioe) {
      if (newModelFile.exists() && !newModelFile.delete()) {
        log.warn("Could not delete {}", newModelFile);
      }
      throw ioe;
    }

    log.info("Done, moving into place at {}", modelFile);
    if (modelFile.exists() && !modelFile.delete()) {
      log.warn("Could not delete old {}", modelFile);
    }
    Files.move(newModelFile, modelFile);
  }

  private Generation computeModel(File inputDir, Generation currentGeneration) throws IOException {

    log.info("Computing model from input in {}", inputDir);

    FastByIDMap<FastIDSet> knownItemIDs;
    if (Boolean.valueOf(System.getProperty(Generation.NO_KNOWN_ITEMS_KEY))) {
      knownItemIDs = null;
    } else {
      knownItemIDs = new FastByIDMap<FastIDSet>(10000, 1.25f);
    }
    FastByIDMap<FastByIDFloatMap> RbyRow = new FastByIDMap<FastByIDFloatMap>(10000, 1.25f);
    FastByIDMap<FastByIDFloatMap> RbyColumn = new FastByIDMap<FastByIDFloatMap>(10000, 1.25f);

    InputFilesReader.readInputFiles(knownItemIDs, RbyRow, RbyColumn, inputDir);

    if (RbyRow.isEmpty() || RbyColumn.isEmpty()) {
      // No data yet
      return null;
    }

    MatrixFactorizer als = runFactorization(currentGeneration, RbyRow, RbyColumn);
    FastByIDMap<float[]> X = als.getX();
    FastByIDMap<float[]> Y = als.getY();

    if (currentGeneration != null) {
      FastIDSet recentlyActiveUsers;
      synchronized (this) {
        recentlyActiveUsers = this.recentlyActiveUsers.clone();
        this.recentlyActiveUsers.clear();
      }
      restoreRecentlyActive(recentlyActiveUsers,
                            currentGeneration.getX(),
                            currentGeneration.getXLock().readLock(),
                            X);

      FastIDSet recentlyActiveItems;
      synchronized (this) {
        recentlyActiveItems = this.recentlyActiveItems.clone();
        this.recentlyActiveItems.clear();
      }
      restoreRecentlyActive(recentlyActiveItems,
                            currentGeneration.getY(),
                            currentGeneration.getYLock().readLock(),
                            Y);

      if (currentGeneration.getKnownItemIDs() != null) {
        restoreRecentlyActive(recentlyActiveUsers,
                              currentGeneration.getKnownItemIDs(),
                              currentGeneration.getKnownItemLock().readLock(),
                              knownItemIDs);
      }
    }

    return new Generation(knownItemIDs, X, Y);
  }

  private static MatrixFactorizer runFactorization(Generation currentGeneration,
                                                   FastByIDMap<FastByIDFloatMap> rbyRow,
                                                   FastByIDMap<FastByIDFloatMap> rbyColumn) throws IOException {
    log.info("Building factorization...");

    String featuresString = System.getProperty("model.features");
    int features = featuresString == null ?
        MatrixFactorizer.DEFAULT_FEATURES : Integer.parseInt(featuresString);

    if (System.getProperty("model.iterations") != null) {
      log.warn("model.iterations system property is deprecated and ignored; " +
               "use model.als.iterations.convergenceThreshold");
    }

    String iterationsConvergenceString = 
        System.getProperty("model.als.iterations.convergenceThreshold",
                           Double.toString(AlternatingLeastSquares.DEFAULT_CONVERGENCE_THRESHOLD));
    String maxIterationsString = 
        System.getProperty("model.iterations.max", 
                           Integer.toString(AlternatingLeastSquares.DEFAULT_MAX_ITERATIONS));    
    MatrixFactorizer als = new AlternatingLeastSquares(rbyRow, 
                                                       rbyColumn, 
                                                       features, 
                                                       Double.parseDouble(iterationsConvergenceString),
                                                       Integer.parseInt(maxIterationsString));

    if (currentGeneration != null) {
      FastByIDMap<float[]> previousY = currentGeneration.getY();
      if (previousY != null) {
        FastByIDMap<float[]> previousYClone;
        Lock yLock = currentGeneration.getYLock().readLock();
        yLock.lock();
        try {
          previousYClone = new FastByIDMap<float[]>(previousY.size());
          for (FastByIDMap.MapEntry<float[]> entry : previousY.entrySet()) {
            previousYClone.put(entry.getKey(), entry.getValue().clone());
          }
        } finally {
          yLock.unlock();
        }
        als.setPreviousY(previousYClone);
      }
    }

    try {
      als.call();
      log.info("Factorization complete");
    } catch (ExecutionException ee) {
      throw new IOException(ee.getCause());
    } catch (InterruptedException ie) {
      log.warn("ALS computation was interrupted");
    }

    return als;
  }

  private static <V> void restoreRecentlyActive(FastIDSet recentlyActive,
                                                FastByIDMap<V> currentMatrix,
                                                Lock currentLock,
                                                FastByIDMap<V> newMatrix) {
    LongPrimitiveIterator it = recentlyActive.iterator();
    while (it.hasNext()) {
      long id = it.nextLong();
      if (!newMatrix.containsKey(id)) {
        currentLock.lock();
        V currentValue;
        try {
          currentValue = currentMatrix.get(id);
        } finally {
          currentLock.unlock();
        }
        if (currentValue != null) {
          newMatrix.put(id, currentValue);
        }
      }
    }
  }



}
