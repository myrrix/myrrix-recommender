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

import com.lexicalscope.jewel.cli.Option;

/**
 * Arguments for {@link AllItemSimilarities} and {@link AllRecommendations}.
 *
 * @author Sean Owen
 * @since 1.0
 */
public interface AllUtilityArgs {

  @Option(description = "Working directory for input and intermediate files")
  File getLocalInputDir();

  @Option(defaultToNull = true, description = "RescorerProvider implementation class")
  String getRescorerProviderClass();

  @Option(defaultValue = "10", minimum = 1, description = "How many similarities, recommendations to compute")
  int getHowMany();

  @Option(helpRequest = true)
  boolean getHelp();

  @Option(defaultValue = "true", description = "Whether to compute results in parallel")  
  boolean isParallel();

  @Option(description = "Output file")  
  File getOutFile();

}
