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
import java.util.Locale;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.model.MemoryIDMigrator;
import org.apache.mahout.cf.taste.model.IDMigrator;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.client.translating.OneWayMigrator;
import net.myrrix.client.translating.TranslatedRecommendedItem;
import net.myrrix.client.translating.TranslatingClientRecommender;
import net.myrrix.client.translating.TranslatingRecommender;
import net.myrrix.common.LangUtils;

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
 *   <li>{@code --userName}: sets {@link MyrrixClientConfiguration#setUserName(String)}</li>
 *   <li>{@code --password}: sets {@link MyrrixClientConfiguration#setPassword(String)}</li>
 *   <li>{@code --keystoreFile}: sets {@link MyrrixClientConfiguration#setKeystoreFile(java.io.File)}</li>
 *   <li>{@code --keystorePassword}: sets {@link MyrrixClientConfiguration#setKeystorePassword(String)}</li>
 *   <li>{@code --allPartitions}: sets {@link MyrrixClientConfiguration#setAllPartitionsSpecification(String)}</li>
 * </ul>
 *
 * <p>"options" may include one additional flag:</p>
 *
 * <ul>
 *   <li>{@code --translateItem [file]}: Sets the client to use String item IDs instead of numeric, and to translate
 *   to/from numeric item IDs when contacting the server. This may be used to avoid sending sensitive IDs to
 *   an external server, while still using them locally for convenience. The optional file argument names
 *   a file containing all known item IDs. This is needed so that the client can reverse translate any
 *   value from the server.</li>
 *   <li>{@code --translateUser}: Same as above, but controls translating user IDs. Since they need never be
 *   translated back, no list of values is required.</li>
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
 *   <li>{@code ingest csvFile}</li>
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

  private static final String TRANSLATE_FLAG = "translate";
  private static final String TRANSLATE_USER_FLAG = "translateUser";
  private static final String TRANSLATE_ITEM_FLAG = "translateItem";
  private static final String HOST_FLAG = "host";
  private static final String ALL_PARTITIONS_FLAG = "allPartitions";
  private static final String PORT_FLAG = "port";
  private static final String SECURE_FLAG = "secure";
  private static final String USER_NAME_FLAG = "userName";
  private static final String PASSWORD_FLAG = "password";
  private static final String KEYSTORE_FILE_FLAG = "keystoreFile";
  private static final String KEYSTORE_PASSWORD_FLAG = "keystorePassword";

  private CLI() {
  }

  public static void main(String[] args) throws Exception {

    Options options = buildOptions();
    CommandLineParser parser = new PosixParser();
    CommandLine commandLine;
    try {
      commandLine = parser.parse(options, args);
    } catch (MissingOptionException moe) {
      printHelp(options);
      return;
    }
    String[] programArgs = commandLine.getArgs();

    if (programArgs.length == 0) {
      printHelp(options);
      return;
    }

    CLICommand command;
    try {
      command = CLICommand.valueOf(programArgs[0].toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException iae) {
      printHelp(options);
      return;
    }

    MyrrixClientConfiguration config = buildConfiguration(commandLine);
    ClientRecommender recommender = new ClientRecommender(config);

    boolean translateUser = commandLine.hasOption(TRANSLATE_USER_FLAG);
    boolean translateItem = commandLine.hasOption(TRANSLATE_ITEM_FLAG);
    if (commandLine.hasOption(TRANSLATE_FLAG)) {
      System.err.println("--" + TRANSLATE_FLAG + " is deprecated. Use --" + TRANSLATE_USER_FLAG +
                         " or --" + TRANSLATE_ITEM_FLAG);
      translateUser = true;
      translateItem = true;
    }

    TranslatingRecommender translatingRecommender = null;
    if (translateUser || translateItem) {
      IDMigrator userTranslator = translateUser ? new OneWayMigrator() : null;
      MemoryIDMigrator itemTranslator = translateItem ? new MemoryIDMigrator() : null;
      translatingRecommender = new TranslatingClientRecommender(recommender, userTranslator, itemTranslator);
      String translateFileName = commandLine.getOptionValue(TRANSLATE_ITEM_FLAG);
      if (translateFileName == null) {
        translateFileName = commandLine.getOptionValue(TRANSLATE_FLAG);
      }
      if (translateFileName != null) {
        File translateFile = new File(translateFileName);
        translatingRecommender.addItemIDs(translateFile);
      }
    }

    boolean success = false;
    switch (command) {
      case SETPREFERENCE:
        success = doSetPreference(programArgs, recommender, translatingRecommender);
        break;
      case REMOVEPREFERENCE:
        success = doRemovePreference(programArgs, recommender, translatingRecommender);
        break;
      case INGEST:
        success = doIngest(programArgs, recommender, translatingRecommender);
        break;
      case ESTIMATEPREFERENCE:
        success = doEstimatePreference(programArgs, recommender, translatingRecommender);
        break;
      case RECOMMEND:
        success = doRecommend(programArgs, recommender, translatingRecommender);
        break;
      case RECOMMENDTOANONYMOUS:
        success = doRecommendToAnonymous(programArgs, recommender, translatingRecommender);
        break;
      case MOSTSIMILARITEMS:
        success = doMostSimilarItems(programArgs, recommender, translatingRecommender);
        break;
      case RECOMMENDEDBECAUSE:
        success = doRecommendedBecause(programArgs, recommender, translatingRecommender);
        break;
      case REFRESH:
        success = doRefresh(programArgs, recommender);
        break;
      case ISREADY:
        success = doIsReady(programArgs, recommender);
        break;
      case GETALLUSERIDS:
        success = doGetAllIDs(programArgs, recommender, translatingRecommender, true);
        break;
      case GETALLITEMIDS:
        success = doGetAllIDs(programArgs, recommender, translatingRecommender, false);
        break;
    }

    if (!success) {
      printHelp(options);
    }

  }

  private static boolean doGetAllIDs(String[] programArgs,
                                     ClientRecommender recommender,
                                     TranslatingRecommender translatingRecommender,
                                     boolean isUser) throws TasteException {
    if (programArgs.length != 1) {
      return false;
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
    return true;
  }

  private static boolean doIsReady(String[] programArgs, ClientRecommender recommender) throws TasteException {
    if (programArgs.length != 1) {
      return false;
    }
    System.out.println(recommender.isReady());
    return true;
  }

  private static boolean doRefresh(String[] programArgs, ClientRecommender recommender) {
    if (programArgs.length != 1) {
      return false;
    }
    recommender.refresh(null);
    return true;
  }

  private static boolean doRecommendedBecause(String[] programArgs,
                                              ClientRecommender recommender,
                                              TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 4) {
      return false;
    }
    int howMany = Integer.parseInt(programArgs[3]);
    if (translatingRecommender == null) {
      long userID = Long.parseLong(programArgs[1]);
      long itemID = Long.parseLong(programArgs[2]);
      output(recommender.recommendedBecause(userID, itemID, howMany));
    } else {
      String userID = programArgs[1];
      String itemID = programArgs[2];
      outputTranslated(translatingRecommender.recommendedBecause(userID, itemID, howMany));
    }
    return true;
  }

  private static boolean doMostSimilarItems(String[] programArgs,
                                            ClientRecommender recommender,
                                            TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length < 3) {
      return false;
    }
    int howMany = Integer.parseInt(programArgs[programArgs.length - 1]);
    if (translatingRecommender == null) {
      long[] itemIDs = new long[programArgs.length - 2];
      for (int i = 1; i < programArgs.length - 1; i++) {
        itemIDs[i - 1] = Long.parseLong(programArgs[i]);
      }
      output(recommender.mostSimilarItems(itemIDs, howMany));
    } else {
      String[] itemIDs = new String[programArgs.length - 2];
      System.arraycopy(programArgs, 1, itemIDs, 0, programArgs.length - 2);
      outputTranslated(translatingRecommender.mostSimilarItems(itemIDs, howMany));
    }
    return true;
  }

  private static boolean doRecommendToAnonymous(String[] programArgs,
                                                ClientRecommender recommender,
                                                TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length < 3) {
      return false;
    }
    int howMany = Integer.parseInt(programArgs[programArgs.length - 1]);
    if (translatingRecommender == null) {
      long[] itemIDs = new long[programArgs.length - 2];
      for (int i = 1; i < programArgs.length - 1; i++) {
        itemIDs[i - 1] = Long.parseLong(programArgs[i]);
      }
      output(recommender.recommendToAnonymous(itemIDs, howMany));
    } else {
      String[] itemIDs = new String[programArgs.length - 2];
      System.arraycopy(programArgs, 1, itemIDs, 0, programArgs.length - 2);
      outputTranslated(translatingRecommender.recommendToAnonymous(itemIDs, howMany));
    }
    return true;
  }

  private static boolean doRecommend(String[] programArgs,
                                     ClientRecommender recommender,
                                     TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 3 && programArgs.length != 4) {
      return false;
    }
    int howMany = Integer.parseInt(programArgs[2]);
    boolean considerKnownItems = programArgs.length == 4 && Boolean.valueOf(programArgs[3]);
    if (translatingRecommender == null) {
      long userID = Long.parseLong(programArgs[1]);
      output(recommender.recommend(userID, howMany, considerKnownItems, null));
    } else {
      String userID = programArgs[1];
      outputTranslated(translatingRecommender.recommend(userID, howMany, considerKnownItems));
    }
    return true;
  }

  private static boolean doEstimatePreference(String[] programArgs,
                                              ClientRecommender recommender,
                                              TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 3) {
      return false;
    }
    float estimate;
    if (translatingRecommender == null) {
      long userID = Long.parseLong(programArgs[1]);
      long itemID = Long.parseLong(programArgs[2]);
      estimate = recommender.estimatePreference(userID, itemID);
    } else {
      String userID = programArgs[1];
      String itemID = programArgs[2];
      estimate = translatingRecommender.estimatePreference(userID, itemID);
    }
    System.out.println(estimate);
    return true;
  }

  private static boolean doIngest(String[] programArgs,
                                  ClientRecommender recommender,
                                  TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 2) {
      return false;
    }
    File ingestFile = new File(programArgs[1]);
    if (translatingRecommender == null) {
      recommender.ingest(ingestFile);
    } else {
      translatingRecommender.ingest(ingestFile);
    }
    return true;
  }

  private static boolean doRemovePreference(String[] programArgs,
                                            ClientRecommender recommender,
                                            TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 3) {
      return false;
    }
    if (translatingRecommender == null) {
      long userID = Long.parseLong(programArgs[1]);
      long itemID = Long.parseLong(programArgs[2]);
      recommender.removePreference(userID, itemID);
    } else {
      String userID = programArgs[1];
      String itemID = programArgs[2];
      translatingRecommender.removePreference(userID, itemID);
    }
    return true;
  }

  private static boolean doSetPreference(String[] programArgs,
                                         ClientRecommender recommender,
                                         TranslatingRecommender translatingRecommender) throws TasteException {
    if (programArgs.length != 3 && programArgs.length != 4) {
      return false;
    }
    if (translatingRecommender == null) {
      long userID = Long.parseLong(programArgs[1]);
      long itemID = Long.parseLong(programArgs[2]);
      if (programArgs.length == 3) {
        recommender.setPreference(userID, itemID);
      } else {
        float value = LangUtils.parseFloat(programArgs[3]);
        recommender.setPreference(userID, itemID, value);
      }
    } else {
      String userID = programArgs[1];
      String itemID = programArgs[2];
      if (programArgs.length == 3) {
        translatingRecommender.setPreference(userID, itemID);
      } else {
        float value = LangUtils.parseFloat(programArgs[3]);
        translatingRecommender.setPreference(userID, itemID, value);
      }
    }
    return true;
  }

  private static void printHelp(Options options) {
    System.out.println("Myrrix Client command line interface. Copyright 2012 Myrrix Ltd, except for included ");
    System.out.println("third-party open source software. Full details of licensing at http://myrrix.com/legal/");
    System.out.println();
    new HelpFormatter().printHelp(CLI.class.getSimpleName() + " [flags] command [arg0 arg1 ...]", options);
  }

  private static MyrrixClientConfiguration buildConfiguration(CommandLine commandLine) throws ParseException {
    MyrrixClientConfiguration config = new MyrrixClientConfiguration();

    boolean hasHost = commandLine.hasOption(HOST_FLAG);
    boolean hasAllPartitions = commandLine.hasOption(ALL_PARTITIONS_FLAG);
    if (hasHost == hasAllPartitions) {
      throw new ParseException("Only one of --host and --allPartitions may be set");
    }

    if (hasHost) {
      config.setHost(commandLine.getOptionValue(HOST_FLAG));
    }

    if (commandLine.hasOption(PORT_FLAG)) {
      config.setPort(Integer.parseInt(commandLine.getOptionValue(PORT_FLAG)));
    }

    if (hasAllPartitions) {
      config.setAllPartitionsSpecification(commandLine.getOptionValue(ALL_PARTITIONS_FLAG));
    }

    if (commandLine.hasOption(SECURE_FLAG)) {
      config.setSecure(true);
    }

    if (commandLine.hasOption(USER_NAME_FLAG)) {
      config.setUserName(commandLine.getOptionValue(USER_NAME_FLAG));
    }
    if (commandLine.hasOption(PASSWORD_FLAG)) {
      config.setPassword(commandLine.getOptionValue(PASSWORD_FLAG));
    }

    if (commandLine.hasOption(KEYSTORE_FILE_FLAG)) {
      config.setKeystoreFile(new File(commandLine.getOptionValue(KEYSTORE_FILE_FLAG)));
    }
    if (commandLine.hasOption(KEYSTORE_PASSWORD_FLAG)) {
      config.setKeystorePassword(commandLine.getOptionValue(KEYSTORE_PASSWORD_FLAG));
    }

    return config;
  }

  private static Options buildOptions() {
    Options options = new Options();
    addOption(options, "Serving Layer host name", HOST_FLAG, true, false);
    addOption(options, "Serving Layer port number", PORT_FLAG, true, false);
    addOption(options, "If set, use HTTPS instead of HTTP", SECURE_FLAG, false, true);
    addOption(options, "User name to authenticate to Serving Layer", USER_NAME_FLAG, true, false);
    addOption(options, "Password to authenticate to Serving Layer", PASSWORD_FLAG, true, false);
    addOption(options, "Test SSL certificate keystore to accept", KEYSTORE_FILE_FLAG, true, false);
    addOption(options, "Password for keystoreFile", KEYSTORE_PASSWORD_FLAG, true, false);
    // TODO remove:
    addOption(options, "Deprecated. Use --" + TRANSLATE_USER_FLAG + " and --" + TRANSLATE_ITEM_FLAG,
              TRANSLATE_FLAG, true, true);
    addOption(options, "Use String user IDs in client API.", TRANSLATE_USER_FLAG, false, true);
    addOption(options, "Use String item IDs in client API. Optional file argument contains list of all known item IDs",
              TRANSLATE_ITEM_FLAG, true, true);
    addOption(options, "All partitions, as comma-separated host:port (e.g. foo1:8080,foo2:80,bar1:8081)",
              ALL_PARTITIONS_FLAG, true, false);
    return options;
  }

  private static void addOption(Options options,
                                String description,
                                String longOpt,
                                boolean hasArg,
                                boolean argOptional) {
    if (hasArg) {
      if (argOptional) {
        OptionBuilder.hasOptionalArg();
      } else {
        OptionBuilder.hasArg();
      }
    }
    OptionBuilder.withDescription(description);
    OptionBuilder.withLongOpt(longOpt);
    options.addOption(OptionBuilder.create());
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

}
