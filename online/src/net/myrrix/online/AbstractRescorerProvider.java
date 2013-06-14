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

package net.myrrix.online;

import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;

import net.myrrix.common.ClassUtils;
import net.myrrix.common.MyrrixRecommender;

/**
 * Abstract implementation of {@link RescorerProvider} which implements all methods to return {@code null}.
 *
 * @author Sean Owen
 * @since 1.0
 */
public abstract class AbstractRescorerProvider implements RescorerProvider {

  private static final Pattern COMMA = Pattern.compile(",");

  /**
   * @return {@code null}
   */
  @Override
  public IDRescorer getRecommendRescorer(long[] userIDs, MyrrixRecommender recommender, String... args) {
    return null;
  }

  /**
   * @return {@code null}
   */
  @Override
  public IDRescorer getRecommendToAnonymousRescorer(long[] itemIDs, MyrrixRecommender recommender, String... args) {
    return null;
  }

  /**
   * @return {@code null}
   */
  @Override
  public IDRescorer getMostPopularItemsRescorer(MyrrixRecommender recommender, String... args) {
    return null;
  }

  /**
   * @return {@code null}
   */
  @Override
  public Rescorer<LongPair> getMostSimilarItemsRescorer(MyrrixRecommender recommender, String... args) {
    return null;
  }
  
  public static RescorerProvider loadRescorerProviders(String classNamesString, URL url) {
    if (classNamesString == null || classNamesString.isEmpty()) {
      return null;
    }
    String[] classNames = COMMA.split(classNamesString);
    if (classNames.length == 1) {
      return loadOneRescorerProvider(classNames[0], url);
    } 
    List<RescorerProvider> providers = Lists.newArrayListWithCapacity(classNames.length);
    for (String className : classNames) {
      providers.add(loadOneRescorerProvider(className, url));
    }
    return new MultiRescorerProvider(providers);
  }
  
  private static RescorerProvider loadOneRescorerProvider(String className, URL url) {
    if (url == null) {
      return ClassUtils.loadInstanceOf(className, RescorerProvider.class);
    }
    return ClassUtils.loadFromRemote(className, RescorerProvider.class, url);
  }

}
