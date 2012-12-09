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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import com.google.common.base.Charsets;
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
                                                   boolean considerKnownItems) throws TasteException {
    long longUserID = translateUser(userID);
    List<RecommendedItem> originals = delegate.recommend(longUserID, howMany, considerKnownItems, null);
    return translate(originals);
  }

  @Override
  public List<TranslatedRecommendedItem> recommendToMany(String[] userIDs,
                                                         int howMany,
                                                         boolean considerKnownItems) throws TasteException {
    long[] longUserIDs = new long[userIDs.length];
    for (int i = 0; i < userIDs.length; i++) {
      longUserIDs[i] = translateUser(userIDs[i]);
    }
    List<RecommendedItem> originals = delegate.recommendToMany(longUserIDs, howMany, considerKnownItems, null);
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
  public List<TranslatedRecommendedItem> mostSimilarItems(String itemID, int howMany) throws TasteException {
    long longItemID = translateItem(itemID);
    List<RecommendedItem> originals = delegate.mostSimilarItems(longItemID, howMany);
    return translate(originals);
  }

  @Override
  public List<TranslatedRecommendedItem> mostSimilarItems(String[] itemIDs, int howMany) throws TasteException {
    return mostSimilarItems(itemIDs, howMany, null);
  }

  @Override
  public List<TranslatedRecommendedItem> mostSimilarItems(String[] itemIDs, int howMany, String contextUserID)
      throws TasteException {
    long[] longItemIDs = new long[itemIDs.length];
    for (int i = 0; i < itemIDs.length; i++) {
      longItemIDs[i] = translateItem(itemIDs[i]);
    }
    List<RecommendedItem> originals;
    if (contextUserID == null) {
      originals = delegate.mostSimilarItems(longItemIDs, howMany);
    } else {
      originals = delegate.mostSimilarItems(longItemIDs, howMany, translateUser(contextUserID));
    }
    return translate(originals);
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
  public List<TranslatedRecommendedItem> recommendToAnonymous(String[] itemIDs, int howMany) throws TasteException {
    return recommendToAnonymous(itemIDs, howMany, null);
  }

  @Override
  public List<TranslatedRecommendedItem> recommendToAnonymous(String[] itemIDs, int howMany, String contextUserID)
      throws TasteException {
    long[] longItemIDs = new long[itemIDs.length];
    for (int i = 0; i < itemIDs.length; i++) {
      longItemIDs[i] = translateItem(itemIDs[i]);
    }
    List<RecommendedItem> originals;
    if (contextUserID == null) {
      originals = delegate.recommendToAnonymous(longItemIDs, howMany);
    } else {
      originals = delegate.recommendToAnonymous(longItemIDs, howMany, translateUser(contextUserID));
    }
    return translate(originals);
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
    BufferedReader buffered =
        reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
    try {
      Writer out = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(tempFile)), Charsets.UTF_8);
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
          out.write(translatedLine + '\n');
        }
      } finally {
        out.close(); // Want to know of output stream close failed -- maybe failed to write
      }
    } finally {
      Closeables.closeQuietly(buffered);
    }
    return tempFile;
  }

  @Override
  public void ingest(File file) throws TasteException {
    Reader reader = null;
    try {
      reader = new InputStreamReader(IOUtils.openMaybeDecompressing(file), Charsets.UTF_8);
      ingest(reader);
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    } finally {
      Closeables.closeQuietly(reader);
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
  public void await() throws TasteException {
    delegate.await();
  }

  @Override
  public Collection<String> getAllItemIDs() throws TasteException {
    FastIDSet itemIDs = delegate.getAllItemIDs();
    Collection<String> result = Lists.newArrayListWithCapacity(itemIDs.size());
    LongPrimitiveIterator it = itemIDs.iterator();
    while (it.hasNext()) {
      result.add(untranslateItem(it.nextLong()));
    }
    return result;
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

}
