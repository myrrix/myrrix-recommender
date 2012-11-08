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
import java.util.Random;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.RandomUtils;

import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.math.SimpleVectorMath;

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

  /**
   * @param vectors user-feature or item-feature matrix from current computation generation
   * @param mapSize dimension of the (square) 2D map of {@link Node}
   * @return a square, 2D array of {@link Node} representing the map, with dimension {@code mapSize}
   */
  public Node[][] buildSelfOrganizedMap(FastByIDMap<float[]> vectors, int mapSize) {

    Preconditions.checkNotNull(vectors);
    Preconditions.checkArgument(!vectors.isEmpty());
    Preconditions.checkArgument(mapSize > 0);

    int numFeatures = vectors.entrySet().iterator().next().getValue().length;
    Node[][] map = buildInitialMap(vectors, mapSize);

    double sigma = vectors.size() / FastMath.log(mapSize);

    int t = 0;
    for (FastByIDMap.MapEntry<float[]> vector : vectors.entrySet()) {
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
        mapRow[j].getAssignedIDs().clear();
      }
    }
    for (FastByIDMap.MapEntry<float[]> vector : vectors.entrySet()) {
      float[] V = vector.getValue();
      int[] bmuCoordinates = findBestMatchingUnit(V, map);
      Node node = map[bmuCoordinates[0]][bmuCoordinates[1]];
      float[] center = node.getCenter();
      double currentScore =
          SimpleVectorMath.dot(V, center) / (SimpleVectorMath.norm(center) * SimpleVectorMath.norm(V));
      map[bmuCoordinates[0]][bmuCoordinates[1]].getAssignedIDs().add(Pair.of(currentScore, vector.getKey()));
    }

    sortMembers(map);
    buildProjections(numFeatures, map);

    return map;
  }

  /**
   * @return map of initialized {@link Node}s, where each node is empty and initialized to a randomly chosen
   *  input vector normalized to unit length
   */
  private static Node[][] buildInitialMap(FastByIDMap<float[]> vectors, int mapSize) {
    Random random = RandomUtils.getRandom();
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
   * @return a vector whose elements are uniformly chosen in [0.5,-0.5], then normalized to unit length
   */
  private static float[] randomUnitVector(int size, Random random) {
    float[] vector = new float[size];
    double total = 0.0;
    for (int k = 0; k < size; k++) {
      double d = random.nextDouble() - 0.5;
      total += d * d;
      vector[k] = (float) d;
    }
    float norm = (float) FastMath.sqrt(total);
    for (int k = 0; k < size; k++) {
      vector[k] /= norm;
    }
    return vector;
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
      for (int j = 0; j < mapSize; j++) {
        float[] center = map[i][j].getCenter();
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

  private static void buildProjections(int numFeatures, Node[][] map) {
    int mapSize = map.length;
    float[] mean = new float[numFeatures];
    for (Node[] mapRow : map) {
      for (int j = 0; j < mapSize; j++) {
        add(mapRow[j].getCenter(), mean);
      }
    }
    divide(mean, mapSize * mapSize);

    Random random = RandomUtils.getRandom();
    float[] rBasis = randomUnitVector(numFeatures, random);
    float[] gBasis = randomUnitVector(numFeatures, random);
    float[] bBasis = randomUnitVector(numFeatures, random);

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
