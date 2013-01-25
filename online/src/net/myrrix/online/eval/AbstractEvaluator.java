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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.io.PatternFilenameFilter;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.GenericRecommendedItem;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.common.iterator.FileLineIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.ByValueAscComparator;
import net.myrrix.common.io.IOUtils;
import net.myrrix.common.LangUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.online.ServerRecommender;

/**
 * Superclass of implementations which can evaluate a recommender according to some metric or process.
 *
 * @author Sean Owen
 */
public abstract class AbstractEvaluator {

  private static final Logger log = LoggerFactory.getLogger(AbstractEvaluator.class);

  private static final char DELIMITER = ',';
  private static final Splitter COMMA_TAB_SPLIT = Splitter.on(CharMatcher.anyOf(",\t")).omitEmptyStrings();

  /**
   * Evaluate a given {@link MyrrixRecommender}, already trained with some training data, using the given
   * test data.
   *
   * @param recommender instance to evaluate, which already has training data appropriate for the supplied test
   *  data
   * @param testData test data to use in the evaluation. It is a {@link Multimap} keyed by user ID, pointing
   *  to many {@link RecommendedItem}s, each of which represents a test datum, which would be considered a
   *  good recommendation.
   */
  public abstract EvaluationResult evaluate(MyrrixRecommender recommender,
                                            Multimap<Long,RecommendedItem> testData) throws TasteException;

  /**
   * @return true iff the implementation should split out test data by taking highest-value items for each user
   */
  protected abstract boolean isSplitTestByPrefValue();

  /**
   * Convenience method which sets up a {@link MyrrixRecommender}, splits data in a given location into test/training
   * data, trains the recommender, then invokes {@link #evaluate(MyrrixRecommender, Multimap)}. Defaults to
   * use 90% of the data for training and 10% for test; no sampling is performed, and all original data is used
   * as either test or training data.
   *
   * @param originalDataDir directory containing recommender input data, as (possibly compressed) CSV files
   *  sets. This is useful for quickly evaluating using a subset of a large data set.
   */
  public final EvaluationResult evaluate(File originalDataDir) throws TasteException, IOException {
    return evaluate(originalDataDir, 0.9, 1.0);
  }

  /**
   * Convenience method which sets up a {@link MyrrixRecommender}, splits data in a given location into test/training
   * data, trains the recommender, then invokes {@link #evaluate(MyrrixRecommender, Multimap)}.
   *
   * @param originalDataDir directory containing recommender input data, as (possibly compressed) CSV files
   * @param trainingPercentage percentage of data to train on; the remainder is test data
   * @param evaluationPercentage percentage of all data to consider, before even splitting into test and training
   *  sets. This is useful for quickly evaluating using a subset of a large data set.
   */
  public final EvaluationResult evaluate(File originalDataDir,
                                         double trainingPercentage,
                                         double evaluationPercentage) throws TasteException, IOException {

    Preconditions.checkArgument(trainingPercentage > 0.0 && trainingPercentage < 1.0,
                                "Training % must be in (0,1): %s", trainingPercentage);
    Preconditions.checkArgument(evaluationPercentage > 0.0 && evaluationPercentage <= 1.0,
                                "Eval % must be in (0,1): %s", evaluationPercentage);
    Preconditions.checkArgument(originalDataDir.exists() && originalDataDir.isDirectory(),
                                "%s is not a directory", originalDataDir);

    File trainingDataDir = Files.createTempDir();
    trainingDataDir.deleteOnExit();
    File trainingFile = new File(trainingDataDir, "training.csv.gz");
    trainingFile.deleteOnExit();

    ServerRecommender recommender = null;
    try {
      Multimap<Long,RecommendedItem> testData =
          split(originalDataDir, trainingFile, trainingPercentage, evaluationPercentage);

      recommender = new ServerRecommender(trainingDataDir);
      recommender.await();

      return evaluate(recommender, testData);
    } finally {
      Closeables.close(recommender, true);
      IOUtils.deleteRecursively(trainingDataDir);
    }
  }

  private Multimap<Long,RecommendedItem> split(File dataDir,
                                               File trainingFile,
                                               double trainPercentage,
                                               double evaluationPercentage) throws IOException {

    Multimap<Long,RecommendedItem> data = readDataFile(dataDir, evaluationPercentage);
    log.info("Read data for {} users from input; splitting...", data.size());

    Multimap<Long,RecommendedItem> testData = ArrayListMultimap.create();
    Writer trainingOut =
        new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(trainingFile)), Charsets.UTF_8);
    try {

      Iterator<Map.Entry<Long,Collection<RecommendedItem>>> it = data.asMap().entrySet().iterator();
      while (it.hasNext()) {

        Map.Entry<Long, Collection<RecommendedItem>> entry = it.next();
        long userID = entry.getKey();
        List<RecommendedItem> userPrefs = Lists.newArrayList(entry.getValue());
        it.remove();

        if (isSplitTestByPrefValue()) {
          // Sort low to high, leaving high values at end for testing as "relevant" items
          Collections.sort(userPrefs, ByValueAscComparator.INSTANCE);
        }
        // else leave sorted in time order

        int numTraining = FastMath.max(1, (int) (trainPercentage * userPrefs.size()));
        for (RecommendedItem rec : userPrefs.subList(0, numTraining)) {
          trainingOut.write(Long.toString(userID) + DELIMITER + rec.getItemID() + DELIMITER + rec.getValue() + '\n');
        }

        for (RecommendedItem rec : userPrefs.subList(numTraining, userPrefs.size())) {
          testData.put(userID, rec);
        }

      }

    } finally {
      trainingOut.close(); // Want to know of output stream close failed -- maybe failed to write
    }

    log.info("{} users in test data", testData.size());

    return testData;
  }

  private static Multimap<Long,RecommendedItem> readDataFile(File dataDir,
                                                             double evaluationPercentage) throws IOException {
    // evaluationPercentage filters per user and item, not per datum, since time scales with users and
    // items. We select sqrt(evaluationPercentage) of users and items to overall select about evaluationPercentage
    // of all data.
    int perMillion = (int) (1000000 * FastMath.sqrt(evaluationPercentage));

    Multimap<Long,RecommendedItem> data = ArrayListMultimap.create();

    for (File dataFile : dataDir.listFiles(new PatternFilenameFilter(".+\\.csv(\\.(zip|gz))?"))) {
      log.info("Reading {}", dataFile);
      int count = 0;
      for (CharSequence line : new FileLineIterable(dataFile)) {
        Iterator<String> parts = COMMA_TAB_SPLIT.split(line).iterator();
        String userIDString = parts.next();
        if (userIDString.hashCode() % 1000000 <= perMillion) {
          String itemIDString = parts.next();
          if (itemIDString.hashCode() % 1000000 <= perMillion) {
            long userID = Long.parseLong(userIDString);
            long itemID = Long.parseLong(itemIDString);
            if (parts.hasNext()) {
              String token = parts.next().trim();
              if (!token.isEmpty()) {
                data.put(userID, new GenericRecommendedItem(itemID, LangUtils.parseFloat(token)));
              }
              // Ignore remove lines
            } else {
              data.put(userID, new GenericRecommendedItem(itemID, 1.0f));
            }
          }
        }
        if (++count % 1000000 == 0) {
          log.info("Finished {} lines", count);
        }
      }
    }
    return data;
  }

}
