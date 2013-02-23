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

package net.myrrix.online.factorizer.als;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.WeightedRunningAverage;
import org.apache.mahout.common.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.ExecutorUtils;
import net.myrrix.common.math.SimpleVectorMath;
import net.myrrix.common.random.RandomManager;
import net.myrrix.common.random.RandomUtils;
import net.myrrix.common.stats.JVMEnvironment;
import net.myrrix.common.LangUtils;
import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.math.MatrixUtils;
import net.myrrix.online.factorizer.MatrixFactorizer;

/**
 * <p>Implements the Alternating Least Squares algorithm described in
 * <a href="http://www2.research.att.com/~yifanhu/PUB/cf.pdf">"Collaborative Filtering for Implicit Feedback Datasets"</a>
 * by Yifan Hu, Yehuda Koren, and Chris Volinsky.</p>
 *
 * <p>This implementation varies in some small details; it does not use the same mechanism for explaining ratings
 * for example and seeds the initial Y differently.</p>
 *
 * <p>Note that in this implementation, matrices are sparse and are implemented with a {@link FastByIDMap} of
 * {@link FastByIDFloatMap} so as to be able to use {@code long} keys. In many cases, a tall, skinny matrix is
 * needed (sparse rows, dense columns). This is represented with {@link FastByIDMap} of {@code float[]}.</p>
 *
 * @author Sean Owen
 */
public final class AlternatingLeastSquares implements MatrixFactorizer {

  private static final Logger log = LoggerFactory.getLogger(AlternatingLeastSquares.class);

  /** Default alpha from the ALS algorithm. Shouldn't really need to change it. */
  public static final double DEFAULT_ALPHA = 40.0;
  /** Default lambda factor; this is multiplied by alpha. */
  public static final double DEFAULT_LAMBDA = 0.1;
  public static final double DEFAULT_CONVERGENCE_THRESHOLD = 0.001;
  public static final int DEFAULT_MAX_ITERATIONS = 30;

  private static final double LN_E_MINUS_1 = Math.log(FastMath.E - 1.0);

  private static final int WORK_UNIT_SIZE = 100;
  private static final int NUM_USER_ITEMS_TO_TEST_CONVERGENCE = 100;

  private final FastByIDMap<FastByIDFloatMap> RbyRow;
  private final FastByIDMap<FastByIDFloatMap> RbyColumn;
  private final int features;
  private final double estimateErrorConvergenceThreshold;
  private final int maxIterations;
  private FastByIDMap<float[]> X;
  private FastByIDMap<float[]> Y;
  private FastByIDMap<float[]> previousY;

  /**
   * Uses default number of feature and convergence threshold.
   *
   * @param RbyRow the input R matrix, indexed by row
   * @param RbyColumn the input R matrix, indexed by column
   */
  public AlternatingLeastSquares(FastByIDMap<FastByIDFloatMap> RbyRow,
                                 FastByIDMap<FastByIDFloatMap> RbyColumn) {
    this(RbyRow, RbyColumn, DEFAULT_FEATURES, DEFAULT_CONVERGENCE_THRESHOLD, DEFAULT_MAX_ITERATIONS);
  }

  /**
   * @param RbyRow the input R matrix, indexed by row
   * @param RbyColumn the input R matrix, indexed by column
   * @param features number of features, must be positive
   */
  public AlternatingLeastSquares(FastByIDMap<FastByIDFloatMap> RbyRow,
                                 FastByIDMap<FastByIDFloatMap> RbyColumn,
                                 int features) {
    this(RbyRow, RbyColumn, features, DEFAULT_CONVERGENCE_THRESHOLD, DEFAULT_MAX_ITERATIONS);
  }

