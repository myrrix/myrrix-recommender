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
import java.io.ObjectInputStream;

import com.google.common.io.Closeables;

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
    GenerationSerializer serializer;
    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile));
    try {
      serializer = (GenerationSerializer) ois.readObject();
    } finally {
      Closeables.close(ois, true);
    }
    Generation generation = serializer.getGeneration();
    print(generation);
  }

  public static void print(Generation generation) {
    System.out.println("X:");
    printFeatureMatrix(generation.getX());
    System.out.println();

    System.out.println("Y:");
    printFeatureMatrix(generation.getY());
    System.out.println();

    //System.out.println("knownUserIDs:");
    //printKnownItems(generation.getKnownUserIDs());
    //System.out.println();

    System.out.println("knownItemIDs:");
    printKnownItems(generation.getKnownItemIDs());
    System.out.println();
  }

  private static void printFeatureMatrix(FastByIDMap<float[]> M) {
    if (M != null) {
      for (FastByIDMap.MapEntry<float[]> entry : M.entrySet()) {
        long id = entry.getKey();
        float[] values = entry.getValue();
        StringBuilder line = new StringBuilder();
        line.append(id);
        for (float value : values) {
          line.append('\t').append(value);
        }
        System.out.println(line);
      }
    }
  }

  private static void printKnownItems(FastByIDMap<FastIDSet> known) {
    if (known != null) {
      for (FastByIDMap.MapEntry<FastIDSet> entry : known.entrySet()) {
        long id = entry.getKey();
        FastIDSet keys = entry.getValue();
        StringBuilder line = new StringBuilder();
        line.append(id);
        for (long key : keys) {
          line.append('\t').append(key);
        }
        System.out.println(line);
      }
    }
  }

}
