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
import java.util.Iterator;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.common.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    Preconditions.checkArgument(minDecay > 0.0);
    Preconditions.checkArgument(initLearningRate > 0.0 && initLearningRate <= 1.0);
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
    log.info("Sampling rate: {}", samplingRate);
    boolean doSample = samplingRate < 1.0;

    int mapSize = (int) FastMath.sqrt(vectors.size() * samplingRate);
    mapSize = FastMath.min(maxMapSize, mapSize);

    RandomGenerator random = RandomManager.getRandom();

    int numFeatures = vectors.entrySet().iterator().next().getValue().length;
    Node[][] map = buildInitialMap(vectors, mapSize, random);

    double sigma = (vectors.size() * samplingRate) / FastMath.log(mapSize);

    int t = 0;
    for (FastByIDMap.MapEntry<float[]> vector : vectors.entrySet()) {
      if (doSample && random.nextDouble() > samplingRate) {
        continue;
      }
      float[] V = vector.getValue();
      int[] bmuCoordinates = findBestMatchingUnit(V, map);
      double decayFactor = FastMath.exp(-t / sigma);
      if (decayFactor < minDecay) {
        break;
      }
      updateNeighborhood(map, V, bmuCoordinates, decayFactor);
      t++;
    }

    for (Node[] mapRow : map) {
      for (int j = 0; j < mapSize; j++) {
        mapRow[j].clearAssignedIDs();
      }
    }

    for (FastByIDMap.MapEntry<float[]> vector : vectors.entrySet()) {
      if (doSample && random.nextDouble() > samplingRate) {
        continue;
      }
      float[] V = vector.getValue();
      int[] bmuCoordinates = findBestMatchingUnit(V, map);
      Node node = map[bmuCoordinates[0]][bmuCoordinates[1]];
      float[] center = node.getCenter();
      double currentScore =
          SimpleVectorMath.dot(V, center) / (SimpleVectorMath.norm(center) * SimpleVectorMath.norm(V));
      node.addAssignedID(Pair.of(currentScore, vector.getKey()));
    }

    sortMembers(map);
    buildProjections(numFeatures, map, random);

    return map;
  }

  /**
   * @return map of initialized {@link Node}s, where each node is empty and initialized to a randomly chosen
   *  input vector normalized to unit length
   */
  private static Node[][] buildInitialMap(FastByIDMap<float[]> vectors, int mapSize, RandomGenerator random) {
    double selectionProbability = 1.0 / vectors.size();
    Iterator<FastByIDMap.MapEntry<float[]>> it = vectors.entrySet().iterator();
    Node[][] map = new Node[mapSize][mapSize];
    for (Node[] mapRow : map) {
      for (int j = 0; j < mapSize; j++) {
        float[] chosenVector = null;
        while (chosenVector == null) {
          if (!it.hasNext()) { // Start over; not exactly random but OK
            it = vectors.entrySet().iterator();
          }
          FastByIDMap.MapEntry<float[]> entry = it.next();
          if (random.nextDouble() < selectionProbability) {
            chosenVector = entry.getValue().clone();
            divide(chosenVector, (float) SimpleVectorMath.norm(chosenVector));
          }
        }
        mapRow[j] = new Node(chosenVector);
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
        if (currentScore > bestScore) {
          bestScore = currentScore;
          bestI = i;
          bestJ = j;
        }
      }
    }
    return new int[] {bestI, bestJ};
  }

  /**
   * Completes the update step after assigning an input vector tentatively to a {@link Node}. The assignment
   * causes nearby nodes (including the assigned one) to move their centers towards the vector.
   */
  private void updateNeighborhood(Node[][] map, float[] v, int[] bmuCoordinates, double decayFactor) {
    int mapSize = map.length;
    double neighborhoodRadius = mapSize * decayFactor;

    int bmuI = bmuCoordinates[0];
    int bmuJ = bmuCoordinates[1];

    int minI = Math.max(0, (int) FastMath.floor(bmuI - neighborhoodRadius));
    int maxI = Math.min(mapSize, (int) FastMath.ceil(bmuI + neighborhoodRadius));
    int minJ = Math.max(0, (int) FastMath.floor(bmuJ - neighborhoodRadius));
    int maxJ = Math.min(mapSize, (int) FastMath.ceil(bmuJ + neighborhoodRadius));

    for (int i = minI; i < maxI; i++) {
      for (int j = minJ; j < maxJ; j++) {
        double learningRate = initLearningRate * decayFactor;
        double currentDistance = distance(i, j, bmuI, bmuJ);
        double theta = FastMath.exp(-(currentDistance * currentDistance) /
                                     (2.0 * neighborhoodRadius * neighborhoodRadius));
        double learningTheta = learningRate * theta;
        float[] W = map[i][j].getCenter();
        for (int k = 0; k < W.length; k++) {
          W[k] += (float) (learningTheta * (v[k] - W[k]));
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

  private static void buildProjections(int numFeatures, Node[][] map, RandomGenerator random) {
    int mapSize = map.length;
    float[] mean = new float[numFeatures];
    for (Node[] mapRow : map) {
      for (int j = 0; j < mapSize; j++) {
        add(mapRow[j].getCenter(), mean);
      }
    }
    divide(mean, mapSize * mapSize);

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
