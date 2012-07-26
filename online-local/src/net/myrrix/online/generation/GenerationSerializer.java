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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;

import net.myrrix.common.collection.FastByIDMap;

/**
 * A {@link Serializable} wrapper around a {@link Generation} that lets it easily write
 * to a file or stream.
 *
 * @author Sean Owen
 */
public final class GenerationSerializer implements Serializable {

  private Generation generation;

  public GenerationSerializer() {
    this(null);
  }

  public GenerationSerializer(Generation generation) {
    this.generation = generation;
  }

  public Generation getGeneration() {
    return generation;
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    FastByIDMap<FastIDSet> knownItemIDs = generation.getKnownItemIDs();
    out.writeInt(knownItemIDs.size());
    for (FastByIDMap.MapEntry<FastIDSet> entry : knownItemIDs.entrySet()) {
      out.writeLong(entry.getKey());
      FastIDSet itemIDs = entry.getValue();
      out.writeInt(itemIDs.size());
      LongPrimitiveIterator it = itemIDs.iterator();
      while (it.hasNext()) {
        out.writeLong(it.nextLong());
      }
    }
    writeMatrix(generation.getX(), out);
    writeMatrix(generation.getY(), out);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    int knownItemIDsCount = in.readInt();
    FastByIDMap<FastIDSet> newKnownItemIDs = new FastByIDMap<FastIDSet>(knownItemIDsCount, 1.25f);
    for (int i = 0; i < knownItemIDsCount; i++) {
      long id = in.readLong();
      int setCount = in.readInt();
      FastIDSet set = new FastIDSet(setCount, 1.25f);
      for (int j = 0; j < setCount; j++) {
        set.add(in.readLong());
      }
      newKnownItemIDs.put(id, set);
    }
    FastByIDMap<float[]> newX = readMatrix(in);
    FastByIDMap<float[]> newY = readMatrix(in);
    generation = new Generation(newKnownItemIDs, newX, newY);
  }

  /**
   * @see #writeMatrix(FastByIDMap, ObjectOutputStream)
   */
  private static FastByIDMap<float[]> readMatrix(ObjectInputStream in) throws IOException {
    int count = in.readInt();
    FastByIDMap<float[]> matrix = new FastByIDMap<float[]>(count, 1.25f);
    for (int i = 0; i < count; i++) {
      long id = in.readLong();
      float[] features = new float[in.readInt()];
      for (int j = 0; j < features.length; j++) {
        features[j] = in.readFloat();
      }
      matrix.put(id, features);
    }
    return matrix;
  }

  /**
   * @see #readMatrix(ObjectInputStream)
   */
  private static void writeMatrix(FastByIDMap<float[]> matrix,
                                  ObjectOutputStream out) throws IOException {
    out.writeInt(matrix.size());
    for (FastByIDMap.MapEntry<float[]> entry : matrix.entrySet()) {
      out.writeLong(entry.getKey());
      float[] features = entry.getValue();
      out.writeInt(features.length);
      for (float f : features) {
        out.writeFloat(f);
      }
    }
  }

}
