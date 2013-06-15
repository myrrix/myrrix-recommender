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

package net.myrrix.online.eval;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.io.PatternFilenameFilter;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.GenericRecommendedItem;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.LangUtils;
import net.myrrix.common.iterator.FileLineIterable;
import net.myrrix.common.math.SimpleVectorMath;
import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.io.IOUtils;
import net.myrrix.online.ServerRecommender;
import net.myrrix.online.generation.Generation;

/**
 * <p>A different kind of evaluator that tests not so much the quality of the recommendations versus input,
 * but the quality of the reconstruction of the input by factored matrices. Perfect reconstruction is not
 * possible in a low-dimension space (or even desirable). The reconstruction over the non-zero entries of
 * P (input reduced to 0/1) however should be fairly small and this provides the means to check that.</p>
 *
 * <p>The output is the average difference between the reconstruction of a value for an existing user-item
 * pair. Negative differences (where > 1 was predicted) are counted as 0.</p>
 * 
 * <p>This class can be run as a Java program; the single argument is a directory containing test data.
 * The {@link EvaluationResult} is printed to standard out.</p>
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class ReconstructionEvaluator {

  private static final Logger log = LoggerFactory.getLogger(ReconstructionEvaluator.class);

  private static final Splitter COMMA_TAB_SPLIT = Splitter.on(CharMatcher.anyOf(",\t")).omitEmptyStrings();

  public EvaluationResult evaluate(File originalDataDir) throws TasteException, IOException, InterruptedException {

    Preconditions.checkArgument(originalDataDir.exists() && originalDataDir.isDirectory(),
                                "%s is not a directory", originalDataDir);
    File tempDir = Files.createTempDir();

    ServerRecommender recommender = null;
    try {

      Multimap<Long,RecommendedItem> data;
      try {
        data = readAndCopyDataFiles(originalDataDir, tempDir);
      } catch (IOException ioe) {
        throw new TasteException(ioe);
      }

      recommender = new ServerRecommender(tempDir);
      recommender.await();

      Generation generation = recommender.getGenerationManager().getCurrentGeneration();
      FastByIDMap<float[]> X = generation.getX();
      FastByIDMap<float[]> Y = generation.getY();

      Mean averageError = new Mean();
      // Only compute average over existing entries...
      for (Map.Entry<Long,RecommendedItem> entry : data.entries()) {
        long userID = entry.getKey();
        long itemID = entry.getValue().getItemID();
        // Each of which was a "1" in the factor P matrix
        double value = SimpleVectorMath.dot(X.get(userID), Y.get(itemID));
        // So store abs(1-value), except, don't penalize for reconstructing > 1. Error is 0 in this case.
        averageError.increment(FastMath.max(0.0, 1.0 - value));
      }

      return new EvaluationResultImpl(averageError.getResult());
    } finally {
      Closeables.close(recommender, true);
      IOUtils.deleteRecursively(tempDir);
    }
  }

  private static Multimap<Long,RecommendedItem> readAndCopyDataFiles(File dataDir, File tempDir) throws IOException {
    Multimap<Long,RecommendedItem> data = ArrayListMultimap.create();
    for (File dataFile : dataDir.listFiles(new PatternFilenameFilter(".+\\.csv(\\.(zip|gz))?"))) {
      log.info("Reading {}", dataFile);
      int count = 0;
      for (CharSequence line : new FileLineIterable(dataFile)) {
        Iterator<String> parts = COMMA_TAB_SPLIT.split(line).iterator();
        long userID = Long.parseLong(parts.next());
        long itemID = Long.parseLong(parts.next());
        if (parts.hasNext()) {
          String token = parts.next().trim();
          if (!token.isEmpty()) {
            data.put(userID, new GenericRecommendedItem(itemID, LangUtils.parseFloat(token)));
          }
          // Ignore remove lines
        } else {
          data.put(userID, new GenericRecommendedItem(itemID, 1.0f));
        }
        if (++count % 1000000 == 0) {
          log.info("Finished {} lines", count);
        }
      }

      Files.copy(dataFile, new File(tempDir, dataFile.getName()));
    }
    return data;
  }

  public static void main(String[] args) throws Exception {
    ReconstructionEvaluator eval = new ReconstructionEvaluator();
    EvaluationResult result = eval.evaluate(new File(args[0]));
    log.info(result.toString());
  }

}