  /**
   * @param RbyRow the input R matrix, indexed by row
   * @param RbyColumn the input R matrix, indexed by column
   * @param features number of features, must be positive
   * @param estimateErrorConvergenceThreshold when the average absolute difference in estimated user-item
   *   scores falls below this threshold between iterations, iterations will stop
   * @param maxIterations caps the number of iterations run. If non-positive, there is no cap.
   */
  public AlternatingLeastSquares(FastByIDMap<FastByIDFloatMap> RbyRow,
                                 FastByIDMap<FastByIDFloatMap> RbyColumn,
                                 int features,
                                 double estimateErrorConvergenceThreshold,
                                 int maxIterations) {
    Preconditions.checkNotNull(RbyRow);
    Preconditions.checkNotNull(RbyColumn);
    Preconditions.checkArgument(features > 0, "features must be positive: %s", features);
    Preconditions.checkArgument(estimateErrorConvergenceThreshold > 0.0 && estimateErrorConvergenceThreshold < 1.0,
                                "threshold must be in (0,1): %s", estimateErrorConvergenceThreshold);
    this.RbyRow = RbyRow;
    this.RbyColumn = RbyColumn;
    this.features = features;
    this.estimateErrorConvergenceThreshold = estimateErrorConvergenceThreshold;
    this.maxIterations = maxIterations;
  }

  @Override
  public FastByIDMap<float[]> getX() {
    return X;
  }

  @Override
  public FastByIDMap<float[]> getY() {
    return Y;
  }

  /**
   * Does nothing.
   */
  @Override
  public void setPreviousX(FastByIDMap<float[]> previousX) {
    // do nothing
  }

  /**
   * Sets the initial state of Y used in the computation, typically the Y from a previous
   * computation. Call before {@link #call()}.
   */
  @Override
  public void setPreviousY(FastByIDMap<float[]> previousY) {
    this.previousY = previousY;
  }

  @Override
  public Void call() throws ExecutionException, InterruptedException {

    X = new FastByIDMap<float[]>(RbyRow.size(), 1.25f);

    if (previousY == null ||
        previousY.isEmpty() ||
        previousY.entrySet().iterator().next().getValue().length != features) {
      log.info("Starting from random Y matrix");
      Y = constructInitialY(null);
    } else {
      log.info("Starting from previous generation's Y matrix");
      Y = constructInitialY(previousY);
    }

    // This will be used to compute rows/columns in parallel during iteration

    String threadsString = System.getProperty("model.threads");
    int numThreads =
        threadsString == null ? Runtime.getRuntime().availableProcessors() : Integer.parseInt(threadsString);
    ExecutorService executor =
        Executors.newFixedThreadPool(numThreads,
                                     new ThreadFactoryBuilder().setNameFormat("ALS-%d").setDaemon(true).build());

    log.info("Iterating using {} threads", numThreads);

    RandomGenerator random = RandomManager.getRandom();
    long[] testUserIDs = RandomUtils.chooseAboutNFromStream(NUM_USER_ITEMS_TO_TEST_CONVERGENCE, 
                                                            RbyRow.keySetIterator(), 
                                                            RbyRow.size(), 
                                                            random);
    long[] testItemIDs = RandomUtils.chooseAboutNFromStream(NUM_USER_ITEMS_TO_TEST_CONVERGENCE, 
                                                            RbyColumn.keySetIterator(), 
                                                            RbyColumn.size(), 
                                                            random);
    double[][] estimates = new double[testUserIDs.length][testItemIDs.length];
    if (!X.isEmpty()) {
      for (int i = 0; i < testUserIDs.length; i++) {
        for (int j = 0; j < testItemIDs.length; j++) {
          estimates[i][j] = SimpleVectorMath.dot(X.get(testUserIDs[i]), Y.get(testItemIDs[j])); 
        }
      }
    }
    // Otherwise X is empty because it's the first ever iteration. Estimates can be left at initial 0 value

    try {
      int iterationNumber = 0;
      while (true) {
        iterateXFromY(executor);
        iterateYFromX(executor);
        WeightedRunningAverage averageAbsoluteEstimateDiff = new WeightedRunningAverage();
        for (int i = 0; i < testUserIDs.length; i++) {
          for (int j = 0; j < testItemIDs.length; j++) {
            double newValue = SimpleVectorMath.dot(X.get(testUserIDs[i]), Y.get(testItemIDs[j]));            
            double oldValue = estimates[i][j];
            estimates[i][j] = newValue;
            averageAbsoluteEstimateDiff.addDatum(FastMath.abs(newValue - oldValue), FastMath.max(0.0, newValue));
          }
        }
      
        iterationNumber++;
        log.info("Finished iteration {}", iterationNumber);
        if (maxIterations > 0 && iterationNumber >= maxIterations) {
          log.info("Reached iteration limit");
          break;
        }
        log.info("Avg absolute difference in estimate vs prior iteration: {}", averageAbsoluteEstimateDiff);
        double convergenceValue = averageAbsoluteEstimateDiff.getAverage();
        if (!LangUtils.isFinite(convergenceValue)) {
          log.warn("Invalid convergence value, aborting iteration! {}", convergenceValue);
          break;
        }
        if (convergenceValue < estimateErrorConvergenceThreshold) {
          log.info("Converged");          
          break;
        }
      }
    } finally {
      ExecutorUtils.shutdownNowAndAwait(executor);
    }
    return null;
  }

