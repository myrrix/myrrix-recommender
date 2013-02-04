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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
   * @see net.myrrix.client.ClientRecommender#recommend(long, int, boolean, String[])
   */
  List<TranslatedRecommendedItem> recommend(String userID,
                                            int howMany,
                                            boolean considerKnownItems,
                                            String[] rescorerParams) throws TasteException;

  /**
   * @see net.myrrix.client.ClientRecommender#recommendToMany(long[], int, boolean, String[])
   */
  List<TranslatedRecommendedItem> recommendToMany(String[] userIDs,
                                                  int howMany,
                                                  boolean considerKnownItems,
                                                  String[] rescorerParams) throws TasteException;

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
   * @see net.myrrix.client.ClientRecommender#mostSimilarItems(long[], int, String[], Long)
   */
  List<TranslatedRecommendedItem> mostSimilarItems(String[] itemIDs,
                                                   int howMany,
                                                   String[] rescorerParams,
                                                   String contextUserID)
      throws TasteException;

  /**
   * @see net.myrrix.client.ClientRecommender#similarityToItem(long, long...)
   */
  float[] similarityToItem(String toItemID, String... itemIDs) throws TasteException;

  /**
   * @see net.myrrix.client.ClientRecommender#similarityToItem(long, long[], Long)
   */
  float[] similarityToItem(String toItemID, String[] itemIDs, String contextUserID) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#recommendedBecause(long, long, int)
   */
  List<TranslatedRecommendedItem> recommendedBecause(String userID, String itemID, int howMany)
      throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#recommendToAnonymous(long[], int)
   */
  List<TranslatedRecommendedItem> recommendToAnonymous(String[] itemIDs, int howMany)
      throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#recommendToAnonymous(long[], float[], int)
   */
  List<TranslatedRecommendedItem> recommendToAnonymous(String[] itemIDs, float[] values, int howMany)
      throws TasteException;

  /**
   * @see net.myrrix.client.ClientRecommender#recommendToAnonymous(long[], float[], int, String[], Long)
   */
  List<TranslatedRecommendedItem> recommendToAnonymous(String[] itemIDs,
                                                       float[] values,
                                                       int howMany,
                                                       String[] rescorerParams,
                                                       String contextUserID)
      throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#mostPopularItems(int)
   */
  List<TranslatedRecommendedItem> mostPopularItems(int howMany) throws TasteException;

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

  /**
   * @see net.myrrix.common.MyrrixRecommender#refresh(Collection)
   */
  void refresh();

  /**
   * Blocks indefinitely until {@link #isReady()} returns {@code true}.
   * 
   * @throws TasteException if an error occurs while checking {@link #isReady()}
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  void await() throws TasteException, InterruptedException;
  
  /**
   * Blocks until {@link #isReady()} returns {@code true}, or the given timeout is reached.
   * 
   * @throws TasteException if an error occurs while checking {@link #isReady()}
   * @throws InterruptedException if the thread is interrupted while waiting
   * @return {@code true} if {@link #isReady()} is {@code true}, or {@code false} if it timed out
   */
  boolean await(long time, TimeUnit unit) throws TasteException, InterruptedException;

  /**
   * @return all item IDs currently in the model
   */
  Collection<String> getAllItemIDs() throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#getNumUserClusters()
   */
  int getNumUserClusters() throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#getNumItemClusters()
   */
  int getNumItemClusters() throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#getUserCluster(int)
   */
  Collection<String> getUserCluster(int n) throws TasteException;

  /**
   * @see net.myrrix.common.MyrrixRecommender#getItemCluster(int)
   */
  Collection<String> getItemCluster(int n) throws TasteException;

}
