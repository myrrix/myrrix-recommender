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
 * the results of:</p>
 *
 * <ul>
 *  <li>{@link ServerRecommender#recommend(long, int)}</li>
 *  <li>{@link ServerRecommender#recommendToMany(long[], int, boolean, IDRescorer)}</li>
 *  <li>{@link ServerRecommender#recommendToAnonymous(long[], int, IDRescorer)}</li>
 *  <li>{@link ServerRecommender#mostSimilarItems(long, int, Rescorer)}</li>
 * </ul>
 *
 * <p>It is a means to inject business logic into the results of {@link ServerRecommender}.</p>
 *
 * <p>Implementations of this class are factories. An implementation creates and configures an {@link IDRescorer}
 * rescoring object and returns it for use in the context of one
 * {@link ServerRecommender#recommend(long, int, IDRescorer)} method call. (A {@code Rescorer&lt;LongPair&gt;}
 * is used for {@link ServerRecommender#mostSimilarItems(long, int, Rescorer)} since it operates on item ID
 * <em>pairs</em>, but is otherwise analogous.) The {@link IDRescorer} then filters the candidates
 * recommendations or most similar items by item ID ({@link IDRescorer#isFiltered(long)})
 * or modifies the scores of item candidates that are not filtered ({@link IDRescorer#rescore(long, double)})
 * based on the item ID and original score.</p>
 *
 * <p>The factory methods, like {@link #getRecommendRescorer(long[], String...)}, take optional
 * {@code String} arguments. These are passed from the REST API, as a {@code String}, from URL parameter
 * {@code rescorerParams}. The implementation may need this information to initialize its rescoring
 * logic for the request.  For example, the argument may be the user's current location, used to filter
 * results by location.</p>
 *
 * @author Sean Owen
 */
public interface RescorerProvider {

  /**
   * @param userIDs user(s) for which recommendations are being made, which may be needed in the rescoring logic.
   * @param args arguments, if any, that should be used when making the {@link IDRescorer}. This is additional
   *  information from the request that may be necessary to its logic, like current location. What it means
   *  is up to the implementation.
   * @return {@link IDRescorer} to use with {@link ServerRecommender#recommend(long, int, IDRescorer)}
   *  or {@code null} if none should be used. The resulting {@link IDRescorer} will be passed each candidate
   *  item ID to {@link IDRescorer#isFiltered(long)}, and each non-filtered candidate with its original score
   *  to {@link IDRescorer#rescore(long, double)}
   */
  IDRescorer getRecommendRescorer(long[] userIDs, String... args);

  /**
   * @param itemIDs items that the anonymous user is associated to
   * @param args arguments, if any, that should be used when making the {@link IDRescorer}. This is additional
   *  information from the request that may be necessary to its logic, like current location. What it means
   *  is up to the implementation.
   * @return {@link IDRescorer} to use with {@link ServerRecommender#recommendToAnonymous(long[], int, IDRescorer)}
   *  or {@code null} if none should be used. The resulting {@link IDRescorer} will be passed each candidate
   *  item ID to {@link IDRescorer#isFiltered(long)}, and each non-filtered candidate with its original score
   *  to {@link IDRescorer#rescore(long, double)}
   */
  IDRescorer getRecommendToAnonymousRescorer(long[] itemIDs, String... args);

  /**
   * @param args arguments, if any, that should be used when making the {@link IDRescorer}. This is additional
   *  information from the request that may be necessary to its logic, like current location. What it means
   *  is up to the implementation.
   * @return {@link Rescorer} to use with {@link ServerRecommender#mostSimilarItems(long[], int, Rescorer)}
   *  or {@code null} if none should be used. The resulting {@code Rescorer&lt;LongPair&gt;} will be passed
   *  each candidate item ID pair (IDs of the two similar items) to {@link Rescorer#isFiltered(Object)},
   *  and each non-filtered candidate item ID pair with its original score to
   *  {@link Rescorer#rescore(Object, double)}
   */
  Rescorer<LongPair> getMostSimilarItemsRescorer(String... args);

}
