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

import com.google.common.base.Preconditions;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import net.myrrix.common.ClassUtils;
import net.myrrix.online.RescorerProvider;

/**
 * Encapsulates command line configuration for {@link AllRecommendations} and {@link AllItemSimilarities}.
 *
 * @author Sean Owen
 * @see AllItemSimilarities
 * @see AllRecommendations
 */
public final class AllConfig {

  private static final String LOCAL_INPUT_DIR_FLAG = "localInputDir";
  private static final String RESCORER_PROVIDER_CLASS_FLAG = "rescorerProviderClass";
  private static final String HOW_MANY_FLAG = "howMany";
  private static final int DEFAULT_HOW_MANY = 10;

  private final File localInputDir;
  private final RescorerProvider rescorerProvider;
  private final int howMany;

  public AllConfig(File localInputDir, RescorerProvider rescorerProvider, int howMany) {
    Preconditions.checkNotNull(localInputDir);
    Preconditions.checkArgument(howMany > 0);
    this.localInputDir = localInputDir;
    this.rescorerProvider = rescorerProvider;
    this.howMany = howMany;
  }

  public File getLocalInputDir() {
    return localInputDir;
  }

  public RescorerProvider getRescorerProvider() {
    return rescorerProvider;
  }

  public int getHowMany() {
    return howMany;
  }

  static AllConfig build(String[] args) {
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
    } catch (ParseException pe) {
      System.out.println(pe.getMessage());
      System.out.println();
      new HelpFormatter().printHelp(AllItemSimilarities.class.getSimpleName(), options);
      return null;
    }

    File localInputDir = new File(commandLine.getOptionValue(LOCAL_INPUT_DIR_FLAG));

    RescorerProvider rescorerProvider;
    if (commandLine.hasOption(RESCORER_PROVIDER_CLASS_FLAG)) {
      String rescorerProviderClassName = commandLine.getOptionValue(RESCORER_PROVIDER_CLASS_FLAG);
      rescorerProvider = ClassUtils.loadInstanceOf(rescorerProviderClassName, RescorerProvider.class);
    } else {
      rescorerProvider = null;
    }

    int howMany;
    if (commandLine.hasOption(HOW_MANY_FLAG)) {
      howMany = Integer.parseInt(commandLine.getOptionValue(HOW_MANY_FLAG));
    } else {
      howMany = DEFAULT_HOW_MANY;
    }

    return new AllConfig(localInputDir, rescorerProvider, howMany);
  }

}
