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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.collect.Lists;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.common.ClassUtils;
import net.myrrix.online.RescorerProvider;
import net.myrrix.online.ServerRecommender;

/**
 * <p>A simple command-line program that will compute recommendations for all users. It does not start
 * an instance of the Serving Layer that responds to requests via HTTP. Instead it performs all computations
 * locally, in bulk. This may be useful to create a simple batch recommendation process when that is
 * all that's needed.</p>
 *
 * <p>Results are written to {@link System#out}. Each user ID is written on line to start.
 * Following that, recommendations are written in {@code item,value} format on subsequent lines. The next
 * user ID follows and so on.</p>
 *
 * <p>Example usage:</p>
 *
 * <p>{@code java -Xmx2g -cp myrrix-serving-x.y.jar net.myrrix.web.AllRecommendations }
 *   --localInputDir=[work dir] --howMany=[# recs] --rescorerProviderClass=[your class]}</p>
 *
 * @author Sean Owen
 */
public final class AllRecommendations {

  private static final String LOCAL_INPUT_DIR_FLAG = "localInputDir";
  private static final String RESCORER_PROVIDER_CLASS_FLAG = "rescorerProviderClass";
  private static final String HOW_MANY_FLAG = "howMany";
  private static final int DEFAULT_HOW_MANY = 10;

  private AllRecommendations() {
  }

  public static void main(String[] args) throws Exception {

    Options options = new Options();

    OptionBuilder.hasArg();
    OptionBuilder.withLongOpt(LOCAL_INPUT_DIR_FLAG);
    OptionBuilder.isRequired();
    options.addOption(OptionBuilder.create());

    OptionBuilder.hasArg();
    OptionBuilder.withLongOpt(RESCORER_PROVIDER_CLASS_FLAG);
    options.addOption(OptionBuilder.create());

    OptionBuilder.hasArg();
    OptionBuilder.withLongOpt(HOW_MANY_FLAG);
    options.addOption(OptionBuilder.create());

    CommandLineParser parser = new PosixParser();
    CommandLine commandLine;
    try {
      commandLine = parser.parse(options, args);
    } catch (MissingOptionException moe) {
      new HelpFormatter().printHelp(AllRecommendations.class.getSimpleName(), options);
      return;
    }

    File localInputDir = new File(commandLine.getOptionValue(LOCAL_INPUT_DIR_FLAG));

    final RescorerProvider rescorerProvider;
    if (commandLine.hasOption(RESCORER_PROVIDER_CLASS_FLAG)) {
      String rescorerProviderClassName = commandLine.getOptionValue(RESCORER_PROVIDER_CLASS_FLAG);
      rescorerProvider = ClassUtils.loadInstanceOf(rescorerProviderClassName, RescorerProvider.class);
    } else {
      rescorerProvider = null;
    }

    final int howMany;
    if (commandLine.hasOption(HOW_MANY_FLAG)) {
      howMany = Integer.parseInt(commandLine.getOptionValue(HOW_MANY_FLAG));
    } else {
      howMany = DEFAULT_HOW_MANY;
    }

    final ServerRecommender recommender = new ServerRecommender(localInputDir);
    while (!recommender.isReady()) {
      Thread.sleep(1000L);
    }

    ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    List<Future<?>> futures = Lists.newArrayList();

    LongPrimitiveIterator it = recommender.getAllUserIDs().iterator();
    while (it.hasNext()) {
      final long userID = it.nextLong();
      futures.add(executorService.submit(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          IDRescorer rescorer =
              rescorerProvider == null ? null : rescorerProvider.getRecommendRescorer(new long[]{userID});
          List<RecommendedItem> recs = recommender.recommend(userID, howMany, rescorer);
          StringBuilder line = new StringBuilder(30);
          synchronized (System.out) {
            System.out.println(Long.toString(userID));
            for (RecommendedItem rec : recs) {
              line.setLength(0);
              line.append(Long.toString(rec.getItemID())).append(',').append(Float.toString(rec.getValue()));
              System.out.println(line);
            }
          }
          return null;
        }
      }));
    }

    executorService.shutdown();
    for (Future<?> future : futures) {
      future.get();
    }

  }

}
