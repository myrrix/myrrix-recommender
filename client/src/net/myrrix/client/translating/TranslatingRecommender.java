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

package net.myrrix.client.translating;

import java.io.File;
import java.io.Reader;
import java.util.List;

import org.apache.mahout.cf.taste.common.TasteException;

/**
 * <p>This is a variation on the usual {@link org.apache.mahout.cf.taste.recommender.ItemBasedRecommender}
 * from Mahout (which uses {@code long} values to identify users and items) that instead uses {@link String}
 * as keys. Implementations of this interface perform the same functions, but internally translate from
 * instances of type {@link String} to {@code long} and back. In particular, {@link String}s are hashed to
 * {@code long} values using MD5, and the reverse mapping is stored locally for a reverse translation.</p>
 *
 * <p>To do this, the client must be aware of all item IDs that may be returned. It will receive a hashed
 * value from the Serving Layer, and must have been initialized with {@link #addItemIDs(Iterable)} or
 * {@link #addItemIDs(File)} first in order to perform the reverse translation. Otherwise the returned item IDs
 * will be (string representations of) the hashed value only.</p>
 */
public interface TranslatingRecommender {

  /**
   * @see net.myrrix.common.MyrrixRecommender#recommend(long, int)
   */
  List<TranslatedRecommendedItem> recommend(String userID, int howMany) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#recommend(long, int, boolean, org.apache.mahout.cf.taste.recommender.IDRescorer)
   */
  List<TranslatedRecommendedItem> recommend(String userID,
                                            int howMany,
                                            boolean considerKnownItems) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#recommendToMany(long[], int, boolean, org.apache.mahout.cf.taste.recommender.IDRescorer)
   */
  List<TranslatedRecommendedItem> recommendToMany(String[] userIDs,
                                                  int howMany,
                                                  boolean considerKnownItems) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#estimatePreference(long, long)
   */
  float estimatePreference(String userID, String itemID) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#estimatePreferences(long, long...)
   */
  float[] estimatePreferences(String userID, String... itemIDs) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#setPreference(long, long)
   */
  void setPreference(String userID, String itemID) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#setPreference(long, long, float)
   */
  void setPreference(String userID, String itemID, float value) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#removePreference(long, long)
   */
  void removePreference(String userID, String itemID) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#mostSimilarItems(long, int)
   */
  List<TranslatedRecommendedItem> mostSimilarItems(String itemID, int howMany) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#mostSimilarItems(long[], int)
   */
  List<TranslatedRecommendedItem> mostSimilarItems(String[] itemIDs, int howMany) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#recommendedBecause(long, long, int)
   */
  List<TranslatedRecommendedItem> recommendedBecause(String userID, String itemID, int howMany)
      throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#recommendToAnonymous(long[], int)
   */
  List<TranslatedRecommendedItem> recommendToAnonymous(String[] itemIDs, int howMany) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#ingest(Reader)
   */
  void ingest(Reader reader) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#ingest(File)
   */
  void ingest(File file) throws TasteException;

  /**
   * "Teaches" the client about item IDs, so that when the underlying recommender returns their hashed
   * representation, this client can translate them back into true IDs. The client must be told about
   * all IDs it may need to return, using this method of {@link #addItemIDs(File)}.
   *
   * @param ids item IDs that are in use by the system
   */
  void addItemIDs(Iterable<String> ids);

  /**
   * Like {@link #addItemIDs(Iterable)}, but accepts a file containing item IDs, one per line.
   *
   * @param idFile file with one ID per line
   */
  void addItemIDs(File idFile) throws TasteException;

  /**
   * @return true if and only if the instance is ready to make recommendations; may be false for example
   *  while the recommender is still building an initial model
   */
  boolean isReady() throws TasteException;

}
