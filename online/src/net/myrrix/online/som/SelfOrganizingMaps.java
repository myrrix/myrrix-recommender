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

package net.myrrix.online.som;

import java.util.Collections;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.distribution.PascalDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.LangUtils;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.math.SimpleVectorMath;
import net.myrrix.common.random.RandomManager;
import net.myrrix.common.random.RandomUtils;

/**
 * <p>This class implements a basic version of
 * <a href="http://en.wikipedia.org/wiki/Self-organizing_map">self-organizing maps</a>, or
 * <a href="http://www.scholarpedia.org/article/Kohonen_network">Kohonen network</a>. Self-organizing maps
 * bear some similarity to clustering techniques like
 * <a href="http://en.wikipedia.org/wiki/K-means_clustering">k-means</a>, in that they both try to discover
 * the centers of relatively close or similar groups of points in the input.</p>
 *
 * <p>K-means and other pure clustering algorithms try to find the centers which best reflect the input's structure.
 * Self-organizing maps have a different priority; the centers it is fitting are connected together as part of
 * a two-dimensional grid, and influence each other as they move. The result is like fitting an elastic 2D grid
 * of points to the input. This constraint results in less faithful clustering -- it is not even primarily a
 * clustering. But it does result in a project of points onto a 2D surface that keeps similar things near
 * to each other -- a sort of randomized ad-hoc 2D map of the space.</p>
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class SelfOrganizingMaps {

  private static final Logger log = LoggerFactory.getLogger(SelfOrganizingMaps.class);

  public static final double DEFAULT_MIN_DECAY = 0.00001;
  public static final double DEFAULT_INIT_LEARNING_RATE = 0.5;

  private final double minDecay;
  private final double initLearningRate;

  public SelfOrganizingMaps() {
    this(DEFAULT_MIN_DECAY, DEFAULT_INIT_LEARNING_RATE);
  }

  /**
   * @param minDecay learning rate decays over iterations; when the decay factor drops below this, stop iteration
   *   as further updates will do little.
   * @param initLearningRate initial learning rate, decaying over time, which controls how much a newly assigned
   *   vector will move vector centers.
   */
  public SelfOrganizingMaps(double minDecay, double initLearningRate) {
    Preconditions.checkArgument(minDecay > 0.0, "Min decay must be positive: {}", minDecay);
    Preconditions.checkArgument(initLearningRate > 0.0 && initLearningRate <= 1.0,
                                "Learning rate should be in (0,1]: {}", initLearningRate);
    this.minDecay = minDecay;
    this.initLearningRate = initLearningRate;
  }

  public Node[][] buildSelfOrganizedMap(FastByIDMap<float[]> vectors, int maxMapSize) {
    return buildSelfOrganizedMap(vectors, maxMapSize, Double.NaN);
  }

  /**
   * @param vectors user-feature or item-feature matrix from current computation generation
   * @param maxMapSize maximum desired dimension of the (square) 2D map
   * @param samplingRate fraction of input to consider when creating the map
   *   size overall, nodes will be pruned to remove least-matching assignments, and not all vectors in the
   *   input will be assigned.
   * @return a square, 2D array of {@link Node} representing the map, with dimension {@code mapSize}
   */
  public Node[][] buildSelfOrganizedMap(FastByIDMap<float[]> vectors, int maxMapSize, double samplingRate) {

    Preconditions.checkNotNull(vectors);
    Preconditions.checkArgument(!vectors.isEmpty());
    Preconditions.checkArgument(maxMapSize > 0);
    Preconditions.checkArgument(Double.isNaN(samplingRate) || (samplingRate > 0.0 && samplingRate <= 1.0));

    if (Double.isNaN(samplingRate)) {
      // Compute a sampling rate that shoots for 1 assignment per node on average
      double expectedNodeSize = (double) vectors.size() / (maxMapSize * maxMapSize);
      samplingRate = expectedNodeSize > 1.0 ? 1.0 / expectedNodeSize : 1.0;
    }
    log.debug("Sampling rate: {}", samplingRate);

    int mapSize = FastMath.min(maxMapSize, (int) FastMath.sqrt(vectors.size() * samplingRate));
    Node[][] map = buildInitialMap(vectors, mapSize);

    sketchMapParallel(vectors, samplingRate, map);

    for (Node[] mapRow : map) {
      for (Node node : mapRow) {
        node.clearAssignedIDs();
      }
    }

    assignVectorsParallel(vectors, samplingRate, map);
    sortMembers(map);

    int numFeatures = vectors.entrySet().iterator().next().getValue().length;
    buildProjections(numFeatures, map);

    return map;
  }

  private void sketchMapParallel(FastByIDMap<float[]> vectors, double samplingRate, Node[][] map) {
    int mapSize = map.length;
    double sigma = (vectors.size() * samplingRate) / Math.log(mapSize);
    int t = 0;
    for (FastByIDMap.MapEntry<float[]> entry : vectors.entrySet()) {
      float[] V = entry.getValue();
      double decayFactor = FastMath.exp(-t / sigma);
      t++;
      if (decayFactor < minDecay) {
        break;
      }
      int[] bmuCoordinates = findBestMatchingUnit(V, map);
      if (bmuCoordinates != null) {      
        updateNeighborhood(map, V, bmuCoordinates[0], bmuCoordinates[1], decayFactor);
      }
    }
  }

  private static void assignVectorsParallel(FastByIDMap<float[]> vectors, double samplingRate, Node[][] map) {
    boolean doSample = samplingRate < 1.0;
    RandomGenerator random = RandomManager.getRandom();
    for (FastByIDMap.MapEntry<float[]> entry : vectors.entrySet()) {
      if (doSample && random.nextDouble() > samplingRate) {
        continue;
      }
      float[] V = entry.getValue();
      int[] bmuCoordinates = findBestMatchingUnit(V, map);
      if (bmuCoordinates != null) {
        Node node = map[bmuCoordinates[0]][bmuCoordinates[1]];
        float[] center = node.getCenter();
        double currentScore =
            SimpleVectorMath.dot(V, center) / (SimpleVectorMath.norm(center) * SimpleVectorMath.norm(V));
        Pair<Double,Long> newAssignedID = new Pair<Double,Long>(currentScore, entry.getKey());
        node.addAssignedID(newAssignedID);
      }
    }
  }

  /**
   * @return map of initialized {@link Node}s, where each node is empty and initialized to a randomly chosen
   *  input vector normalized to unit length
   */
  private static Node[][] buildInitialMap(FastByIDMap<float[]> vectors, int mapSize) {

    double p = ((double) mapSize * mapSize) / vectors.size(); // Choose mapSize^2 out of # vectors
    PascalDistribution pascalDistribution;
    if (p >= 1.0) {
      // No sampling at all, we can't fill the map with one pass even
      pascalDistribution = null;
    } else {
      // Number of un-selected elements to skip between selections is geometrically distributed with
      // parameter p; this is the same as a negative binomial / Pascal distribution with r=1:
      pascalDistribution = new PascalDistribution(RandomManager.getRandom(), 1, p);
    }

    LongPrimitiveIterator keyIterator = vectors.keySetIterator();
    Node[][] map = new Node[mapSize][mapSize];
    for (Node[] mapRow : map) {
      for (int j = 0; j < mapSize; j++) {
        if (pascalDistribution != null) {
          keyIterator.skip(pascalDistribution.sample());
        }
        while (!keyIterator.hasNext()) {
          keyIterator = vectors.keySetIterator(); // Start over, a little imprecise but affects it not much
          Preconditions.checkState(keyIterator.hasNext());
          if (pascalDistribution != null) {
            keyIterator.skip(pascalDistribution.sample());
          }
        }
        float[] sampledVector = vectors.get(keyIterator.nextLong());
        mapRow[j] = new Node(sampledVector);
      }
    }
    return map;
  }

  /**
   * @return coordinates of {@link Node} in map whose center is "closest" to the given vector. Here closeness
   *  is defined as smallest angle between the vectors
   */
  private static int[] findBestMatchingUnit(float[] vector, Node[][] map) {
    int mapSize = map.length;
    double vectorNorm = SimpleVectorMath.norm(vector);
    double bestScore = Double.NEGATIVE_INFINITY;
    int bestI = -1;
    int bestJ = -1;
    for (int i = 0; i < mapSize; i++) {
      Node[] mapRow = map[i];
      for (int j = 0; j < mapSize; j++) {
        float[] center = mapRow[j].getCenter();
        double currentScore = SimpleVectorMath.dot(vector, center) / (SimpleVectorMath.norm(center) * vectorNorm);
        if (LangUtils.isFinite(currentScore) && currentScore > bestScore) {
          bestScore = currentScore;
          bestI = i;
          bestJ = j;
        }
      }
    }
    return bestI == -1 || bestJ == -1 ? null : new int[] {bestI, bestJ};
  }

  /**
   * Completes the update step after assigning an input vector tentatively to a {@link Node}. The assignment
   * causes nearby nodes (including the assigned one) to move their centers towards the vector.
   */
  private void updateNeighborhood(Node[][] map, float[] V, int bmuI, int bmuJ, double decayFactor) {
    int mapSize = map.length;
    double neighborhoodRadius = mapSize * decayFactor;

    int minI = FastMath.max(0, (int) FastMath.floor(bmuI - neighborhoodRadius));
    int maxI = FastMath.min(mapSize, (int) FastMath.ceil(bmuI + neighborhoodRadius));
    int minJ = FastMath.max(0, (int) FastMath.floor(bmuJ - neighborhoodRadius));
    int maxJ = FastMath.min(mapSize, (int) FastMath.ceil(bmuJ + neighborhoodRadius));

    for (int i = minI; i < maxI; i++) {
      Node[] mapRow = map[i];
      for (int j = minJ; j < maxJ; j++) {
        double learningRate = initLearningRate * decayFactor;
        double currentDistance = distance(i, j, bmuI, bmuJ);
        double theta = FastMath.exp(-(currentDistance * currentDistance) /
                                     (2.0 * neighborhoodRadius * neighborhoodRadius));
        double learningTheta = learningRate * theta;
        float[] center = mapRow[j].getCenter();
        int length = center.length;
        // Don't synchronize, for performance. Colliding updates once in a while does little.
        for (int k = 0; k < length; k++) {
          center[k] += (float) (learningTheta * (V[k] - center[k]));
        }
      }
    }
  }

  private static void sortMembers(Node[][] map) {
    for (Node[] mapRow : map) {
      for (Node node : mapRow) {
        Collections.sort(node.getAssignedIDs(), Collections.reverseOrder());
      }
    }
  }

  private static void buildProjections(int numFeatures, Node[][] map) {
    int mapSize = map.length;
    float[] mean = new float[numFeatures];
    for (Node[] mapRow : map) {
      for (int j = 0; j < mapSize; j++) {
        add(mapRow[j].getCenter(), mean);
      }
    }
    divide(mean, mapSize * mapSize);

    RandomGenerator random = RandomManager.getRandom();
    float[] rBasis = RandomUtils.randomUnitVector(numFeatures, random);
    float[] gBasis = RandomUtils.randomUnitVector(numFeatures, random);
    float[] bBasis = RandomUtils.randomUnitVector(numFeatures, random);

    for (Node[] mapRow : map) {
      for (int j = 0; j < mapSize; j++) {
        float[] W = mapRow[j].getCenter().clone();
        subtract(mean, W);
        double norm = SimpleVectorMath.norm(W);
        float[] projection3D = mapRow[j].getProjection3D();
        projection3D[0] = (float) ((1.0 + SimpleVectorMath.dot(W, rBasis) / norm) / 2.0);
        projection3D[1] = (float) ((1.0 + SimpleVectorMath.dot(W, gBasis) / norm) / 2.0);
        projection3D[2] = (float) ((1.0 + SimpleVectorMath.dot(W, bBasis) / norm) / 2.0);
      }
    }
  }

  private static void add(float[] from, float[] to) {
    int length = from.length;
    for (int i = 0; i < length; i++) {
      to[i] += from[i];
    }
  }

  private static void subtract(float[] toSubtract, float[] from) {
    int length = toSubtract.length;
    for (int i = 0; i < length; i++) {
      from[i] -= toSubtract[i];
    }
  }

  private static void divide(float[] x, float by) {
    int length = x.length;
    for (int i = 0; i < length; i++) {
      x[i] /= by;
    }
  }

  private static double distance(int i1, int j1, int i2, int j2) {
    int diff1 = i1 - i2;
    int diff2 = j1 - j2;
    return FastMath.sqrt(diff1 * diff1 + diff2 * diff2);
  }

}
