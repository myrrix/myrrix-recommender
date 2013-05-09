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

package net.myrrix.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;

import net.myrrix.common.NotReadyException;
import net.myrrix.common.parallel.Paralleler;
import net.myrrix.common.parallel.Processor;
import net.myrrix.online.RescorerProvider;
import net.myrrix.online.ServerRecommender;

/**
 * <p>A simple command-line program that will compute most similar items for all items. It does not start
 * an instance of the Serving Layer that responds to requests via HTTP. Instead it performs all computations
 * locally, in bulk. This may be useful to create a simple batch recommendation process when that is
 * all that's needed.</p>
 *
 * <p>Results are written to the file indicated by {@code --outFile}. The format mirrors that of the Computation
 * Layer. Each line begins with an item ID, followed by tab, followed "[item1:sim1,item2:sim2,...]"</p>
 *
 * <p>Example usage:</p>
 *
 * <p>{@code java -cp myrrix-serving-x.y.jar net.myrrix.web.AllItemSimilarities
 *   --localInputDir=[work dir] --outFile=out.txt --howMany=[# similar items] 
 *   --rescorerProviderClass=[your class(es)]}</p>
 *
 * @author Sean Owen
 * @see AllRecommendations
 */
public final class AllItemSimilarities implements Callable<Object> {

  private final AllConfig config;

  public AllItemSimilarities(AllConfig config) {
    Preconditions.checkNotNull(config);
    this.config = config;
  }

  public static void main(String[] args) throws Exception {
    AllConfig config = AllConfig.build(args);
    if (config != null) {
      new AllItemSimilarities(config).call();
    }
  }

  @Override
  public Object call() throws IOException, InterruptedException, NotReadyException, ExecutionException {

    final ServerRecommender recommender = new ServerRecommender(config.getLocalInputDir());
    recommender.await();

    final RescorerProvider rescorerProvider = config.getRescorerProvider();
    final int howMany = config.getHowMany();

    File outFile = config.getOutFile();
    outFile.delete();
    final Writer out = new OutputStreamWriter(new FileOutputStream(outFile), Charsets.UTF_8);
    
    Processor<Long> processor = new Processor<Long>() {
      @Override
      public void process(Long itemID, long count) throws ExecutionException {
        Rescorer<LongPair> rescorer =
            rescorerProvider == null ? null : rescorerProvider.getMostSimilarItemsRescorer(recommender);
        List<RecommendedItem> similar;
        try {
          similar = recommender.mostSimilarItems(new long[]{itemID}, howMany, rescorer);
        } catch (TasteException te) {
          throw new ExecutionException(te);
        }
        String outLine = formatOutLine(itemID, similar);
        synchronized (out) {
          try {
            out.write(outLine);
          } catch (IOException e) {
            throw new ExecutionException(e);
          }
        }
      }
    };

    Paralleler<Long> paralleler = 
        new Paralleler<Long>(recommender.getAllItemIDs().iterator(), processor, "AllItemSimilarities");
    if (config.isParallel()) {
      paralleler.runInParallel();
    } else {
      paralleler.runInSerial();
    }

    out.close();
    return null;
  }
  
  static String formatOutLine(long id, Iterable<RecommendedItem> recs) {
    StringBuilder result = new StringBuilder(100);
    result.append(id);
    result.append("\t[");
    boolean first = true;
    for (RecommendedItem rec : recs) {
      if (first) {
        first = false;
      } else {
        result.append(',');
      }
      result.append(rec.getItemID()).append(':').append(rec.getValue());
    }
    result.append("]\n");
    return result.toString();
  }

}
