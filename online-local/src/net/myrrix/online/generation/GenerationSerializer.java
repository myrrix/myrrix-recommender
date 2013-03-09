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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;

import net.myrrix.common.LangUtils;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.io.IOUtils;

/**
 * A {@link Serializable} wrapper around a {@link Generation} that lets it easily write
 * to a file or stream, with convenience methods {@link #readGeneration(File)} and
 * {@link #writeGeneration(Generation, File)} to do so.
 *
 * @author Sean Owen
 */
public final class GenerationSerializer implements Serializable {

  private Generation generation;

  /**
   * Exists only for the serialization mechanism.
   */
  public GenerationSerializer() {
    this(null);
  }

  public GenerationSerializer(Generation generation) {
    this.generation = generation;
  }

  public Generation getGeneration() {
    return generation;
  }

  /**
   * @param f file to read {@code GenerationSerializer} from
   * @return {@link Generation} it serializes
   */
  public static Generation readGeneration(File f) throws IOException {
    ObjectInputStream in = new ObjectInputStream(IOUtils.openMaybeDecompressing(f));
    try {
      GenerationSerializer serializer = (GenerationSerializer) in.readObject();
      return serializer.getGeneration();
    } catch (ClassNotFoundException cnfe) {
      throw new IllegalStateException(cnfe);
    } finally {
      in.close();
    }
  }

  /**
   * @param generation {@link Generation} to serialize
   * @param f file to serialize a {@code GenerationSerializer} to
   */
  public static void writeGeneration(Generation generation, File f) throws IOException {
    Preconditions.checkArgument(f.getName().endsWith(".gz"), "File should end in .gz: %s", f);
    ObjectOutputStream out = new ObjectOutputStream(IOUtils.buildGZIPOutputStream(f));
    try {
      out.writeObject(new GenerationSerializer(generation));
    } finally {
      out.close();
    }
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    FastByIDMap<FastIDSet> knownItemIDs = generation.getKnownItemIDs();
    writeKnownIDs(out, knownItemIDs);
    writeMatrix(generation.getX(), out);
    writeMatrix(generation.getY(), out);
    writeIDSet(generation.getItemTagIDs(), out);
    writeIDSet(generation.getUserTagIDs(), out);
    writeClusters(generation.getUserClusters(), out);
    writeClusters(generation.getItemClusters(), out);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    FastByIDMap<FastIDSet> newKnownItemIDs = readKnownIDs(in);
    FastByIDMap<float[]> newX = readMatrix(in);
    FastByIDMap<float[]> newY = readMatrix(in);
    FastIDSet itemTagIDs = readIDSet(in);
    FastIDSet userTagIDs = readIDSet(in);
    List<IDCluster> userClusters = readClusters(in);
    List<IDCluster> itemClusters = readClusters(in);
    generation = new Generation(newKnownItemIDs,
                                newX,
                                newY,
                                itemTagIDs,
                                userTagIDs,
                                userClusters,
                                itemClusters);
  }

  private static FastByIDMap<FastIDSet> readKnownIDs(ObjectInputStream in) throws IOException {
    int knownItemIDsCount = in.readInt();
    FastByIDMap<FastIDSet> newKnownItemIDs;
    if (knownItemIDsCount == 0) {
      newKnownItemIDs = null;
    } else {
      newKnownItemIDs = new FastByIDMap<FastIDSet>(knownItemIDsCount, 1.25f);
      for (int i = 0; i < knownItemIDsCount; i++) {
        long id = in.readLong();
        int setCount = in.readInt();
        FastIDSet set = new FastIDSet(setCount, 1.25f);
        for (int j = 0; j < setCount; j++) {
          set.add(in.readLong());
        }
        newKnownItemIDs.put(id, set);
      }
    }
    return newKnownItemIDs;
  }

  private static void writeKnownIDs(ObjectOutputStream out, FastByIDMap<FastIDSet> knownItemIDs) throws IOException {
    if (knownItemIDs == null) {
      out.writeInt(0);
    } else {
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
    }
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
        float f = in.readFloat();
        Preconditions.checkState(LangUtils.isFinite(f));
        features[j] = f;
      }
      matrix.put(id, features);
    }
    return matrix;
  }

  /**
   * @see #readMatrix(ObjectInputStream)
   */
  private static void writeMatrix(FastByIDMap<float[]> matrix, ObjectOutputStream out) throws IOException {
    if (matrix == null) {
      out.writeInt(0);
    } else {
      out.writeInt(matrix.size());
      for (FastByIDMap.MapEntry<float[]> entry : matrix.entrySet()) {
        out.writeLong(entry.getKey());
        float[] features = entry.getValue();
        out.writeInt(features.length);
        for (float f : features) {
          Preconditions.checkState(LangUtils.isFinite(f));
          out.writeFloat(f);
        }
      }
    }
  }
  
  private static FastIDSet readIDSet(ObjectInputStream in) throws IOException {
    int count = in.readInt();
    FastIDSet ids = new FastIDSet(count, 1.25f);
    for (int i = 0; i < count; i++) {
      ids.add(in.readLong());
    }
    return ids;
  }
  
  private static void writeIDSet(FastIDSet ids, ObjectOutputStream out) throws IOException {
    if (ids == null) {
      out.writeInt(0);
    } else {
      out.writeInt(ids.size());
      LongPrimitiveIterator it = ids.iterator();
      while (it.hasNext()) {
        out.writeLong(it.nextLong());
      }
    }
  }

  private static List<IDCluster> readClusters(ObjectInputStream in) throws IOException {
    int count = in.readInt();
    List<IDCluster> clusters = Lists.newArrayListWithCapacity(count);
    for (int i = 0; i < count; i++) {
      int membersSize = in.readInt();
      FastIDSet members = new FastIDSet(membersSize);
      for (int j = 0; j < membersSize; j++) {
        members.add(in.readLong());
      }
      int centroidSize = in.readInt();
      float[] centroid = new float[centroidSize];
      for (int j = 0; j < centroidSize; j++) {
        centroid[j] = in.readFloat();
      }
      clusters.add(new IDCluster(members, centroid));
    }
    return clusters;
  }

  private static void writeClusters(Collection<IDCluster> clusters, ObjectOutputStream out) throws IOException {
    if (clusters == null) {
      out.writeInt(0);
    } else {
      out.writeInt(clusters.size());
      for (IDCluster cluster : clusters) {
        FastIDSet members = cluster.getMembers();
        out.writeInt(members.size());
        LongPrimitiveIterator it = members.iterator();
        while (it.hasNext()) {
          out.writeLong(it.nextLong());
        }
        float[] centroid = cluster.getCentroid();
        out.writeInt(centroid.length);
        for (float f : centroid) {
          out.writeFloat(f);
        }
      }
    }
  }

}