  private FastByIDMap<float[]> constructInitialY(FastByIDMap<float[]> previousY) {
    FastByIDMap<float[]> randomY = previousY == null ? new FastByIDMap<float[]>(RbyColumn.size(), 1.25f) : previousY;
    RandomGenerator random = RandomManager.getRandom();
    List<float[]> recentVectors = Lists.newArrayList();
    for (FastByIDMap.MapEntry<float[]> entry : randomY.entrySet()) {
      recentVectors.add(entry.getValue());
    }
    LongPrimitiveIterator it = RbyColumn.keySetIterator();
    long count = 0;
    while (it.hasNext()) {
      long id = it.nextLong();
      if (!randomY.containsKey(id)) {
        float[] vector = RandomUtils.randomUnitVectorFarFrom(features, recentVectors, random);
        randomY.put(id, vector);
        recentVectors.add(vector);
      }
      if (++count % 100000 == 0) {
        log.info("Computed {} initial Y rows", count);
      }
    }
    log.info("Constructed initial Y");
    return randomY;
  }

  /**
   * Runs one iteration to compute X from Y.
   */
  private void iterateXFromY(ExecutorService executor) throws ExecutionException, InterruptedException {

    RealMatrix YTY = MatrixUtils.transposeTimesSelf(Y);
    Collection<Future<?>> futures = Lists.newArrayList();

    List<Pair<Long,FastByIDFloatMap>> workUnit = Lists.newArrayListWithCapacity(WORK_UNIT_SIZE);
    for (FastByIDMap.MapEntry<FastByIDFloatMap> entry : RbyRow.entrySet()) {
      workUnit.add(new Pair<Long,FastByIDFloatMap>(entry.getKey(), entry.getValue()));
      if (workUnit.size() == WORK_UNIT_SIZE) {
        futures.add(executor.submit(new Worker(features, Y, YTY, X, workUnit)));
        workUnit = Lists.newArrayListWithCapacity(WORK_UNIT_SIZE);
      }
    }
    if (!workUnit.isEmpty()) {
      futures.add(executor.submit(new Worker(features, Y, YTY, X, workUnit)));
    }

    int count = 0;
    int total = 0;
    for (Future<?> f : futures) {
      f.get();
      count += WORK_UNIT_SIZE;
      if (count >= 100000) {
        total += count;
        JVMEnvironment env = new JVMEnvironment();
        log.info("{} X rows computed ({}MB heap)", total, env.getUsedMemoryMB());
        if (env.getPercentUsedMemory() > 95) {
          log.warn("Memory is low. Increase heap size with -Xmx, decrease new generation size with larger " +
                   "-XX:NewRatio value, and/or use -XX:+UseCompressedOops");
        }
        count = 0;
      }
    }
  }

  /**
   * Runs one iteration to compute Y from X.
   */
  private void iterateYFromX(ExecutorService executor) throws ExecutionException, InterruptedException {

    RealMatrix XTX = MatrixUtils.transposeTimesSelf(X);
    Collection<Future<?>> futures = Lists.newArrayList();

    List<Pair<Long,FastByIDFloatMap>> workUnit = Lists.newArrayListWithCapacity(WORK_UNIT_SIZE);
    for (FastByIDMap.MapEntry<FastByIDFloatMap> entry : RbyColumn.entrySet()) {
      workUnit.add(new Pair<Long,FastByIDFloatMap>(entry.getKey(), entry.getValue()));
      if (workUnit.size() == WORK_UNIT_SIZE) {
        futures.add(executor.submit(new Worker(features, X, XTX, Y, workUnit)));
        workUnit = Lists.newArrayListWithCapacity(WORK_UNIT_SIZE);
      }
    }
    if (!workUnit.isEmpty()) {
      futures.add(executor.submit(new Worker(features, X, XTX, Y, workUnit)));
    }

    int count = 0;
    int total = 0;
    for (Future<?> f : futures) {
      f.get();
      count += WORK_UNIT_SIZE;
      if (count >= 10000) {
        total += count;
        log.info("{} Y rows computed ({}MB heap)", total, new JVMEnvironment().getUsedMemoryMB());
        count = 0;
      }
    }
  }

