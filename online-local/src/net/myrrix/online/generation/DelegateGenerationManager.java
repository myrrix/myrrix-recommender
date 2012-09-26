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

import java.io.BufferedWriter;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.io.PatternFilenameFilter;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.common.iterator.FileLineIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.LangUtils;
import net.myrrix.common.NamedThreadFactory;
import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.online.factorizer.MatrixFactorizer;
import net.myrrix.online.factorizer.MatrixUtils;
import net.myrrix.online.factorizer.als.AlternatingLeastSquares;

/**
 * <p>Manages one generation of the underlying recommender model. Input is read from a local file system,
 * written to a local file system, and the intermediate model is stored on a local file system.</p>
 *
 * @author Sean Owen
 */
public final class DelegateGenerationManager implements GenerationManager {

  private static final Logger log = LoggerFactory.getLogger(DelegateGenerationManager.class);

  private static final int WRITES_BETWEEN_REBUILD = 10000000;

  /**
   * Values with absolute value less than this in the input are considered 0.
   * Values are generally assumed to be > 1, actually,
   * and usually not negative, though they need not be.
   */
  private static final float DEFAULT_ZERO_THRESHOLD = 0.0001f;

  private final String bucket;
  private final long instanceID;
  private final File inputDir;
  private final File modelFile;
  private final File appendFile;
  private Writer appender;
  private Generation currentGeneration;
  private int countdownToRebuild;
  private final ExecutorService refreshExecutor;
  private final Semaphore refreshSemaphore;

  public DelegateGenerationManager(File localInputDir) throws IOException {
    this(null, 0L, localInputDir, 0, 0);
  }

  /**
   * @param bucket not used in local mode; required for API compatibility internally
   * @param instanceID not used in local mode; required for API compatibility internally
   * @param localInputDir local work directory from which input is read,
   *  and to which additional input is written. The model file is stored here too. Input in CSV format can
   *  be placed in this directory at any time. The file name should end in ".csv" and the file should
   *  contain lines of the form "userID,itemID(,value)".
   * @param partition not used; required for API compatibility internally
   * @param numPartitions not used; required for API compatibility internally
   */
  public DelegateGenerationManager(String bucket,
                                   long instanceID,
                                   File localInputDir,
                                   int partition,
                                   int numPartitions) throws IOException {

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

    countdownToRebuild = WRITES_BETWEEN_REBUILD;
    refreshExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory(true, "LocalGenerationManager"));
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
  public long getInstanceID() {
    return instanceID;
  }

  @Override
  public void append(long userID, long itemID, float value) throws IOException {
    StringBuilder line = new StringBuilder(32);
    line.append(userID).append(',').append(itemID).append(',').append(value).append('\n');
    synchronized (this) {
      appender.append(line);
    }
    if (--countdownToRebuild == 0) {
      countdownToRebuild = WRITES_BETWEEN_REBUILD;
      refresh(null);
    }
  }

  @Override
  public void remove(long userID, long itemID) throws IOException {
    StringBuilder line = new StringBuilder(24);
    line.append(userID).append(',').append(itemID).append(",\n");
    synchronized (this) {
      appender.append(line);
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
      if (appendFile.length() == 0) {
        if (appendFile.exists() && !appendFile.delete()) {
          log.warn("Could not delete {}", appendFile);
        }
      } else {
        Files.move(appendFile, new File(inputDir, System.currentTimeMillis() + ".csv"));
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
              appender = new BufferedWriter(
                  new OutputStreamWriter(new FileOutputStream(appendFile, false), Charsets.UTF_8), 512);
            }

            Generation newGeneration = null;
            try {
              if (currentGeneration == null && modelFile.exists()) {
                newGeneration = readModel(modelFile);
              }
              if (newGeneration == null) {
                newGeneration = computeModel(inputDir);
                saveModel(newGeneration, modelFile);
              }
            } catch (OutOfMemoryError oome) {
              log.warn("Increase heap size with -Xmx, decrease new generation size with larger " +
                       "-XX:NewRatio value, and/or use -XX:+UseCompressedOops");
              throw oome;
            }
            currentGeneration = newGeneration;

          } catch (Throwable t) {
            log.warn("Unexpected exception while refreshing", t);

          } finally {
            refreshSemaphore.release();
            log.info("Refresh done");
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

  private Generation computeModel(File inputDir) throws IOException {

    log.info("Computing model from input in {}", inputDir);

    FastByIDMap<FastIDSet> knownItemIDs;
    if (Boolean.valueOf(System.getProperty(Generation.NO_KNOWN_ITEMS_KEY))) {
      knownItemIDs = null;
    } else {
      knownItemIDs = new FastByIDMap<FastIDSet>(10000, 1.25f);
    }
    Splitter comma = Splitter.on(',');
    FastByIDMap<FastByIDFloatMap> RbyRow = new FastByIDMap<FastByIDFloatMap>(10000, 1.25f);
    FastByIDMap<FastByIDFloatMap> RbyColumn = new FastByIDMap<FastByIDFloatMap>(10000, 1.25f);

    File[] inputFiles = inputDir.listFiles(new PatternFilenameFilter(".+\\.csv(\\.(zip|gz))?"));
    Arrays.sort(inputFiles, ByLastModifiedComparator.INSTANCE);

    int lines = 0;
    for (File inputFile : inputFiles) {
      log.info("Reading {}", inputFile);
      for (CharSequence line : new FileLineIterable(inputFile)) {
        Iterator<String> it = comma.split(line).iterator();
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
          MatrixUtils.remove(userID, itemID, RbyRow, RbyColumn);
        } else {
          MatrixUtils.addTo(userID, itemID, value, RbyRow, RbyColumn);
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

    removeSmall(RbyRow);
    removeSmall(RbyColumn);

    if (RbyRow.isEmpty() || RbyColumn.isEmpty()) {
      // No data yet
      return new Generation(null,
                            new FastByIDMap<float[]>(),
                            new FastByIDMap<float[]>());
    }

    log.info("Building factorization...");

    String featuresString = System.getProperty("model.features");
    int features = featuresString == null ?
        MatrixFactorizer.DEFAULT_FEATURES : Integer.parseInt(featuresString);
    String iterationsString = System.getProperty("model.iterations");
    int iterations = iterationsString == null ?
        MatrixFactorizer.DEFAULT_ITERATIONS : Integer.parseInt(iterationsString);

    MatrixFactorizer als = new AlternatingLeastSquares(RbyRow, RbyColumn, features, iterations);

    Generation currentGeneration = getCurrentGeneration();
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

    return new Generation(knownItemIDs, als.getX(), als.getY());
  }

  private static void removeSmall(FastByIDMap<FastByIDFloatMap> matrix) {
    for (FastByIDMap.MapEntry<FastByIDFloatMap> entry : matrix.entrySet()) {
      for (Iterator<FastByIDFloatMap.MapEntry> it = entry.getValue().entrySet().iterator(); it.hasNext();) {
        FastByIDFloatMap.MapEntry entry2 = it.next();
        if (Math.abs(entry2.getValue()) < DEFAULT_ZERO_THRESHOLD) {
          it.remove();
        }
      }
    }
  }

}
