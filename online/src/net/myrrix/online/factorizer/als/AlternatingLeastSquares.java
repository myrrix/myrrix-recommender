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
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.JVMEnvironment;
import net.myrrix.common.LangUtils;
import net.myrrix.common.NamedThreadFactory;
import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.online.factorizer.MatrixFactorizer;
import net.myrrix.online.factorizer.MatrixUtils;

/**
 * <p>Implements the Alternating Least Squares algorithm described in
 * <a href="www2.research.att.com/~yifanhu/PUB/cf.pdf">"Collaborative Filtering for Implicit Feedback Datasets"</a>
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
  public static final double DEFAULT_LAMBDA = 0.01;


  private static final int WORK_UNIT_SIZE = 100;

  private final FastByIDMap<FastByIDFloatMap> RbyRow;
  private final FastByIDMap<FastByIDFloatMap> RbyColumn;
  private final int features;
  private final int iterations;
  private FastByIDMap<float[]> X;
  private FastByIDMap<float[]> Y;
  private FastByIDMap<float[]> previousY;

  /**
   * Uses default number of feature and iterations.
   *
   * @param RbyRow the input R matrix, indexed by row
   * @param RbyColumn the input R matrix, indexed by column
   */
  public AlternatingLeastSquares(FastByIDMap<FastByIDFloatMap> RbyRow,
                                 FastByIDMap<FastByIDFloatMap> RbyColumn) {
    this(RbyRow, RbyColumn, DEFAULT_FEATURES, DEFAULT_ITERATIONS);
  }

  /**
   * @param RbyRow the input R matrix, indexed by row
   * @param RbyColumn the input R matrix, indexed by column
   * @param features number of features, must be positive
   * @param iterations number of iterations, must be positive
   */
  public AlternatingLeastSquares(FastByIDMap<FastByIDFloatMap> RbyRow,
                                 FastByIDMap<FastByIDFloatMap> RbyColumn,
                                 int features,
                                 int iterations) {
    Preconditions.checkArgument(features > 0);
    Preconditions.checkArgument(iterations > 0);
    this.RbyRow = RbyRow;
    this.RbyColumn = RbyColumn;
    this.features = features;
    this.iterations = iterations;

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

    int iterationsToRun;
    if (previousY == null ||
        previousY.isEmpty() ||
        previousY.entrySet().iterator().next().getValue().length != features) {
      log.info("Constructing initial Y and adding 1 iteration to compute it...");
      Y = constructInitialY();
      iterationsToRun = iterations + 1;
    } else {
      log.info("Starting from previous generation Y...");
      Y = previousY;
      iterationsToRun = iterations;
    }

    // This will be used to compute rows/columns in parallel during iteration

    String threadsString = System.getProperty("model.threads");
    int numThreads =
        threadsString == null ? Runtime.getRuntime().availableProcessors() : Integer.parseInt(threadsString);
    ExecutorService executor =
        Executors.newFixedThreadPool(numThreads, new NamedThreadFactory(false, "AlternatingLeastSquares"));

    log.info("Starting {} iterations with {} threads", iterationsToRun, numThreads);

    try {
      for (int i = 0; i < iterationsToRun; i++) {
        iterateXFromY(executor);
        iterateYFromX(executor);
        log.info("Finished iteration {} of {}", i + 1, iterationsToRun);
      }
    } finally {
      executor.shutdown();
    }
    return null;
  }

  private FastByIDMap<float[]> constructInitialY() {
    FastByIDMap<float[]> randomY = new FastByIDMap<float[]>(RbyColumn.size(), 1.25f);
    Random random = RandomUtils.getRandom();
    // The expected length of a vector from origin to random point in the unit f-dimensional hypercube is
    // sqrt(f/3). We've picked a vector to a point in the "half" hypercube (bounds at 0.5 not 1). Expected
    // length is sqrt(f/12). We expected actual feature vectors to converge to unit vectors. So start off
    // by making the expected random vector length 1.
    int features = this.features;
    double normalization = Math.sqrt(features / 12.0);
    for (FastByIDMap<FastByIDFloatMap>.MapEntry entry : RbyColumn.entrySet()) {
      float[] Yrow = new float[features];
      for (int col = 0; col < features; col++) {
        Yrow[col] = (float) ((random.nextDouble() - 0.5) / normalization);
      }
      randomY.put(entry.getKey(), Yrow);
    }
    return randomY;
  }

  /**
   * Runs one iteration to compute X from Y.
   */
  private void iterateXFromY(ExecutorService executor) throws ExecutionException, InterruptedException {

    RealMatrix YTY = MatrixUtils.transposeTimesSelf(Y);
    Collection<Future<?>> futures = Lists.newArrayList();

    List<Pair<Long,FastByIDFloatMap>> workUnit = Lists.newArrayListWithCapacity(WORK_UNIT_SIZE);
    for (FastByIDMap<FastByIDFloatMap>.MapEntry entry : RbyRow.entrySet()) {
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
      if (count >= 10000) {
        total += count;
        log.info("{} X rows computed ({}MB heap)", total, new JVMEnvironment().getUsedMemoryMB());
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
    for (FastByIDMap<FastByIDFloatMap>.MapEntry entry : RbyColumn.entrySet()) {
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

        RealMatrix Wu = new Array2DRowRealMatrix(features, features);
        double[] YTCupu = new double[features];

        for (FastByIDFloatMap.MapEntry entry : ru.entrySet()) {

          double alphaTimesValue = alpha * entry.getValue();
          float[] vector = Y.get(entry.getKey());

          // Wu

          for (int row = 0; row < features; row++) {
            double rowValue = vector[row] * alphaTimesValue;
            for (int col = 0; col < features; col++) {
              Wu.addToEntry(row, col, rowValue * vector[col]);
            }
          }

          // YTCupu

          double onePlusAlphaCount = 1.0 + alphaTimesValue;
          for (int i = 0; i < features; i++) {
            YTCupu[i] += vector[i] * onePlusAlphaCount;
          }

        }

        Wu = Wu.add(YTY);
        double lambdaTimesCount = lambda * ru.size();
        for (int x = 0; x < features; x++) {
          Wu.addToEntry(x, x, lambdaTimesCount);
        }
        Wu = new LUDecomposition(Wu).getSolver().getInverse();

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