  private static final class Worker implements Runnable {

    private final int features;
    private final FastByIDMap<float[]> Y;
    private final RealMatrix YTY;
    private final FastByIDMap<float[]> X;
    private final List<Pair<Long,FastByIDFloatMap>> workUnit;

    private Worker(int features,
                   FastByIDMap<float[]> Y,
                   RealMatrix YTY,
                   FastByIDMap<float[]> X,
                   List<Pair<Long,FastByIDFloatMap>> workUnit) {
      this.features = features;
      this.Y = Y;
      this.YTY = YTY;
      this.X = X;
      this.workUnit = workUnit;
    }

    @Override
    public void run() {
      double alpha = getAlpha();
      double lambda = getLambda() * alpha;
      int features = this.features;
      // Each worker has a batch of rows to compute:
      for (Pair<Long,FastByIDFloatMap> work : workUnit) {

        // Row (column) in original R matrix containing total association value. For simplicity we will
        // talk about users and rows only in the comments and variables. It's symmetric for columns / items.
        // This is Ru:
        FastByIDFloatMap ru = work.getSecond();

        RealMatrix Wu = YTY.copy();
        double[][] WuData = MatrixUtils.accessMatrixDataDirectly(Wu);
        double[] YTCupu = new double[features];

        for (FastByIDFloatMap.MapEntry entry : ru.entrySet()) {

          // Normally, cu = 1 + alpha * xu. To provide some reasonable accommodation for negative values,
          // we use a hinge-loss-style function: cu = ln(1 + e^(alpha * (xu + s))
          // where s is chosen so that cu = 1 when xu = 0, which is the case in the original formula and
          // is necessary to preserve the rest of the math. s = ln(e-1)/alpha.
          // cu decays to 0 as xu goes from 1 to -infinity in the new formula, while the original would
          // become negative, which is a problem. Above 1 it is nearly linear like the original, just slightly
          // offset.

          double xu = entry.getValue();
          // Actually treat this as a two part function to avoid overflow:
          double cu = xu < 0.0 ? Math.log1p(FastMath.exp(alpha * (xu + LN_E_MINUS_1 / alpha))) : 1.0 + alpha * xu;

          float[] vector = Y.get(entry.getKey());
          if (vector == null) {
            log.warn("No vector for {}. This should not happen. Continuing...", entry.getKey());
            continue;
          }

          // Wu and YTCupu
          for (int row = 0; row < features; row++) {
            float vectorAtRow = vector[row];
            double rowValue = vectorAtRow * (cu - 1.0);
            for (int col = 0; col < features; col++) {
              WuData[row][col] += rowValue * vector[col];
              //Wu.addToEntry(row, col, rowValue * vector[col]);
            }
            YTCupu[row] += vectorAtRow * cu;
          }

        }

        double lambdaTimesCount = lambda * ru.size();
        for (int x = 0; x < features; x++) {
          WuData[x][x] += lambdaTimesCount;          
          //Wu.addToEntry(x, x, lambdaTimesCount);
        }
        Wu = MatrixUtils.invert(Wu);

        float[] xu = new float[features];
        for (int row = 0; row < features; row++) {
          double[] wuRow = Wu.getRow(row);
          double total = 0.0;
          for (int col = 0; col < features; col++) {
            total += wuRow[col] * YTCupu[col];
          }
          xu[row] = (float) total;
        }

        // Store result:
        synchronized (X) {
          X.put(work.getFirst(), xu);
        }

        // Process is identical for computing Y from X. Swap X in for Y, Y for X, i for u, etc.
      }
    }

    private static double getAlpha() {
      String alphaProperty = System.getProperty("model.als.alpha");
      return alphaProperty == null ? DEFAULT_ALPHA : LangUtils.parseDouble(alphaProperty);
    }

    private static double getLambda() {
      String lambdaProperty = System.getProperty("model.als.lambda");
      return lambdaProperty == null ? DEFAULT_LAMBDA : LangUtils.parseDouble(lambdaProperty);
    }

  }

}
