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

package net.myrrix.client;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.model.MemoryIDMigrator;
import org.apache.mahout.cf.taste.model.IDMigrator;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.client.translating.OneWayMigrator;
import net.myrrix.client.translating.TranslatedRecommendedItem;
import net.myrrix.client.translating.TranslatingClientRecommender;
import net.myrrix.client.translating.TranslatingRecommender;
import net.myrrix.common.LangUtils;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.log.MemoryHandler;

/**
 * <p>A basic command-line interface to the Java client. It is run like so:</p>
 *
 * <p>{@code java -jar myrrix-client-X.Y.jar [options] command [arg0 arg1 ...]}</p>
 *
 * <p>"options" are flags, most of which configure an instance of {@link MyrrixClientConfiguration}:</p>
 *
 * <ul>
 *   <li>{@code --host}: sets {@link MyrrixClientConfiguration#getHost()}</li>
 *   <li>{@code --port}: sets {@link MyrrixClientConfiguration#getPort()}</li>
 *   <li>{@code --secure}: sets {@link MyrrixClientConfiguration#isSecure()}</li>
 *   <li>{@code --contextPath}: sets {@link MyrrixClientConfiguration#getContextPath()}</li>
 *   <li>{@code --userName}: sets {@link MyrrixClientConfiguration#setUserName(String)}</li>
 *   <li>{@code --password}: sets {@link MyrrixClientConfiguration#setPassword(String)}</li>
 *   <li>{@code --keystoreFile}: sets {@link MyrrixClientConfiguration#setKeystoreFile(File)}</li>
 *   <li>{@code --keystorePassword}: sets {@link MyrrixClientConfiguration#setKeystorePassword(String)}</li>
 *   <li>{@code --allPartitions}: sets {@link MyrrixClientConfiguration#setAllPartitionsSpecification(String)}</li>
 * </ul>
 *
 * <p>"options" may include a few additional flags:</p>
 *
 * <ul>
 *   <li>{@code --translateItem [file]}: Sets the client to use String item IDs instead of numeric, and to translate
 *   to/from numeric item IDs when contacting the server. This may be used to avoid sending sensitive IDs to
 *   an external server, while still using them locally for convenience. The optional file argument names
 *   a file containing all known item IDs. This is needed so that the client can reverse translate any
 *   value from the server.</li>
 *   <li>{@code --translateUser}: Same as above, but controls translating user IDs. Since they need never be
 *   translated back, no list of values is required.</li>
 *   <li>{@code --verbose}: log more messages to standard out</li>
 * </ul>
 *
 * <p>"command" may be any value of {@link CLICommand}, in lower case if you like; "estimatePreference" and
 * "recommend" are valid values for example. These correspond to the methods of
 * {@link net.myrrix.common.MyrrixRecommender}</p>
 *
 * <p>The remaining arguments are arguments to the method named by command, and are likewise analogous to the
 * method arguments seen in {@link net.myrrix.common.MyrrixRecommender}:</p>
 *
 * <ul>
 *   <li>{@code setPreference userID itemID [value]}</li>
 *   <li>{@code removePreference userID itemID}</li>
 *   <li>{@code ingest csvFile [csvFile2 ...]}</li>
 *   <li>{@code estimatePreference userID itemID}</li>
 *   <li>{@code recommend userID howMany [considerKnownItems]}</li>
 *   <li>{@code recommendToAnonymous itemID0 [itemID1 itemID2 ...] howMany}</li>
 *   <li>{@code mostSimilarItems itemID0 [itemID1 itemID2 ...] howMany}</li>
 *   <li>{@code recommendedBecause userID itemID howMany}</li>
 *   <li>{@code refresh}</li>
 *   <li>{@code isReady}</li>
 *   <li>{@code getAllUserIDs}</li>
 *   <li>{@code getAllItemIDs}</li>
 * </ul>
 *
 * <p>Methods that return {@code void} in {@link net.myrrix.common.MyrrixRecommender} produce no output. Methods
 * like {@link net.myrrix.common.MyrrixRecommender#estimatePreference(long, long)} that return a single value
 * have this written to a single line of output. Methods like
 * {@link net.myrrix.common.MyrrixRecommender#recommend(long, int)} that return a series of values are output in
 * CSV format.</p>
 *
 * <p>For example, to make 3 recommendations for user 35, one might run:</p>
 *
 * <p>{@code java -jar myrrix-client-X.Y.jar --host=example.com --port=8080 recommend 35 3}</p>
 *
 * <p>... and output might be:</p>
 *
 * <p>{@code
 * 352352,0.559
 * 9898,0.4034
 * 209,0.03339
 * }</p>
 *
 * <p>If using string IDs, it might look more like:</p>
 *
 * <p>{@code java -jar myrrix-client-X.Y.jar --host=example.com --port=8080
 *   --translateUser --translateItem ids.txt recommend Jane 3}</p>
 *
 * <p>... and output might be:</p>
 *
 * <p>{@code
 * Apple,0.559
 * Orange,0.4034
 * Banana,0.03339
 * }</p>
 *
 * @author Sean Owen
 */
