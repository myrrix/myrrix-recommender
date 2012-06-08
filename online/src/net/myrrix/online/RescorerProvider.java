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

import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;

/**
 * <p>Implementations of this interface provide, optionally, objects that can be used to modify and influence
 * the results of {@link ServerRecommender#recommend(long, int)},
 * {@link ServerRecommender#recommendToMany(long[], int, boolean, IDRescorer)} and
 * {@link ServerRecommender#mostSimilarItems(long, int)}. It is a means to inject business logic into
 * the results of {@link ServerRecommender}.</p>
 *
 * <p>Implementations of this class are factories. An implementation creates and configures the
 * rescoring objects and returns them for use by the framework.</p>
 *
 * <p>The factory methods, like {@link #getRecommendRescorer(String...)}, takes an optional argument. These
 * are passed from the REST API, as a {@code String}, from URL parameter {@code rescorerParams}. The
 * implementation may need this information to initialize its rescoring logic. For example, the argument
 * may be the user's current location, used to filter results by location.</p>
 *
 * @author Sean Owen
 */
public interface RescorerProvider {

  /**
   * @param args arguments, if any, that should be used when making the {@link IDRescorer}. This is additional
   *  information from the request that may be necessary to its logic, like current location. What it means
   *  is up to the implementation.
   * @return {@link IDRescorer} to use with {@link ServerRecommender#recommend(long, int, IDRescorer)}
   *  or null if none should be used
   */
  IDRescorer getRecommendRescorer(String... args);

  /**
   * @param args arguments, if any, that should be used when making the {@link IDRescorer}. This is additional
   *  information from the request that may be necessary to its logic, like current location. What it means
   *  is up to the implementation.
   * @return {@link Rescorer} to use with {@link ServerRecommender#mostSimilarItems(long[], int, Rescorer)}
   *  or null if none should be used
   */
  Rescorer<LongPair> getMostSimilarItemsRescorer(String... args);

}
