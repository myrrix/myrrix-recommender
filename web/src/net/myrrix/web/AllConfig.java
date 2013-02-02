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
import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;

import net.myrrix.online.AbstractRescorerProvider;
import net.myrrix.online.RescorerProvider;

/**
 * Encapsulates configuration for {@link AllRecommendations} and {@link AllItemSimilarities}.
 *
 * @author Sean Owen
 * @see AllItemSimilarities
 * @see AllRecommendations
 */
public final class AllConfig {

  private final File localInputDir;
  private final RescorerProvider rescorerProvider;
  private final int howMany;

  public AllConfig(File localInputDir, RescorerProvider rescorerProvider, int howMany) {
    Preconditions.checkNotNull(localInputDir);
    Preconditions.checkArgument(howMany > 0, "howMany must be positive: %s", howMany);
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

    AllUtilityArgs allArgs;
    try {
      allArgs = CliFactory.parseArguments(AllUtilityArgs.class, args);
    } catch (ArgumentValidationException ave) {
      System.out.println();
      System.out.println(ave.getMessage());
      System.out.println();
      return null;
    }

    String rescorerProviderClassNames = allArgs.getRescorerProviderClass();
    RescorerProvider rescorerProvider;
    if (rescorerProviderClassNames == null) {
      rescorerProvider = null;
    } else {
      rescorerProvider = AbstractRescorerProvider.loadRescorerProviders(rescorerProviderClassNames, null);
    }

    return new AllConfig(allArgs.getLocalInputDir(), rescorerProvider, allArgs.getHowMany());
  }

}
