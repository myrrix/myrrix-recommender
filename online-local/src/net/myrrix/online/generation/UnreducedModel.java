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

import com.google.common.base.Preconditions;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.collection.FastByIDFloatMap;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;

/**
 * <p>Normally, the input to the recommender is construed as a very large sparse matrix, and factored (approximately)
 * as two low-rank user-feature / feature-item matrices. The input's dimensionality is reduced in this way.</p>
 * 
 * <p>But it's possible that one (or both) dimensions is already low; maybe there are only 30 items in the 
 * model to begin with. There is no point in constructing a lower-dimension approximation since it is already
 * low dimension. In this case, the factorization is trivial: the user-item matrix is already a tall/skinny
 * matrix which can be thought of as the user-feature matrix, and the "feature-item" matrix is just the
 * identity matrix. Or vice versa if there are few users.</p>
 * 
 * <p>This utility program will read input and output the result as a {@code model.bin} file suitable
 * for use with the Serving Layer, without dimensionality reduction. Of course, this should only be
 * done if the dimensionality of one or both is in fact low!</p>
 * 
 * <p>Usage: {@code UnreducedModel [input file dir]}</p>
 * 
 * @author Sean Owen
 */
public final class UnreducedModel {
  
  private static final Logger log = LoggerFactory.getLogger(UnreducedModel.class);

  private UnreducedModel() {
  }

  public static void main(String[] args) throws Exception {
    File inputDir = new File(args[0]);
    writeUnreducedModel(inputDir);
  }

  public static void writeUnreducedModel(File inputDir) throws IOException {
    
    Preconditions.checkNotNull(inputDir);
    Preconditions.checkArgument(inputDir.exists());
    Preconditions.checkArgument(inputDir.isDirectory());
    
    FastByIDMap<FastIDSet> knownItemIDs = new FastByIDMap<FastIDSet>(10000, 1.25f);
    FastByIDMap<FastByIDFloatMap> RbyRow = new FastByIDMap<FastByIDFloatMap>(10000, 1.25f);
    FastByIDMap<FastByIDFloatMap> RbyColumn = new FastByIDMap<FastByIDFloatMap>(10000, 1.25f);
    InputFilesReader.readInputFiles(knownItemIDs, RbyRow, RbyColumn, inputDir);

    int numUsers = RbyRow.size();
    int numItems = RbyColumn.size();
    if (numUsers == 0 || numItems == 0) {
      log.warn("No input?");
      return;
    }
    
    FastByIDMap<float[]> X;
    FastByIDMap<float[]> Y;
    if (numUsers < numItems) {
      log.info("{} users < {} items; input will be written as the feature-item matrix", numUsers, numItems);
      long[] idsInOrder = idsInOrder(RbyRow);
      X = buildIdentity(RbyRow, idsInOrder);
      Y = buildBinarizedMatrix(RbyColumn, idsInOrder);
    } else {
      log.info("{} users >= {} items; input will be written as the user-feature matrix", numUsers, numItems);
      long[] idsInOrder = idsInOrder(RbyColumn);      
      X = buildBinarizedMatrix(RbyRow, idsInOrder);
      Y = buildIdentity(RbyColumn, idsInOrder);
    }
    
    Generation generation = new Generation(knownItemIDs, X, Y);
    GenerationSerializer.writeGeneration(generation, new File(inputDir, "model.bin"));
  }
  
  private static FastByIDMap<float[]> buildIdentity(FastByIDMap<FastByIDFloatMap> input, long[] idsInOrder) {
    int n = input.size();    
    FastByIDMap<float[]> identity = new FastByIDMap<float[]>(n);
    for (int i = 0; i < n; i++) {
      float[] rowOrCol = new float[n];
      rowOrCol[i] = 1.0f;
      identity.put(idsInOrder[i], rowOrCol);
    }
    return identity;
  }
  
  private static FastByIDMap<float[]> buildBinarizedMatrix(FastByIDMap<FastByIDFloatMap> input, long[] idsInOrder) {
    int n = idsInOrder.length;
    FastByIDMap<float[]> result = new FastByIDMap<float[]>();
    for (FastByIDMap.MapEntry<FastByIDFloatMap> entry : input.entrySet()) {
      float[] rowOrCol = new float[n];
      FastByIDFloatMap inputValues = entry.getValue();
      for (int i = 0; i < idsInOrder.length; i++) {
        if (inputValues.containsKey(idsInOrder[i])) {
          rowOrCol[i] = 1.0f;
        }
      }
      result.put(entry.getKey(), rowOrCol);
    }
    return result;
  }
  
  private static long[] idsInOrder(FastByIDMap<?> input) {
    int n = input.size();    
    long[] idsInOrder = new long[n];
    int count = 0;
    LongPrimitiveIterator it = input.keySetIterator();
    while (it.hasNext()) {
      idsInOrder[count] = it.nextLong();
      count++;
    }
    Preconditions.checkState(n == count);
    return idsInOrder;
  }

}