public final class CLI {

  private CLI() {
  }

  public static void main(String[] args) throws Exception {

    CLIArgs cliArgs;
    try {
      cliArgs = CliFactory.parseArguments(CLIArgs.class, args);
    } catch (ArgumentValidationException ave) {
      printHelp(ave.getMessage());
      return;
    }

    List<String> programArgsList = cliArgs.getCommands();
    if (programArgsList == null || programArgsList.isEmpty()) {
      printHelp("No command specified");
      return;
    }
    String[] programArgs = programArgsList.toArray(new String[programArgsList.size()]);

    CLICommand command;
    try {
      command = CLICommand.valueOf(programArgs[0].toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException iae) {
      printHelp(iae.getMessage());
      return;
    }

    if (cliArgs.isVerbose()) {
      MemoryHandler.setSensibleLogFormat();
      enableDebugLoggingIn(CLI.class, ClientRecommender.class, TranslatingClientRecommender.class);
    }

    MyrrixClientConfiguration config = buildConfiguration(cliArgs);
    ClientRecommender recommender = new ClientRecommender(config);

    boolean translateUser = cliArgs.isTranslateUser();
    String translateFileName = cliArgs.getTranslateItem();
    boolean translateItem = translateFileName != null;

    TranslatingRecommender translatingRecommender = null;
    if (translateUser || translateItem) {
      IDMigrator userTranslator = translateUser ? new OneWayMigrator() : null;
      MemoryIDMigrator itemTranslator = translateItem ? new MemoryIDMigrator() : null;
      translatingRecommender = new TranslatingClientRecommender(recommender, userTranslator, itemTranslator);
      if (translateFileName != null) {
        File translateFile = new File(translateFileName);
        translatingRecommender.addItemIDs(translateFile);
      }
    }

    try {
      switch (command) {
        case SETPREFERENCE:
          doSetPreference(programArgs, recommender, translatingRecommender);
          break;
        case REMOVEPREFERENCE:
          doRemovePreference(programArgs, recommender, translatingRecommender);
          break;
        case INGEST:
          doIngest(programArgs, recommender, translatingRecommender);
          break;
        case ESTIMATEPREFERENCE:
          doEstimatePreference(programArgs, recommender, translatingRecommender);
          break;
        case RECOMMEND:
          doRecommend(programArgs, recommender, translatingRecommender);
          break;
        case RECOMMENDTOANONYMOUS:
          doRecommendToAnonymous(programArgs, recommender, translatingRecommender);
          break;
        case MOSTSIMILARITEMS:
          doMostSimilarItems(programArgs, recommender, translatingRecommender);
          break;
        case RECOMMENDEDBECAUSE:
          doRecommendedBecause(programArgs, recommender, translatingRecommender);
          break;
        case REFRESH:
          doRefresh(programArgs, recommender);
          break;
        case ISREADY:
          doIsReady(programArgs, recommender);
          break;
        case GETALLUSERIDS:
          doGetAllIDs(programArgs, recommender, translatingRecommender, true);
          break;
        case GETALLITEMIDS:
          doGetAllIDs(programArgs, recommender, translatingRecommender, false);
          break;
      }
    } catch (ArgumentValidationException ave) {
      printHelp(ave.getMessage());
    }
  }

  private static void doGetAllIDs(String[] programArgs,
                                  ClientRecommender recommender,
                                  TranslatingRecommender translatingRecommender,
                                  boolean isUser) throws TasteException {
    if (programArgs.length != 1) {
      throw new ArgumentValidationException("no arguments");
    }
    if (translatingRecommender == null) {
      FastIDSet ids = isUser ? recommender.getAllUserIDs() : recommender.getAllItemIDs();
      LongPrimitiveIterator it = ids.iterator();
      while (it.hasNext()) {
        System.out.println(Long.toString(it.nextLong()));
      }
    } else {
      if (isUser) {
        throw new UnsupportedOperationException();
      }
      Collection<String> ids = translatingRecommender.getAllItemIDs();
      for (String id : ids) {
        System.out.println(id);
      }
    }
  }

  private static void doIsReady(String[] programArgs, ClientRecommender recommender) throws TasteException {
    if (programArgs.length != 1) {
      throw new ArgumentValidationException("no arguments");
    }
    System.out.println(recommender.isReady());
  }

  private static void doRefresh(String[] programArgs, ClientRecommender recommender) {
    if (programArgs.length != 1) {
      throw new ArgumentValidationException("no arguments");
    }
    recommender.refresh(null);
  }

  private static void doRecommendedBecause(String[] programArgs,
                                           ClientRecommender recommender,
                                           TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 4) {
      throw new ArgumentValidationException("args are userID itemID howMany");
    }
    int howMany = Integer.parseInt(programArgs[3]);
    if (translatingRecommender == null) {
      long userID = Long.parseLong(unquote(programArgs[1]));
      long itemID = Long.parseLong(unquote(programArgs[2]));
      output(recommender.recommendedBecause(userID, itemID, howMany));
    } else {
      String userID = unquote(programArgs[1]);
      String itemID = unquote(programArgs[2]);
      outputTranslated(translatingRecommender.recommendedBecause(userID, itemID, howMany));
    }
  }

