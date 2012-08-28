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

package net.myrrix.client.eval;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.Closeables;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.IRStatistics;
import org.apache.mahout.cf.taste.impl.recommender.GenericRecommendedItem;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.common.iterator.FileLineIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.client.ClientRecommender;
import net.myrrix.client.MyrrixClientConfiguration;
import net.myrrix.common.LangUtils;
import net.myrrix.web.Runner;
import net.myrrix.web.RunnerConfiguration;

public abstract class AbstractEvaluator {

  private static final Logger log = LoggerFactory.getLogger(AbstractEvaluator.class);

  private static final Splitter COMMA_TAB_SPLIT = Splitter.on(CharMatcher.anyOf(",\t")).omitEmptyStrings();
  private static final char DELIMITER = ',';

  private final File trainingDataDir;

  AbstractEvaluator(File trainingDataDir) {
    this.trainingDataDir = trainingDataDir;
  }

  /**
   * Runs a recommender locally on port 8080 and conducts a precision/recall test against it.
   *
   * @return {@link IRStatistics} reporting precision, recall, and nDCG results of the test
   */
  public final EvaluationResult evaluate() throws TasteException {

    RunnerConfiguration runnerConfig = new RunnerConfiguration();
    runnerConfig.setInstanceID(0L);
    runnerConfig.setPort(8080);
    runnerConfig.setLocalInputDir(trainingDataDir);

    Runner runner = new Runner(runnerConfig);

    int clientPort = runnerConfig.getPort();
    MyrrixClientConfiguration clientConfig = new MyrrixClientConfiguration();
    clientConfig.setHost("127.0.0.1");
    clientConfig.setPort(clientPort);

    try {
      runner.call();
      ClientRecommender client = new ClientRecommender(clientConfig);
      while (!client.isReady()) {
        try {
          Thread.sleep(1000L);
        } catch (InterruptedException ie) {
          // continue
        }
      }
      return doEvaluate(client);
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    } finally {
      runner.close();
    }
  }

  abstract EvaluationResult doEvaluate(ClientRecommender client) throws TasteException;

  static Multimap<Long,RecommendedItem> split(File dataFile,
                                              File trainingFile,
                                              double trainPercentage,
                                              double evaluationPercentage,
                                              boolean sortByValue) throws IOException {

    Multimap<Long,RecommendedItem> data = readDataFile(dataFile, evaluationPercentage);
    log.info("Read data for {} users from input; splitting...", data.size());

    Multimap<Long,RecommendedItem> testData = ArrayListMultimap.create();
    Writer trainingOut =
        new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(trainingFile)), Charsets.UTF_8);
    try {
      for (Map.Entry<Long,Collection<RecommendedItem>> entry : data.asMap().entrySet()) {

        long userID = entry.getKey();
        List<RecommendedItem> userPrefs = Lists.newArrayList(entry.getValue());

        if (sortByValue) {
          // Sort low to high, leaving high values at end for testing as "relevant" items
          Collections.sort(userPrefs, new Comparator<RecommendedItem>() {
            @Override
            public int compare(RecommendedItem a, RecommendedItem b) {
              return a.getValue() < b.getValue() ? -1 : a.getValue() > b.getValue() ? 1 : 0;
            }
          });
        }
        // else leave sorted in time order

        int numTraining = (int) (trainPercentage * userPrefs.size());
        for (RecommendedItem rec : userPrefs.subList(0, numTraining)) {
          trainingOut.write(Long.toString(userID) + DELIMITER + rec.getItemID() + DELIMITER + rec.getValue() + '\n');
        }

        for (RecommendedItem rec : userPrefs.subList(numTraining, userPrefs.size())) {
          testData.put(userID, rec);
        }

      }

    } finally {
      Closeables.closeQuietly(trainingOut);
    }

    return testData;
  }

  static Multimap<Long,RecommendedItem> readDataFile(File dataFile, double evaluationPercentage) throws IOException {
    // evaluationPercentage filters per user and item, not per datum, since time scales with users and
    // items. We select sqrt(evaluationPercentage) of users and items to overall select about evaluationPercentage
    // of all data.
    int perMillion = (int) (1000000 * Math.sqrt(evaluationPercentage));

    Multimap<Long,RecommendedItem> data = ArrayListMultimap.create();

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
    return data;
  }

}
