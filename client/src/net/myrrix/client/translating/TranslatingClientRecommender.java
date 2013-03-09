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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.model.IDMigrator;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.common.iterator.FileLineIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.client.ClientRecommender;
import net.myrrix.common.OneWayMigrator;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.io.IOUtils;
import net.myrrix.common.random.MemoryIDMigrator;

/**
 * Default implementation of {@link TranslatingRecommender}. It delegates to an underlying {@link ClientRecommender}
 * which must be created and configured first.
 */
public final class TranslatingClientRecommender implements TranslatingRecommender {

  private static final Logger log = LoggerFactory.getLogger(TranslatingClientRecommender.class);

  private static final Splitter COMMA_SPLIT = Splitter.on(',');
  private static final Joiner COMMA_JOIN = Joiner.on(',');

  private final IDMigrator userTranslator;
  private final MemoryIDMigrator itemTranslator;
  private final ClientRecommender delegate;

  /**
   * @param delegate underlying {@link ClientRecommender} to use with translation
   */
  public TranslatingClientRecommender(ClientRecommender delegate) {
    this(delegate, new OneWayMigrator(), new MemoryIDMigrator());
  }

  /**
   * @param delegate underlying {@link ClientRecommender} to use with translation
   * @param userTranslator {@link IDMigrator} that translates users (need not translate back), or null to disable
   *   user translation
   * @param itemTranslator {@link MemoryIDMigrator} to use to translate items and back, or null to disable item
   *   translation
   */
  public TranslatingClientRecommender(ClientRecommender delegate,
                                      IDMigrator userTranslator,
                                      MemoryIDMigrator itemTranslator) {
    this.delegate = delegate;
    this.userTranslator = userTranslator;
    this.itemTranslator = itemTranslator;
  }

  private long translateUser(String userID) {
    return userTranslator == null ? Long.parseLong(userID) : userTranslator.toLongID(userID);
  }

  private long translateItem(String itemID) {
    return itemTranslator == null ? Long.parseLong(itemID) : itemTranslator.toLongID(itemID);
  }

  private String untranslateItem(long itemID) {
    return itemTranslator == null ? Long.toString(itemID) : itemTranslator.toStringID(itemID);
  }

  @Override
  public List<TranslatedRecommendedItem> recommend(String userID, int howMany) throws TasteException {
    long longUserID = translateUser(userID);
    List<RecommendedItem> originals = delegate.recommend(longUserID, howMany);
    return translate(originals);
  }

  @Override
  public List<TranslatedRecommendedItem> recommend(String userID,
                                                   int howMany,
                                                   boolean considerKnownItems,
                                                   String[] rescorerParams) throws TasteException {
    long longUserID = translateUser(userID);
    List<RecommendedItem> originals = delegate.recommend(longUserID, howMany, considerKnownItems, rescorerParams);
    return translate(originals);
  }

  @Override
  public List<TranslatedRecommendedItem> recommendToMany(String[] userIDs,
                                                         int howMany,
                                                         boolean considerKnownItems,
                                                         String[] rescorerParams) throws TasteException {
    long[] longUserIDs = new long[userIDs.length];
    for (int i = 0; i < userIDs.length; i++) {
      longUserIDs[i] = translateUser(userIDs[i]);
    }
    List<RecommendedItem> originals =
        delegate.recommendToMany(longUserIDs, howMany, considerKnownItems, rescorerParams);
    return translate(originals);
  }

  @Override
  public float estimatePreference(String userID, String itemID) throws TasteException {
    long longUserID = translateUser(userID);
    long longItemID = translateItem(itemID);
    return delegate.estimatePreference(longUserID, longItemID);
  }

  @Override
  public float[] estimatePreferences(String userID, String... itemIDs) throws TasteException {
    long longUserID = translateUser(userID);
    long[] longItemIDs = new long[itemIDs.length];
    for (int i = 0; i < itemIDs.length; i++) {
      longItemIDs[i] = translateItem(itemIDs[i]);
    }
    return delegate.estimatePreferences(longUserID, longItemIDs);
  }

  @Override
  public void setPreference(String userID, String itemID) throws TasteException {
    long longUserID = translateUser(userID);
    long longItemID = translateItem(itemID);
    delegate.setPreference(longUserID, longItemID);
  }

  @Override
  public void setPreference(String userID, String itemID, float value) throws TasteException {
    long longUserID = translateUser(userID);
    long longItemID = translateItem(itemID);
    delegate.setPreference(longUserID, longItemID, value);
  }

