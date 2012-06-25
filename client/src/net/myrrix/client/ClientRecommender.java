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

package net.myrrix.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.GenericRecommendedItem;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.IOUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;

/**
 * <p>An implementation of {@link MyrrixRecommender} which accesses a remote Serving Layer instance
 * over HTTP or HTTPS. This is like a local "handle" on the remote recommender.</p>
 *
 * <p>It is useful to note here, again, that the API methods {@link #setPreference(long, long)}
 * and {@link #removePreference(long, long)}, retained from Apache Mahout, have a somewhat different meaning
 * than in Mahout. They add to an association strength, rather than replace it. See the javadoc.</p>
 *
 * <p>There are a few advanced, system-wide parameters that can be set to affect how the client works.
 * These should not normally be used:</p>
 *
 * <ul>
 *   <li>{@code client.connection.close}: Causes the client to request no HTTP keep-alive. This can avoid
 *    running out of local sockets on the client side during load testing, but should not otherwise be set.</li>
 * </ul>
 *
 * @author Sean Owen
 */
public final class ClientRecommender implements MyrrixRecommender {

  private static final Logger log = LoggerFactory.getLogger(ClientRecommender.class);

  private static final Splitter COMMA = Splitter.on(',');

  private final MyrrixClientConfiguration config;
  private final boolean needAuthentication;
  private final boolean closeConnection;
  private final List<List<Pair<String,Integer>>> partitions;
  private final Random random;

