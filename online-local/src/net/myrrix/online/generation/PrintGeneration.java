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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;

import com.google.common.base.Charsets;

import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;

/**
 * <p>Simply prints the contents of a generation model file.</p>
 *
 * <p>{@code java -cp ... net.myrrix.online.generation.PrintGeneration [model.bin file]}</p>
 *
 * @author Sean Owen
 */
public final class PrintGeneration {

  private PrintGeneration() {
  }

  public static void main(String[] args) throws Exception {
    File modelFile = new File(args[0]);
    File outFile = args.length > 1 ? new File(args[1]) : null;

    Generation generation = GenerationSerializer.readGeneration(modelFile);

    if (outFile == null) {
      print(generation, System.out);
    } else {
      Writer out = new OutputStreamWriter(new FileOutputStream(outFile), Charsets.UTF_8);
      try {
        print(generation, out);
      } finally {
        out.close();
      }
    }
  }

  public static void print(Generation generation, Appendable out) throws IOException {
    out.append("X:\n");
    printFeatureMatrix(generation.getX(), out);
    out.append('\n');

    out.append("Y:\n");
    printFeatureMatrix(generation.getY(), out);
    out.append('\n');

    //out.append("Known User IDs:\n");
    //printKnownItems(generation.getKnownUserIDs(), out);
    //out.append('\n');

    out.append("Known Item IDs:\n");
    printKnownItems(generation.getKnownItemIDs(), out);
    out.append('\n');

    out.append("User Clusters / Centroids:\n");
    printCentroids(generation.getUserClusters(), out);
    out.append("Item Clusters / Centroids:\n");
    printCentroids(generation.getItemClusters(), out);
    out.append('\n');
  }

  private static void printFeatureMatrix(FastByIDMap<float[]> M, Appendable out) throws IOException {
    if (M != null) {
      for (FastByIDMap.MapEntry<float[]> entry : M.entrySet()) {
        long id = entry.getKey();
        float[] values = entry.getValue();
        StringBuilder line = new StringBuilder();
        line.append(id);
        for (float value : values) {
          line.append('\t').append(value);
        }
        out.append(line).append('\n');
      }
    }
  }

  private static void printKnownItems(FastByIDMap<FastIDSet> known, Appendable out) throws IOException {
    if (known != null) {
      for (FastByIDMap.MapEntry<FastIDSet> entry : known.entrySet()) {
        long id = entry.getKey();
        FastIDSet keys = entry.getValue();
        StringBuilder line = new StringBuilder();
        line.append(id);
        for (long key : keys) {
          line.append('\t').append(key);
        }
        out.append(line).append('\n');
      }
    }
  }

  private static void printCentroids(Iterable<IDCluster> clusters, Appendable out) throws IOException {
    if (clusters != null) {
      for (IDCluster cluster : clusters) {
        out.append(Arrays.toString(cluster.getCentroid())).append('\n');
        out.append(cluster.getMembers().toString()).append('\n');
      }
    }
  }

}