  @Override
  public void removePreference(String userID, String itemID) throws TasteException {
    long longUserID = translateUser(userID);
    long longItemID = translateItem(itemID);
    delegate.removePreference(longUserID, longItemID);
  }

  @Override
  public void setUserTag(String userID, String tag) throws TasteException {
    long longUserID = translateUser(userID);
    delegate.setUserTag(longUserID, tag);
  }

  @Override
  public void setUserTag(String userID, String tag, float value) throws TasteException {
    long longUserID = translateUser(userID);
    delegate.setUserTag(longUserID, tag, value);
  }

  @Override
  public void setItemTag(String tag, String itemID) throws TasteException {
    long longItemID = translateItem(itemID);
    delegate.setItemTag(tag, longItemID);
  }

  @Override
  public void setItemTag(String tag, String itemID, float value) throws TasteException {
    long longItemID = translateItem(itemID);
    delegate.setItemTag(tag, longItemID, value);
  }

  @Override
  public List<TranslatedRecommendedItem> mostSimilarItems(String itemID, int howMany) throws TasteException {
    long longItemID = translateItem(itemID);
    List<RecommendedItem> originals = delegate.mostSimilarItems(longItemID, howMany);
    return translate(originals);
  }

  @Override
  public List<TranslatedRecommendedItem> mostSimilarItems(String[] itemIDs, int howMany) throws TasteException {
    return mostSimilarItems(itemIDs, howMany, null, null);
  }

  @Override
  public List<TranslatedRecommendedItem> mostSimilarItems(String[] itemIDs,
                                                          int howMany,
                                                          String[] rescorerParams,
                                                          String contextUserID)
      throws TasteException {
    long[] longItemIDs = new long[itemIDs.length];
    for (int i = 0; i < itemIDs.length; i++) {
      longItemIDs[i] = translateItem(itemIDs[i]);
    }
    List<RecommendedItem> originals;
    if (contextUserID == null) {
      originals = delegate.mostSimilarItems(longItemIDs, howMany, rescorerParams, null);
    } else {
      originals = delegate.mostSimilarItems(longItemIDs, howMany, rescorerParams, translateUser(contextUserID));
    }
    return translate(originals);
  }

  @Override
  public float[] similarityToItem(String toItemID, String... itemIDs) throws TasteException {
    return similarityToItem(toItemID, itemIDs, null);
  }

  @Override
  public float[] similarityToItem(String toItemID, String[] itemIDs, String contextUserID) throws TasteException {
    long longToItemID = translateItem(toItemID);
    long[] longItemIDs = new long[itemIDs.length];
    for (int i = 0; i < itemIDs.length; i++) {
      longItemIDs[i] = translateItem(itemIDs[i]);
    }
    Long longContextUserID = contextUserID == null ? null : Long.valueOf(contextUserID);
    return delegate.similarityToItem(longToItemID, longItemIDs, longContextUserID);
  }

  @Override
  public List<TranslatedRecommendedItem> recommendedBecause(String userID, String itemID, int howMany)
      throws TasteException {
    long longUserID = translateUser(userID);
    long longItemID = translateItem(itemID);
    List<RecommendedItem> originals = delegate.recommendedBecause(longUserID, longItemID, howMany);
    return translate(originals);
  }

  @Override
  public List<TranslatedRecommendedItem> recommendToAnonymous(String[] itemIDs, int howMany)
      throws TasteException {
    return recommendToAnonymous(itemIDs, null, howMany, null, null);
  }

  @Override
  public List<TranslatedRecommendedItem> recommendToAnonymous(String[] itemIDs, float[] values, int howMany)
      throws TasteException {
    return recommendToAnonymous(itemIDs, values, howMany, null, null);
  }

  @Override
  public List<TranslatedRecommendedItem> recommendToAnonymous(String[] itemIDs,
                                                              float[] values,
                                                              int howMany,
                                                              String[] rescorerParams,
                                                              String contextUserID)
      throws TasteException {
    long[] longItemIDs = new long[itemIDs.length];
    for (int i = 0; i < itemIDs.length; i++) {
      longItemIDs[i] = translateItem(itemIDs[i]);
    }
    List<RecommendedItem> originals;
    if (contextUserID == null) {
      originals = delegate.recommendToAnonymous(longItemIDs, values, howMany, rescorerParams, null);
    } else {
      originals =
          delegate.recommendToAnonymous(longItemIDs, values, howMany, rescorerParams, translateUser(contextUserID));
    }
    return translate(originals);
  }