  /**
   * Instantiates a new recommender client with the given configuration
   *
   * @param config configuration to use with this client
   * @throws IOException if the HTTP client encounters an error during configuration
   */
  public ClientRecommender(MyrrixClientConfiguration config) throws IOException {
    Preconditions.checkNotNull(config);
    this.config = config;

    final String userName = config.getUserName();
    final String password = config.getPassword();
    needAuthentication = userName != null && password != null;
    if (needAuthentication) {
      Authenticator.setDefault(new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(userName, password.toCharArray());
        }
      });
    }

    if (config.getKeystoreFile() != null) {
      log.warn("A keystore file has been specified. " +
               "This should only be done to accept self-signed certificates in development.");
      HttpsURLConnection.setDefaultSSLSocketFactory(buildSSLSocketFactory());
    }

    closeConnection = Boolean.valueOf(System.getProperty("client.connection.close"));

    partitions = config.getPartitions();
    random = RandomUtils.getRandom();
  }

  private SSLSocketFactory buildSSLSocketFactory() throws IOException {

    final HostnameVerifier defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
    HttpsURLConnection.setDefaultHostnameVerifier(
      new HostnameVerifier(){
        @Override
        public boolean verify(String hostname, SSLSession sslSession) {
          return "localhost".equals(hostname)
              || "127.0.0.1".equals(hostname)
              || defaultVerifier.verify(hostname, sslSession);
        }
      });

    try {

      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      File trustStoreFile = config.getKeystoreFile();
      String password = config.getKeystorePassword();
      Preconditions.checkNotNull(password);

      InputStream in = new FileInputStream(trustStoreFile);
      try {
        keyStore.load(in, password.toCharArray());
      } finally {
        Closeables.closeQuietly(in);
      }

      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(keyStore);

      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, tmf.getTrustManagers(), null);
      return ctx.getSocketFactory();

    } catch (NoSuchAlgorithmException nsae) {
      // can't happen?
      throw new IllegalStateException(nsae);
    } catch (KeyStoreException kse) {
      throw new IOException(kse);
    } catch (KeyManagementException kme) {
      throw new IOException(kme);
    } catch (CertificateException ce) {
      throw new IOException(ce);
    }
  }

  /**
   * @param path URL to access
   * @param method HTTP method to use
   * @param partitionID ID value that determines partition, or {@code null} if no partition is needed
   * @return a {@link HttpURLConnection} to the Serving Layer with default configuration in place
   */
  private HttpURLConnection makeConnection(String path, String method, Long partitionID) throws IOException {
    String protocol = config.isSecure() ? "https" : "http";
    Pair<String,Integer> replica = choosePartitionAndReplica(partitionID);
    URL url;
    try {
      url = new URL(protocol, replica.getFirst(), replica.getSecond(), path);
    } catch (MalformedURLException mue) {
      // can't happen
      throw new IllegalStateException(mue);
    }
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod(method);
    connection.setDoInput(true);
    connection.setDoOutput(false);
    connection.setUseCaches(false);
    connection.setAllowUserInteraction(false);
    connection.setRequestProperty("Accept", "text/csv");
    if (closeConnection) {
      connection.setRequestProperty("Connection", "close");
    }
    return connection;
  }

  private Pair<String,Integer> choosePartitionAndReplica(Long id) {
    List<Pair<String,Integer>> replicas;
    int numPartitions = partitions.size();
    if (numPartitions == 1) {
      replicas = partitions.get(0);
    } else {
      if (id == null) {
        // No need to partition, use one at random
        replicas = partitions.get(random.nextInt(numPartitions));
      } else {
        replicas = partitions.get(partition(id));
      }
    }
    int numReplicas = replicas.size();
    return numReplicas == 1 ? replicas.get(0) : replicas.get(random.nextInt(numReplicas));
  }

  private int partition(long id) {
    int numPartitions = partitions.size();
    return numPartitions == 1 ? 0 : Math.abs((int) (id % numPartitions));
  }

  /**
   * Calls {@link #setPreference(long, long, float)} with value 1.0.
   */
  @Override
  public void setPreference(long userID, long itemID) throws TasteException {
    setPreference(userID, itemID, 1.0f);
  }

  @Override
  public void setPreference(long userID, long itemID, float value) throws TasteException {
    doSetOrRemove(userID, itemID, value, true);
  }

  /**
   * Calls {@link #setPreference(long, long, float)} with value -1.0.
   */
  @Override
  public void removePreference(long userID, long itemID) throws TasteException {
    removePreference(userID, itemID, 1.0f);
  }

  @Override
  public void removePreference(long userID, long itemID, float value) throws TasteException {
    doSetOrRemove(userID, itemID, value, false);
  }

  private void doSetOrRemove(long userID, long itemID, float value, boolean set) throws TasteException {
    boolean sendValue = value != 1.0f;
    try {
      String method = set ? "POST" : "DELETE";
      HttpURLConnection connection = makeConnection("/pref/" + userID + '/' + itemID, method, userID);
      connection.setDoOutput(sendValue);
      try {
        if (sendValue) {
          byte[] bytes = String.valueOf(value).getBytes(Charsets.UTF_8);
          connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
          connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
          OutputStream out = connection.getOutputStream();
          out.write(bytes);
          Closeables.closeQuietly(out);
        }
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            break;
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } finally {
        connection.disconnect();
      }
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

  /**
   * @param userID user ID whose preference is to be estimated
   * @param itemID item ID to estimate preference for
   * @return an estimate of the strength of the association between the user and item. These values are the
   *  same as will be returned from {@link #recommend(long, int)}. They are opaque values and have no interpretation
   *  other than that larger means stronger. The values are typically in the range [0,1] but are not guaranteed
   *  to be so.
   * @throws NoSuchUserException if the user is not known in the model
   * @throws NoSuchItemException if the item is not known in the model
   * @throws NotReadyException if the recommender has no model available yet
   * @throws TasteException if another error occurs
   */
  @Override
  public float estimatePreference(long userID, long itemID) throws TasteException {
    float[] results = estimatePreferences(userID, itemID);
    return results[0];
  }

  @Override
  public float[] estimatePreferences(long userID, long... itemIDs) throws TasteException {
    StringBuilder urlPath = new StringBuilder();
    urlPath.append("/estimate/");
    urlPath.append(userID);
    for (long itemID : itemIDs) {
      urlPath.append('/').append(itemID);
    }
    try {
      HttpURLConnection connection = makeConnection(urlPath.toString(), "GET", userID);
      try {
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            break;
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchItemException(userID + "/" + Arrays.toString(itemIDs));
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charsets.UTF_8));
        try {
          float[] result = new float[itemIDs.length];
          for (int i = 0; i < itemIDs.length; i++) {
            String line = reader.readLine();
            result[i] = Float.parseFloat(line);
          }
          return result;
        } finally {
          Closeables.closeQuietly(reader);
        }
      } finally {
        connection.disconnect();
      }
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

  /**
   * Like {@link #recommend(long, int, boolean, IDRescorer)}, and sets {@code considerKnownItems} to {@code false}
   * and {@code rescorer} to {@code null}.
   */
  @Override
  public List<RecommendedItem> recommend(long userID, int howMany) throws TasteException {
    return recommend(userID, howMany, false, null);
  }

  /**
   * <p>Note that {@link IDRescorer} is not supported in the client now and must be null.</p>
   *
   * @param userID user for which recommendations are to be computed
   * @param howMany desired number of recommendations
   * @param considerKnownItems if true, items that the user is already associated to are candidates
   *  for recommendation. Normally this is {@code false}.
   * @param rescorer must be null
   * @return {@link List} of recommended {@link RecommendedItem}s, ordered from most strongly recommend to least
   * @throws NoSuchUserException if the user is not known in the model
   * @throws NotReadyException if the recommender has no model available yet
   * @throws TasteException if another error occurs
   * @throws UnsupportedOperationException if rescorer is not null
   */
  @Override
  public List<RecommendedItem> recommend(long userID,
                                         int howMany,
                                         boolean considerKnownItems,
                                         IDRescorer rescorer) throws TasteException {

    if (rescorer != null) {
      throw new UnsupportedOperationException();
    }

    StringBuilder urlPath = new StringBuilder();
    urlPath.append("/recommend/");
    urlPath.append(userID);
    urlPath.append("?howMany=").append(howMany);
    if (considerKnownItems) {
      urlPath.append("&considerKnownItems=true");
    }

    try {
      HttpURLConnection connection = makeConnection(urlPath.toString(), "GET", userID);
      try {
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            break;
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchUserException(userID);
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
        return consumeItems(connection);
      } finally {
        connection.disconnect();
      }

    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

  private static List<RecommendedItem> consumeItems(HttpURLConnection connection) throws IOException {
    List<RecommendedItem> result = Lists.newArrayList();
    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charsets.UTF_8));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        Iterator<String> tokens = COMMA.split(line).iterator();
        long itemID = Long.parseLong(tokens.next());
        float value = Float.parseFloat(tokens.next());
        result.add(new GenericRecommendedItem(itemID, value));
      }
    } finally {
      Closeables.closeQuietly(reader);
    }
    return result;
  }

  /**
   * <p>Note that {@link IDRescorer} is not supported in the client now and must be null.</p>
   *
   * @param userIDs users for which recommendations are to be computed
   * @param howMany desired number of recommendations
   * @param considerKnownItems if true, items that the user is already associated to are candidates
   *  for recommendation. Normally this is {@code false}.
   * @param rescorer must be null
   * @return {@link List} of recommended {@link RecommendedItem}s, ordered from most strongly recommend to least
   * @throws NoSuchUserException if the user is not known in the model
   * @throws NotReadyException if the recommender has no model available yet
   * @throws TasteException if another error occurs
   * @throws UnsupportedOperationException if rescorer is not null
   */
  @Override
  public List<RecommendedItem> recommendToMany(long[] userIDs,
                                               int howMany,
                                               boolean considerKnownItems,
                                               IDRescorer rescorer) throws TasteException {
    if (rescorer != null) {
      throw new UnsupportedOperationException();
    }

    StringBuilder urlPath = new StringBuilder();
    urlPath.append("/recommendToMany");
    for (long userID : userIDs) {
      urlPath.append('/').append(userID);
    }
    urlPath.append("?howMany=").append(howMany);

    Integer requiredPartition = null;
    for (long userID : userIDs) {
      if (requiredPartition == null) {
        requiredPartition = partition(userID);
      } else {
        if (requiredPartition != partition(userID)) {
          throw new IllegalArgumentException("Not all user IDs are on the same partition");
        }
      }
    }

    try {
      HttpURLConnection connection = makeConnection(urlPath.toString(), "GET", userIDs[0]);
      try {
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            break;
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchUserException(Arrays.toString(userIDs));
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
        return consumeItems(connection);
      } finally {
        connection.disconnect();
      }

    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

  @Override
  public List<RecommendedItem> recommendToAnonymous(long[] itemIDs, int howMany) throws TasteException {
    StringBuilder urlPath = new StringBuilder();
    urlPath.append("/recommendToAnonymous");
    for (long itemID : itemIDs) {
      urlPath.append('/').append(itemID);
    }
    urlPath.append("?howMany=").append(howMany);
    try {
      HttpURLConnection connection = makeConnection(urlPath.toString(), "GET", null);
      try {
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            break;
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchItemException(Arrays.toString(itemIDs));
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
        return consumeItems(connection);
      } finally {
        connection.disconnect();
      }
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

  /**
   * Computes items most similar to an item or items. The returned items have the highest average similarity
   * to the given items.
   *
   * @param itemIDs items for which most similar items are required
   * @param howMany maximum number of similar items to return; fewer may be returned
   * @return {@link RecommendedItem}s representing the top recommendations for the user, ordered by quality,
   *  descending. The score associated to it is an opaque value. Larger means more similar, but no further
   *  interpretation may necessarily be applied.
   * @throws NoSuchItemException if any of the items is not known in the model
   * @throws NotReadyException if the recommender has no model available yet
   * @throws TasteException if another error occurs
   */
  @Override
  public List<RecommendedItem> mostSimilarItems(long[] itemIDs, int howMany) throws TasteException {
    StringBuilder urlPath = new StringBuilder();
    urlPath.append("/similarity");
    for (long itemID : itemIDs) {
      urlPath.append('/').append(itemID);
    }
    urlPath.append("?howMany=").append(howMany);
    try {
      HttpURLConnection connection = makeConnection(urlPath.toString(), "GET", null);
      try {
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            break;
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchItemException(Arrays.toString(itemIDs));
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
        return consumeItems(connection);
      } finally {
        connection.disconnect();
      }

    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

  /**
   * One-argument version of {@link #mostSimilarItems(long[], int)}.
   */
  @Override
  public List<RecommendedItem> mostSimilarItems(long itemID, int howMany) throws TasteException {
    return mostSimilarItems(new long[] { itemID }, howMany);
  }

  /**
   * <p>Lists the items that were most influential in recommending a given item to a given user. Exactly how this
   * is determined is left to the implementation, but, generally this will return items that the user prefers
   * and that are similar to the given item.</p>
   *
   * <p>These values by which the results are ordered are opaque values and have no interpretation
   * other than that larger means stronger.</p>
   *
   * @param userID ID of user who was recommended the item
   * @param itemID ID of item that was recommended
   * @param howMany maximum number of items to return
   * @return {@link List} of {@link RecommendedItem}, ordered from most influential in recommended the given
   *  item to least
   * @throws NoSuchUserException if the user is not known in the model
   * @throws NoSuchItemException if the item is not known in the model
   * @throws NotReadyException if the recommender has no model available yet
   * @throws TasteException if another error occurs
   */
  @Override
  public List<RecommendedItem> recommendedBecause(long userID, long itemID, int howMany) throws TasteException {
    try {
      HttpURLConnection connection =
          makeConnection("/because/" + userID + '/' + itemID + "?howMany=" + howMany, "GET", userID);
      try {
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            break;
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchItemException(userID + '/' + itemID);
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
        return consumeItems(connection);
      } finally {
        connection.disconnect();
      }

    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
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
  public void ingest(Reader reader) throws TasteException {
    try {
      HttpURLConnection connection = makeConnection("/ingest", "POST", null);
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
      connection.setRequestProperty("Content-Encoding", "gzip");
      if (!needAuthentication) {
        connection.setChunkedStreamingMode(0); // Use default buffer size
      }
      // Must buffer in memory if using authentication since it won't handle the authorization challenge
      // otherwise -- client will have to buffer all in memory
      try {
        Writer out = new OutputStreamWriter(new GZIPOutputStream(connection.getOutputStream()), Charsets.UTF_8);
        try {
          CharStreams.copy(reader, out);
          //out.flush();
        } finally {
          Closeables.closeQuietly(out);
        }
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            break;
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } finally {
        connection.disconnect();
      }
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

  /**
   * Requests that the Serving Layer recompute its models. This is a request, and may or may not result
   * in an update.
   *
   * @param alreadyRefreshed should be null
   */
  @Override
  public void refresh(Collection<Refreshable> alreadyRefreshed) {
    try {
      HttpURLConnection connection = makeConnection("/refresh", "POST", null);
      try {
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
          log.warn("Unable to refresh; continuing");
        }
      } finally {
        connection.disconnect();
      }
    } catch (IOException ioe) {
      log.warn("Unable to refresh; continuing");
    }
  }

  /**
   * Not available. The client does not directly use any {@link DataModel}.
   *
   * @throws UnsupportedOperationException
   * @deprecated do not call
   */
  @Deprecated
  @Override
  public DataModel getDataModel() {
    throw new UnsupportedOperationException();
  }

  /**
   * {@link Rescorer}s are not available at this time in the model.
   *
   * @return {@link #recommend(long, int)} if rescorer is null
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #recommend(long, int)} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> recommend(long userID, int howMany, IDRescorer rescorer)
      throws TasteException {
    if (rescorer != null) {
      throw new UnsupportedOperationException();
    }
    return recommend(userID, howMany);
  }

  /**
   * {@link Rescorer}s are not available at this time in the model.
   *
   * @return {@link #mostSimilarItems(long, int)} if rescorer is null
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #mostSimilarItems(long, int)} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> mostSimilarItems(long itemID, int howMany, Rescorer<LongPair> rescorer)
      throws TasteException {
    if (rescorer != null) {
      throw new UnsupportedOperationException();
    }
    return mostSimilarItems(itemID, howMany);
  }

  /**
   * {@link Rescorer}s are not available at this time in the model.
   *
   * @return {@link #mostSimilarItems(long[], int)} if rescorer is null
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #mostSimilarItems(long[], int)} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> mostSimilarItems(long[] itemIDs, int howMany, Rescorer<LongPair> rescorer)
      throws TasteException {
    if (rescorer != null) {
      throw new UnsupportedOperationException();
    }
    return mostSimilarItems(itemIDs, howMany);
  }

  /**
   * {@code excludeItemIfNotSimilarToAll} is not applicable in this implementation.
   *
   * @return {@link #mostSimilarItems(long[], int)} if excludeItemIfNotSimilarToAll is false
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #mostSimilarItems(long[], int)} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> mostSimilarItems(long[] itemIDs,
                                                int howMany,
                                                boolean excludeItemIfNotSimilarToAll) throws TasteException {
    if (excludeItemIfNotSimilarToAll) {
      throw new UnsupportedOperationException();
    }
    return mostSimilarItems(itemIDs, howMany);
  }

  /**
   * {@link Rescorer}s are not available at this time in the model.
   * {@code excludeItemIfNotSimilarToAll} is not applicable in this implementation.
   *
   * @return {@link #mostSimilarItems(long[], int)} if excludeItemIfNotSimilarToAll is false and rescorer is null
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #mostSimilarItems(long[], int)} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> mostSimilarItems(long[] itemIDs,
                                                int howMany,
                                                Rescorer<LongPair> rescorer,
                                                boolean excludeItemIfNotSimilarToAll) throws TasteException {
    if (excludeItemIfNotSimilarToAll || rescorer != null) {
      throw new UnsupportedOperationException();
    }
    return mostSimilarItems(itemIDs, howMany);
  }

  @Override
  public boolean isReady() throws TasteException {
    try {
      HttpURLConnection connection = makeConnection("/ready", "HEAD", null);
      try {
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            return true;
          case HttpURLConnection.HTTP_UNAVAILABLE:
            return false;
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } finally {
        connection.disconnect();
      }
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

}
