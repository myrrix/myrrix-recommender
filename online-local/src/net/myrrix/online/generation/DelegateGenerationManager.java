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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.zip.GZIPOutputStream;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.io.PatternFilenameFilter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.common.iterator.FileLineIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.LangUtils;
import net.myrrix.common.ReloadingReference;
import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.math.MatrixUtils;
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

  private static final Splitter COMMA = Splitter.on(',');

  private static final int WRITES_BETWEEN_REBUILD;
  static {
    WRITES_BETWEEN_REBUILD =
          Integer.parseInt(System.getProperty("model.local.writesBetweenRebuild", "100000"));
    Preconditions.checkArgument(WRITES_BETWEEN_REBUILD > 0,
                                "Bad model.local.writesBetweenRebuild: %s", WRITES_BETWEEN_REBUILD);
  }

  /**
   * Values with absolute value less than this in the input are considered 0.
   * Values are generally assumed to be > 1, actually,
   * and usually not negative, though they need not be.
   */
  private static final float ZERO_THRESHOLD =
      Float.parseFloat(System.getProperty("model.decay.zeroThreshold", "0.0001"));

  private final String bucket;
  private final String instanceID;
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
    this(null, null, localInputDir, 0, null);
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
   */
  public DelegateGenerationManager(String bucket,
                                   String instanceID,
                                   File localInputDir,
                                   int partition,
                                   ReloadingReference<List<?>> allPartitions) throws IOException {

    this.bucket = bucket;
    this.instanceID = instanceID;

    // Arguments above are unused but here for compatibility with other DelegateGenerationManager

    log.info("Using local computation, and data in {}", localInputDir);
    inputDir = localInputDir;
    if (!inputDir.exists() || !inputDir.isDirectory()) {
      throw new FileNotFoundException(inputDir.toString());
    }

    modelFile = new File(inputDir, "model.bin");
    appendFile = new File(inputDir, "append.bin");

    recentlyActiveUsers = new FastIDSet();
    recentlyActiveItems = new FastIDSet();

    countdownToRebuild = WRITES_BETWEEN_REBUILD;
    refreshExecutor = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("LocalGenerationManager-%d").build());
    refreshSemaphore = new Semaphore(1);
    refresh(null);
  }

  /**
   * Not used.
   */
  @Override
  public String getBucket() {
    return bucket;
  }

  /**
   * Not used.
   */
  @Override
  public String getInstanceID() {
    return instanceID;
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
      refresh(null);
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
    refreshExecutor.shutdown();
    closeAppender();
  }

  @Override
  public void refresh(Collection<Refreshable> alreadyRefreshed) {
    if (refreshSemaphore.tryAcquire()) {
      log.info("Starting new refresh");
      refreshExecutor.submit(new Callable<Void>() {
        @Override
        public Void call() {
          try {

            synchronized (DelegateGenerationManager.this) {
              closeAppender();
              // A small buffer is needed here, but GZIPOutputStream already provides a substantial native buffer
              appender = new OutputStreamWriter(
                  new GZIPOutputStream(new FileOutputStream(appendFile, false)), Charsets.UTF_8);
            }

            try {
              Generation newGeneration = null;
              if (currentGeneration == null && modelFile.exists()) {
                newGeneration = readModel(modelFile);
              }
              if (newGeneration == null) {
                newGeneration = computeModel(inputDir, currentGeneration);
                saveModel(newGeneration, modelFile);
              }
              log.info("New generation has {} users, {} items",
                       newGeneration.getNumUsers(), newGeneration.getNumItems());
              currentGeneration = newGeneration;
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
    ObjectInputStream in = new ObjectInputStream(new FileInputStream(modelFile));
    try {
      GenerationSerializer serializer = (GenerationSerializer) in.readObject();
      return serializer.getGeneration();
    } catch (ObjectStreamException ose) {
      log.warn("Model file was not readable, rebuilding ({})", ose);
      return null;
    } catch (ClassNotFoundException cnfe) {
      throw new IOException(cnfe);
    } finally {
      Closeables.closeQuietly(in);
    }
  }

  /**
   * Saves a model (as a {@link Generation} to a given file.
   *
   * @see #readModel(File)
   */
  private static void saveModel(Generation generation, File modelFile) throws IOException {

    File newModelFile = File.createTempFile(DelegateGenerationManager.class.getSimpleName(), ".bin");
    log.info("Writing model to {}", newModelFile);

    ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(newModelFile));
    try {
      out.writeObject(new GenerationSerializer(generation));
    } catch (IOException ioe) {
      if (newModelFile.exists() && !newModelFile.delete()) {
        log.warn("Could not delete {}", newModelFile);
      }
      throw ioe;
    } finally {
      out.close(); // Want to know of output stream close failed -- maybe failed to write
    }

    log.info("Done, moving into place at {}", modelFile);
    modelFile.delete();
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

    readInputFiles(knownItemIDs, RbyRow, RbyColumn, inputDir);

    removeSmall(RbyRow);
    removeSmall(RbyColumn);

    if (RbyRow.isEmpty() || RbyColumn.isEmpty()) {
      // No data yet
      return new Generation(null,
                            new FastByIDMap<float[]>(),
                            new FastByIDMap<float[]>());
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

      restoreRecentlyActive(recentlyActiveUsers,
                            currentGeneration.getKnownItemIDs(),
                            currentGeneration.getKnownItemLock().readLock(),
                            knownItemIDs);
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
    String iterationsString = System.getProperty("model.iterations");
    int iterations = iterationsString == null ?
        MatrixFactorizer.DEFAULT_ITERATIONS : Integer.parseInt(iterationsString);

    MatrixFactorizer als = new AlternatingLeastSquares(rbyRow, rbyColumn, features, iterations);

    if (currentGeneration != null) {
      FastByIDMap<float[]> previousY = currentGeneration.getY();
      if (previousY != null) {
        als.setPreviousY(previousY);
      }
    }

    try {
      als.call();
    } catch (ExecutionException ee) {
      throw new IOException(ee.getCause());
    } catch (InterruptedException ie) {
      throw new IOException(ie);
    }

    log.info("Factorization complete");
    return als;
  }

  private static void readInputFiles(FastByIDMap<FastIDSet> knownItemIDs,
                                     FastByIDMap<FastByIDFloatMap> rbyRow,
                                     FastByIDMap<FastByIDFloatMap> rbyColumn,
                                     File inputDir) throws IOException {

    File[] inputFiles = inputDir.listFiles(new PatternFilenameFilter(".+\\.csv(\\.(zip|gz))?"));
    if (inputFiles == null) {
      log.info("No input files in {}", inputDir);
      return;
    }
    Arrays.sort(inputFiles, ByLastModifiedComparator.INSTANCE);

    int lines = 0;
    for (File inputFile : inputFiles) {
      log.info("Reading {}", inputFile);
      for (CharSequence line : new FileLineIterable(inputFile)) {
        Iterator<String> it = COMMA.split(line).iterator();
        long userID = Long.parseLong(it.next());
        long itemID = Long.parseLong(it.next());
        float value;
        if (it.hasNext()) {
          String valueToken = it.next().trim();
          value = valueToken.isEmpty() ? Float.NaN : LangUtils.parseFloat(valueToken);
        } else {
          value = 1.0f;
        }

        if (Float.isNaN(value)) {
          // Remove, not set
          MatrixUtils.remove(userID, itemID, rbyRow, rbyColumn);
        } else {
          MatrixUtils.addTo(userID, itemID, value, rbyRow, rbyColumn);
        }

        if (knownItemIDs != null) {
          FastIDSet itemIDs = knownItemIDs.get(userID);
          if (Float.isNaN(value)) {
            // Remove, not set
            if (itemIDs != null) {
              itemIDs.remove(itemID);
              if (itemIDs.isEmpty()) {
                knownItemIDs.remove(userID);
              }
            }
          } else {
            if (itemIDs == null) {
              itemIDs = new FastIDSet();
              knownItemIDs.put(userID, itemIDs);
            }
            itemIDs.add(itemID);
          }
        }

        if (++lines % 1000000 == 0) {
          log.info("Finished {} lines", lines);
        }
      }
    }
  }

  private static void removeSmall(FastByIDMap<FastByIDFloatMap> matrix) {
    for (FastByIDMap.MapEntry<FastByIDFloatMap> entry : matrix.entrySet()) {
      for (Iterator<FastByIDFloatMap.MapEntry> it = entry.getValue().entrySet().iterator(); it.hasNext();) {
        FastByIDFloatMap.MapEntry entry2 = it.next();
        if (FastMath.abs(entry2.getValue()) < ZERO_THRESHOLD) {
          it.remove();
        }
      }
    }
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
        try {
          newMatrix.put(id, currentMatrix.get(id));
        } finally {
          currentLock.unlock();
        }
      }
    }
  }



}