  @Override
  public List<TranslatedRecommendedItem> mostPopularItems(int howMany) throws TasteException {
    return translate(delegate.mostPopularItems(howMany));
  }

  @Override
  public void ingest(Reader reader) throws TasteException {
    File tempFile = null;
    try {
      tempFile = copyAndTranslateToTempFile(reader);
      delegate.ingest(tempFile);
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    } finally {
      if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
        log.warn("Could not delete {}", tempFile);
      }
    }
  }

  private File copyAndTranslateToTempFile(Reader reader) throws IOException {
    File tempFile = File.createTempFile("myrrix-", ".csv.gz");
    tempFile.deleteOnExit();
    log.debug("Translating ingest input to {}", tempFile);
    BufferedReader buffered = IOUtils.buffer(reader);
    try {
      Writer out = IOUtils.buildGZIPWriter(tempFile);
      try {
        String line;
        while ((line = buffered.readLine()) != null) {
          Iterator<String> it = COMMA_SPLIT.split(line).iterator();
          String userIDString = it.next();
          String itemIDString = it.next();
          long longUserID = translateUser(userIDString);
          long longItemID = translateItem(itemIDString);
          String translatedLine;
          if (it.hasNext()) {
            String valueString = it.next();
            translatedLine = COMMA_JOIN.join(longUserID, longItemID, valueString);
          } else {
            translatedLine = COMMA_JOIN.join(longUserID, longItemID);
          }
          out.write(translatedLine);
          out.write('\n');
        }
      } finally {
        out.close(); // Want to know if output stream close failed -- maybe failed to write
      }
    } finally {
      Closeables.close(buffered, true);
    }
    log.debug("Done translating ingest input to {}", tempFile);    
    return tempFile;
  }

  @Override
  public void ingest(File file) throws TasteException {
    Reader reader = null;
    try {
      reader = IOUtils.openReaderMaybeDecompressing(file);
      ingest(reader);
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    } finally {
      try {
        Closeables.close(reader, true);
      } catch (IOException e) {
        // Can't happen, continue
      }
    }
  }

  @Override
  public void addItemIDs(Iterable<String> ids) {
    if (itemTranslator != null) {
      itemTranslator.initialize(ids);
    }
  }

  @Override
  public void addItemIDs(File idFile) throws TasteException {
    try {
      addItemIDs(new FileLineIterable(idFile));
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

  @Override
  public boolean isReady() throws TasteException {
    return delegate.isReady();
  }

  @Override
  public void refresh() {
    delegate.refresh();
  }

  @Override
  public void await() throws TasteException, InterruptedException {
    delegate.await();
  }
  
  @Override
  public boolean await(long time, TimeUnit unit) throws TasteException, InterruptedException {
    return delegate.await(time, unit);
  }

  @Override
  public Collection<String> getAllItemIDs() throws TasteException {
    return translate(delegate.getAllItemIDs());
  }

  @Override
  public int getNumUserClusters() throws TasteException {
    return delegate.getNumUserClusters();
  }

  @Override
  public int getNumItemClusters() throws TasteException {
    return delegate.getNumItemClusters();
  }

  @Override
  public Collection<String> getUserCluster(int n) throws TasteException {
    FastIDSet userIDs = delegate.getUserCluster(n);
    Collection<String> translated = Lists.newArrayListWithCapacity(userIDs.size());
    LongPrimitiveIterator it = userIDs.iterator();
    while (it.hasNext()) {
      translated.add(Long.toString(it.nextLong()));
    }
    return translated;
  }

  @Override
  public Collection<String> getItemCluster(int n) throws TasteException {
    return translate(delegate.getItemCluster(n));
  }

  private List<TranslatedRecommendedItem> translate(Collection<RecommendedItem> originals) {
    List<TranslatedRecommendedItem> translated = Lists.newArrayListWithCapacity(originals.size());
    for (RecommendedItem original : originals) {
      long id = original.getItemID();
      String untranslation = untranslateItem(id);
      String translatedItemID = untranslation == null ? Long.toString(id) : untranslation;
      translated.add(new GenericTranslatedRecommendedItem(translatedItemID, original.getValue()));
    }
    return translated;
  }

  private Collection<String> translate(FastIDSet itemIDs) {
    Collection<String> result = Lists.newArrayListWithCapacity(itemIDs.size());
    LongPrimitiveIterator it = itemIDs.iterator();
    while (it.hasNext()) {
      result.add(untranslateItem(it.nextLong()));
    }
    return result;
  }

}
