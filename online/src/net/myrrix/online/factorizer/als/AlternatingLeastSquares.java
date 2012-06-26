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
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
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
  private RealMatrix XTX;
  private RealMatrix YTY;

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

  private static double getAlpha() {
    String alphaProperty = System.getProperty("model.als.alpha");
    return alphaProperty == null ? DEFAULT_ALPHA : LangUtils.parseDouble(alphaProperty);
  }

  private static double getLambda() {
    String lambdaProperty = System.getProperty("model.als.lambda");
    return lambdaProperty == null ? DEFAULT_LAMBDA: LangUtils.parseDouble(lambdaProperty);
  }

  @Override
  public Void call() throws ExecutionException, InterruptedException {

    X = new FastByIDMap<float[]>(RbyRow.size(), 1.2f);

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

    log.info("Starting {} iterations...", iterationsToRun);

    // This will be used to compute rows/columns in parallel during iteration
    ExecutorService executor =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
                                     new NamedThreadFactory(false, "AlternatingLeastSquares"));
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
    FastByIDMap<float[]> randomY = new FastByIDMap<float[]>(RbyColumn.size(), 1.2f);
    Random random = RandomUtils.getRandom();
    for (FastByIDMap<FastByIDFloatMap>.MapEntry entry : RbyColumn.entrySet()) {
      float[] Yrow = new float[features];
      for (int col = 0; col < features; col++) {
      // The expected length of a vector from origin to random point in the unit f-dimensional hypercube is
      // sqrt(f/3). We've picked a vector to a point in the "half" hypercube (bounds at 0.5 not 1). Expected
      // length is sqrt(f/12). We expected actual feature vectors to converge to unit vectors. So start off
      // by making the expected random vector length 1.
      double normalization = Math.sqrt(features / 12.0);
        Yrow[col] = (float) ((random.nextDouble() - 0.5) / normalization);
      }
      randomY.put(entry.getKey(), Yrow);
    }
    return randomY;
  }

  /**
   * @param M tall, skinny matrix (sparse rows, dense columns)
   * @param D sparse diagonal matrix
   * @return MT * D * M
   */
  private RealMatrix multiplySparseMiddleDiagonal(FastByIDMap<float[]> M, FastByIDFloatMap D) {
    double alpha = getAlpha();
    RealMatrix result = new OpenMapRealMatrix(features, features);
    for (int row = 0; row < features; row++) {
      for (int col = 0; col < features; col++) {
        double total = 0.0;
        for (FastByIDFloatMap.MapEntry entry : D.entrySet()) {
          float[] leftVec = M.get(entry.getKey());
          total += alpha * entry.getValue() * leftVec[row] * leftVec[col];
        }
        result.setEntry(row, col, total);
      }
    }
    return result;
  }

  /**
   * Runs one iteration to compute X from Y.
   */
  private void iterateXFromY(ExecutorService executor) throws ExecutionException, InterruptedException {

    YTY = MatrixUtils.transposeTimesSelf(Y);
    Collection<Future<?>> futures = Lists.newArrayList();

    List<Pair<Long,FastByIDFloatMap>> workUnit = Lists.newArrayListWithCapacity(WORK_UNIT_SIZE);
    for (FastByIDMap<FastByIDFloatMap>.MapEntry entry : RbyRow.entrySet()) {
      workUnit.add(new Pair<Long,FastByIDFloatMap>(entry.getKey(), entry.getValue()));
      if (workUnit.size() == WORK_UNIT_SIZE) {
        futures.add(executor.submit(new YWorker(workUnit)));
        workUnit = Lists.newArrayListWithCapacity(WORK_UNIT_SIZE);
      }
    }
    if (!workUnit.isEmpty()) {
      futures.add(executor.submit(new YWorker(workUnit)));
    }

    int count = 0;
    int total = 0;
    for (Future<?> f : futures) {
      f.get();
      count += WORK_UNIT_SIZE;
      if (count >= 1000) {
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
    
    XTX = MatrixUtils.transposeTimesSelf(X);
    Collection<Future<?>> futures = Lists.newArrayList();

    List<Pair<Long,FastByIDFloatMap>> workUnit = Lists.newArrayListWithCapacity(WORK_UNIT_SIZE);
    for (FastByIDMap<FastByIDFloatMap>.MapEntry entry : RbyColumn.entrySet()) {
      workUnit.add(new Pair<Long,FastByIDFloatMap>(entry.getKey(), entry.getValue()));
      if (workUnit.size() == WORK_UNIT_SIZE) {
        futures.add(executor.submit(new XWorker(workUnit)));
        workUnit = Lists.newArrayListWithCapacity(WORK_UNIT_SIZE);
      }
    }
    if (!workUnit.isEmpty()) {
      futures.add(executor.submit(new XWorker(workUnit)));
    }

    int count = 0;
    int total = 0;
    for (Future<?> f : futures) {
      f.get();
      count += WORK_UNIT_SIZE;
      if (count >= 1000) {
        total += count;
        log.info("{} Y rows computed ({}MB heap)", total, new JVMEnvironment().getUsedMemoryMB());
        count = 0;
      }
    }
  }

  private final class YWorker implements Runnable {

    private final List<Pair<Long,FastByIDFloatMap>> workUnit;

    private YWorker(List<Pair<Long,FastByIDFloatMap>> workUnit) {
      this.workUnit = workUnit;
    }

    @Override
    public void run() {
      double alpha = getAlpha();
      double lambda = getLambda() * alpha;
      FastByIDMap<float[]> WuYT = new FastByIDMap<float[]>(10000, 1.2f);
      for (Pair<Long,FastByIDFloatMap> work : workUnit) {
        FastByIDFloatMap ru = work.getSecond();

        RealMatrix YTCuY = multiplySparseMiddleDiagonal(Y, ru);
        YTCuY = YTCuY.add(YTY);

        float cuFactor = (float) (ru.size() * lambda);
        for (int x = 0; x < features; x++) {
          YTCuY.addToEntry(x, x, cuFactor);
        }
        RealMatrix Wu = new LUDecomposition(YTCuY).getSolver().getInverse();

        FastByIDFloatMap Cupu = new FastByIDFloatMap();
        for (FastByIDFloatMap.MapEntry ruEntry : ru.entrySet()) {
          Cupu.put(ruEntry.getKey(), (float) (1.0 + alpha * ruEntry.getValue()));
        }

        MatrixUtils.multiply(Wu, Y, WuYT);
        float[] xu = new float[features];
        for (int row = 0; row < features; row++) {
          double total = 0.0;
          for (FastByIDFloatMap.MapEntry cupuEntry : Cupu.entrySet()) {
            total += cupuEntry.getValue() * WuYT.get(cupuEntry.getKey())[row];
          }
          xu[row] = (float) total;
        }

        synchronized (X) {
          X.put(work.getFirst(), xu);
        }
      }
    }
  }

  private final class XWorker implements Runnable {

    private final List<Pair<Long,FastByIDFloatMap>> workUnit;

    private XWorker(List<Pair<Long,FastByIDFloatMap>> workUnit) {
      this.workUnit = workUnit;
    }

    @Override
    public void run() {
      double alpha = getAlpha();
      double lambda = getLambda() * alpha;
      FastByIDMap<float[]> WiXT = new FastByIDMap<float[]>(10000, 1.2f);
      for (Pair<Long,FastByIDFloatMap> work : workUnit) {
        FastByIDFloatMap ri = work.getSecond();

        RealMatrix XTCiX = multiplySparseMiddleDiagonal(X, ri);
        XTCiX = XTCiX.add(XTX);

        float ciFactor = (float) (ri.size() * lambda);
        for (int x = 0; x < features; x++) {
          XTCiX.addToEntry(x, x, ciFactor);
        }
        RealMatrix Wi = new LUDecomposition(XTCiX).getSolver().getInverse();

        FastByIDFloatMap Cipi = new FastByIDFloatMap();
        for (FastByIDFloatMap.MapEntry riEntry : ri.entrySet()) {
          Cipi.put(riEntry.getKey(), (float) (1.0 + alpha * riEntry.getValue()));
        }

        MatrixUtils.multiply(Wi, X, WiXT);
        float[] yi = new float[features];
        for (int row = 0; row < features; row++) {
          double total = 0.0;
          for (FastByIDFloatMap.MapEntry cipiEntry : Cipi.entrySet()) {
            total += cipiEntry.getValue() * WiXT.get(cipiEntry.getKey())[row];
          }
          yi[row] = (float) total;
        }
        synchronized (Y) {
          Y.put(work.getFirst(), yi);
        }
      }
    }
  }
}