  private static void doMostSimilarItems(String[] programArgs,
                                         ClientRecommender recommender,
                                         TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length < 3) {
      throw new ArgumentValidationException("args are itemID1 [itemID2 [itemID3...]] howMany");
    }
    int howMany = Integer.parseInt(programArgs[programArgs.length - 1]);
    if (translatingRecommender == null) {
      long[] itemIDs = new long[programArgs.length - 2];
      for (int i = 1; i < programArgs.length - 1; i++) {
        itemIDs[i - 1] = Long.parseLong(unquote(programArgs[i]));
      }
      output(recommender.mostSimilarItems(itemIDs, howMany));
    } else {
      String[] itemIDs = new String[programArgs.length - 2];
      for (int i = 1; i < programArgs.length - 1; i++) {
        itemIDs[i - 1] = unquote(programArgs[i]);
      }
      outputTranslated(translatingRecommender.mostSimilarItems(itemIDs, howMany));
    }
  }

  private static void doRecommendToAnonymous(String[] programArgs,
                                             ClientRecommender recommender,
                                             TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length < 3) {
      throw new ArgumentValidationException("args are itemID1 [itemID2 [itemID3...]] howMany");
    }
    int howMany = Integer.parseInt(programArgs[programArgs.length - 1]);
    if (translatingRecommender == null) {
      long[] itemIDs = new long[programArgs.length - 2];
      for (int i = 1; i < programArgs.length - 1; i++) {
        itemIDs[i - 1] = Long.parseLong(unquote(programArgs[i]));
      }
      output(recommender.recommendToAnonymous(itemIDs, howMany));
    } else {
      String[] itemIDs = new String[programArgs.length - 2];
      for (int i = 1; i < programArgs.length - 1; i++) {
        itemIDs[i - 1] = unquote(programArgs[i]);
      }
      outputTranslated(translatingRecommender.recommendToAnonymous(itemIDs, howMany));
    }
  }

  private static void doRecommend(String[] programArgs,
                                  ClientRecommender recommender,
                                  TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 3 && programArgs.length != 4) {
      throw new ArgumentValidationException("args are userID howMany [considerKnownItems]");
    }
    int howMany = Integer.parseInt(programArgs[2]);
    boolean considerKnownItems = programArgs.length == 4 && Boolean.valueOf(programArgs[3]);
    if (translatingRecommender == null) {
      long userID = Long.parseLong(unquote(programArgs[1]));
      output(recommender.recommend(userID, howMany, considerKnownItems, null));
    } else {
      String userID = unquote(programArgs[1]);
      outputTranslated(translatingRecommender.recommend(userID, howMany, considerKnownItems));
    }
  }

  private static void doEstimatePreference(String[] programArgs,
                                           ClientRecommender recommender,
                                           TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 3) {
      throw new ArgumentValidationException("args are userID itemID");
    }
    float estimate;
    if (translatingRecommender == null) {
      long userID = Long.parseLong(unquote(programArgs[1]));
      long itemID = Long.parseLong(unquote(programArgs[2]));
      estimate = recommender.estimatePreference(userID, itemID);
    } else {
      String userID = unquote(programArgs[1]);
      String itemID = unquote(programArgs[2]);
      estimate = translatingRecommender.estimatePreference(userID, itemID);
    }
    System.out.println(estimate);
  }

  private static void doIngest(String[] programArgs,
                               ClientRecommender recommender,
                               TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length < 2) {
      throw new ArgumentValidationException("args are file1 [file2 [file3...]]");
    }
    for (int i = 1; i < programArgs.length; i++) {
      File ingestFile = new File(programArgs[i]);
      if (translatingRecommender == null) {
        recommender.ingest(ingestFile);
      } else {
        translatingRecommender.ingest(ingestFile);
      }
    }
  }

  private static void doRemovePreference(String[] programArgs,
                                         ClientRecommender recommender,
                                         TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 3) {
      throw new ArgumentValidationException("args are userID itemID");
    }
    if (translatingRecommender == null) {
      long userID = Long.parseLong(unquote(programArgs[1]));
      long itemID = Long.parseLong(unquote(programArgs[2]));
      recommender.removePreference(userID, itemID);
    } else {
      String userID = unquote(programArgs[1]);
      String itemID = unquote(programArgs[2]);
      translatingRecommender.removePreference(userID, itemID);
    }
  }

  private static void doSetPreference(String[] programArgs,
                                      ClientRecommender recommender,
                                      TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 3 && programArgs.length != 4) {
      throw new ArgumentValidationException("args are userID itemID [value]");
    }
    if (translatingRecommender == null) {
      long userID = Long.parseLong(unquote(programArgs[1]));
      long itemID = Long.parseLong(unquote(programArgs[2]));
      if (programArgs.length == 3) {
        recommender.setPreference(userID, itemID);
      } else {
        float value = LangUtils.parseFloat(unquote(programArgs[3]));
        recommender.setPreference(userID, itemID, value);
      }
    } else {
      String userID = unquote(programArgs[1]);
      String itemID = unquote(programArgs[2]);
      if (programArgs.length == 3) {
        translatingRecommender.setPreference(userID, itemID);
      } else {
        float value = LangUtils.parseFloat(unquote(programArgs[3]));
        translatingRecommender.setPreference(userID, itemID, value);
      }
    }
  }

  /**
   * Unquotes a string. Makes it possible to pass negative values without being interpreted as a flag.
   */
  private static String unquote(String s) {
    int length = s.length();
    if (length >= 2 && s.charAt(0) =='"' && s.charAt(length - 1) == '"') {
      return s.substring(1, length - 1);
    }
    return s;
  }

  private static void printHelp(String message) {
    System.out.println();
    System.out.println("Myrrix Client command line interface. Copyright Myrrix Ltd, except for included ");
    System.out.println("third-party open source software. Full details of licensing at http://myrrix.com/legal/");
    System.out.println();
    if (message != null) {
      System.out.println(message);
      System.out.println();
    }
  }

  private static MyrrixClientConfiguration buildConfiguration(CLIArgs cliArgs) {
    MyrrixClientConfiguration config = new MyrrixClientConfiguration();
    config.setHost(cliArgs.getHost());
    config.setPort(cliArgs.getPort());
    config.setSecure(cliArgs.isSecure());
    config.setContextPath(cliArgs.getContextPath());
    config.setAllPartitionsSpecification(cliArgs.getAllPartitions());
    config.setUserName(cliArgs.getUserName());
    config.setPassword(cliArgs.getPassword());
    config.setKeystoreFile(cliArgs.getKeystoreFile());
    config.setKeystorePassword(cliArgs.getKeystorePassword());
    return config;
  }

  private static void output(Iterable<RecommendedItem> items) {
    for (RecommendedItem item : items) {
      System.out.println(item.getItemID() + "," + item.getValue());
    }
  }

  private static void outputTranslated(Iterable<TranslatedRecommendedItem> items) {
    for (TranslatedRecommendedItem item : items) {
      System.out.println(item.getItemID() + ',' + item.getValue());
    }
  }

  private static void enableDebugLoggingIn(Class<?>... classes) {
    for (Class<?> c : classes) {
      java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(c.getName());
      julLogger.setLevel(java.util.logging.Level.FINE);
      while (julLogger != null) {
        for (java.util.logging.Handler handler : julLogger.getHandlers()) {
          handler.setLevel(java.util.logging.Level.FINE);
        }
        julLogger = julLogger.getParent();
      }
    }
  }

}
