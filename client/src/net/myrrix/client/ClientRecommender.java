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
import java.io.OutputStream;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.io.IOUtils;
import net.myrrix.common.LangUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;
import net.myrrix.common.random.RandomUtils;

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
 *   <li>{@code client.https.ignoreHost}: When using HTTPS, ignore the host specified in the certificate. This
 *    can make development easier, but must not be used in production.</li>
 * </ul>
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class ClientRecommender implements MyrrixRecommender {

  private static final Logger log = LoggerFactory.getLogger(ClientRecommender.class);

  private static final Splitter COMMA = Splitter.on(',');
  private static final String IGNORE_HOSTNAME_KEY = "client.https.ignoreHost";
  private static final String CONNECTION_CLOSE_KEY = "client.connection.close";

  private static final Map<String,String> INGEST_REQUEST_PROPS;
  static {
    INGEST_REQUEST_PROPS = Maps.newHashMapWithExpectedSize(2);
    INGEST_REQUEST_PROPS.put(HttpHeaders.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
    INGEST_REQUEST_PROPS.put(HttpHeaders.CONTENT_ENCODING, "gzip");
  }
  private static final String DESIRED_RESPONSE_CONTENT_TYPE = MediaType.CSV_UTF_8.withoutParameters().toString();

  private final MyrrixClientConfiguration config;
  private final boolean needAuthentication;
  private final boolean closeConnection;
  private final boolean ignoreHTTPSHost;
  private final List<List<HostAndPort>> partitions;

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

    closeConnection = Boolean.valueOf(System.getProperty(CONNECTION_CLOSE_KEY));
    ignoreHTTPSHost = Boolean.valueOf(System.getProperty(IGNORE_HOSTNAME_KEY));

    partitions = config.getPartitions();
  }

  private SSLSocketFactory buildSSLSocketFactory() throws IOException {

    final HostnameVerifier defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
    HttpsURLConnection.setDefaultHostnameVerifier(
      new HostnameVerifier(){
        @Override
        public boolean verify(String hostname, SSLSession sslSession) {
          return ignoreHTTPSHost
              || "localhost".equals(hostname)
              || "127.0.0.1".equals(hostname)
              || defaultVerifier.verify(hostname, sslSession);
        }
      });

    try {

      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      File trustStoreFile = config.getKeystoreFile().getAbsoluteFile();
      String password = config.getKeystorePassword();
      Preconditions.checkNotNull(password);

      InputStream in = new FileInputStream(trustStoreFile);
      try {
        keyStore.load(in, password.toCharArray());
      } finally {
        Closeables.close(in, true);
      }

      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(keyStore);

      SSLContext ctx;
      try {
        ctx = SSLContext.getInstance("TLSv1.1"); // Java 7 only
      } catch (NoSuchAlgorithmException ignored) {
        log.info("TLSv1.1 unavailable, falling back to TLSv1");
        ctx = SSLContext.getInstance("TLSv1"); // Java 6       
        // This also seems to be necessary:
        if (System.getProperty("https.protocols") == null) {
          System.setProperty("https.protocols", "TLSv1");
        }
      }
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
   * @param replica host and port of replica to connect to
   * @param path URL to access (relative to context root)
   * @param method HTTP method to use
   */
  private HttpURLConnection buildConnectionToReplica(HostAndPort replica,
                                                     String path,
                                                     String method) throws IOException {
    return buildConnectionToReplica(replica, path, method, false, false, null);
  }

  /**
   * @param replica host and port of replica to connect to
   * @param path URL to access (relative to context root)
   * @param method HTTP method to use
   * @param doOutput if true, will need to write data into the request body
   * @param chunkedStreaming if true, use chunked streaming to accommodate a large upload, if possible
   * @param requestProperties additional request key/value pairs or {@code null} for none
   */
  private HttpURLConnection buildConnectionToReplica(HostAndPort replica,
                                                     String path,
                                                     String method,
                                                     boolean doOutput,
                                                     boolean chunkedStreaming,
                                                     Map<String,String> requestProperties) throws IOException {
    String contextPath = config.getContextPath();
    if (contextPath != null) {
      path = '/' + contextPath + path;
    }
    String protocol = config.isSecure() ? "https" : "http";    
    URL url;
    try {
      url = new URL(protocol, replica.getHostText(), replica.getPort(), path);
    } catch (MalformedURLException mue) {
      // can't happen
      throw new IllegalStateException(mue);
    }
    log.debug("{} {}", method, url);

    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod(method);
    connection.setDoInput(true);
    connection.setDoOutput(doOutput);
    connection.setUseCaches(false);
    connection.setAllowUserInteraction(false);
    connection.setRequestProperty(HttpHeaders.ACCEPT, DESIRED_RESPONSE_CONTENT_TYPE);
    if (closeConnection) {
      connection.setRequestProperty(HttpHeaders.CONNECTION, "close");
    }
    if (chunkedStreaming) {
      if (needAuthentication) {
        // Must buffer in memory if using authentication since it won't handle the authorization challenge
        log.debug("Authentication is enabled, so ingest data must be buffered in memory");
      } else {
        connection.setChunkedStreamingMode(0); // Use default buffer size
      }
    }
    if (requestProperties != null) {
      for (Map.Entry<String,String> entry : requestProperties.entrySet()) {
        connection.setRequestProperty(entry.getKey(), entry.getValue());
      }
    }
    return connection;
  }

  /**
   * @param unnormalizedID ID value that determines partition
   */
  private Iterable<HostAndPort> choosePartitionAndReplicas(long unnormalizedID) {
    List<HostAndPort> replicas = partitions.get(LangUtils.mod(unnormalizedID, partitions.size()));
    int numReplicas = replicas.size();
    if (numReplicas <= 1) {
      return replicas;
    }
    // Fix first replica; cycle through remainder in order since the remainder doesn't matter
    int currentReplica = LangUtils.mod(RandomUtils.md5HashToLong(unnormalizedID), numReplicas);
    List<HostAndPort> rotatedReplicas = Lists.newArrayListWithCapacity(numReplicas);
    for (int i = 0; i < numReplicas; i++) {
      rotatedReplicas.add(replicas.get(currentReplica));
      if (++currentReplica == numReplicas) {
        currentReplica = 0;
      }
    }
    return rotatedReplicas;
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
    doSetOrRemovePreference(userID, itemID, value, true);
  }

  @Override
  public void removePreference(long userID, long itemID) throws TasteException {
    doSetOrRemovePreference(userID, itemID, 1.0f, false); // 1.0 is a dummy value that gets ignored
  }

  private void doSetOrRemovePreference(long userID, long itemID, float value, boolean set) throws TasteException {
    doSetOrRemove("/pref/" + userID + '/' + itemID, userID, value, set);
  }
  
  private void doSetOrRemove(String path, long unnormalizedID, float value, boolean set) throws TasteException {
    boolean sendValue = value != 1.0f;
    Map<String,String> requestProperties;
    byte[] bytes;
    if (sendValue) {
      requestProperties = Maps.newHashMapWithExpectedSize(2);
      bytes = Float.toString(value).getBytes(Charsets.UTF_8);
      requestProperties.put(HttpHeaders.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
      requestProperties.put(HttpHeaders.CONTENT_LENGTH, Integer.toString(bytes.length));
    } else {
      requestProperties = null;
      bytes = null;
    }

    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(unnormalizedID)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica,
                                              path,
                                              set ? "POST" : "DELETE",
                                              sendValue,
                                              false,
                                              requestProperties);
        if (sendValue) {
          OutputStream out = connection.getOutputStream();
          out.write(bytes);
          out.close();
        }
        // Should not be able to return Not Available status
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
          throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
        return;
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", path, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", path, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }

  @Override
  public void setUserTag(long userID, String tag) throws TasteException {
    setUserTag(userID, tag, 1.0f);
  }

  @Override
  public void setUserTag(long userID, String tag, float value) throws TasteException {
    Preconditions.checkNotNull(tag);    
    Preconditions.checkArgument(!tag.isEmpty());
    doSetOrRemove("/tag/user/" + userID + '/' + IOUtils.urlEncode(tag), userID, value, true);
  }

  @Override
  public void setItemTag(String tag, long itemID) throws TasteException {
    setItemTag(tag, itemID, 1.0f);
  }

  @Override
  public void setItemTag(String tag, long itemID, float value) throws TasteException {
    setItemTag(tag, itemID, value, null);
  }
  
  /**
   * Like {@link #setItemTag(String, long, float)}, but allows caller to specify the user for
   * which the request is being made. This information does not directly affect the computation,
   * but affects <em>routing</em> of the request in a distributed context. This is always recommended
   * when there is a user in whose context the request is being made, as it will ensure that the
   * request can take into account all the latest information from the user, including a very new
   * item like {@code itemID}.
   */
  public void setItemTag(String tag, long itemID, float value, Long contextUserID) throws TasteException {  
    Preconditions.checkNotNull(tag);
    Preconditions.checkArgument(!tag.isEmpty());
    long idToPartitionOn = contextUserID == null ? itemID : contextUserID;    
    doSetOrRemove("/tag/item/" + itemID + '/' + IOUtils.urlEncode(tag), idToPartitionOn, value, true);
  }

  /**
   * @param userID user ID whose preference is to be estimated
   * @param itemID item ID to estimate preference for
   * @return an estimate of the strength of the association between the user and item. These values are the
   *  same as will be returned from {@link #recommend(long, int)}. They are opaque values and have no interpretation
   *  other than that larger means stronger. The values are typically in the range [0,1] but are not guaranteed
   *  to be so. Note that 0 will be returned if the user or item is not known in the data.
   * @throws NotReadyException if the recommender has no model available yet
   * @throws TasteException if another error occurs
   */
  @Override
  public float estimatePreference(long userID, long itemID) throws TasteException {
    return estimatePreferences(userID, itemID)[0];
  }

  @Override
  public float[] estimatePreferences(long userID, long... itemIDs) throws TasteException {
    StringBuilder urlPath = new StringBuilder();
    urlPath.append("/estimate/");
    urlPath.append(userID);
    for (long itemID : itemIDs) {
      urlPath.append('/').append(itemID);
    }
    
    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(userID)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath.toString(), "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            BufferedReader reader = IOUtils.bufferStream(connection.getInputStream());
            try {
              float[] result = new float[itemIDs.length];
              for (int i = 0; i < itemIDs.length; i++) {
                result[i] = LangUtils.parseFloat(reader.readLine());
              }
              return result;
            } finally {
              Closeables.close(reader, true);
            }
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }
  
  @Override
  public float estimateForAnonymous(long toItemID, long[] itemIDs) throws TasteException {
    return estimateForAnonymous(toItemID, itemIDs, null);
  }
  
  @Override
  public float estimateForAnonymous(long toItemID, long[] itemIDs, float[] values) throws TasteException {
    return estimateForAnonymous(toItemID, itemIDs, values, null);
  }
  
  public float estimateForAnonymous(long toItemID, long[] itemIDs, float[] values, Long contextUserID) 
      throws TasteException {  
    Preconditions.checkArgument(values == null || values.length == itemIDs.length,
                                "Number of values doesn't match number of items");
    StringBuilder urlPath = new StringBuilder(32);
    urlPath.append("/estimateForAnonymous/");
    urlPath.append(toItemID);
    for (int i = 0; i < itemIDs.length; i++) {
      urlPath.append('/').append(itemIDs[i]);
      if (values != null) {
        urlPath.append('=').append(values[i]);
      }
    }

    // Requests are typically partitioned by user, but this request does not directly depend on a user.
    // If a user ID is supplied anyway, use it for partitioning since it will follow routing for other
    // requests related to that user. Otherwise just partition on the "to" item ID, which is at least
    // deterministic.
    long idToPartitionOn = contextUserID == null ? toItemID : contextUserID;
    
    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(idToPartitionOn)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath.toString(), "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            BufferedReader reader = IOUtils.bufferStream(connection.getInputStream());
            try {
              return LangUtils.parseFloat(reader.readLine());
            } finally {
              Closeables.close(reader, true);
            }
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchItemException(Arrays.toString(itemIDs) + ' ' + toItemID);
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }

  /**
   * Like {@link #recommend(long, int, boolean, IDRescorer)}, and sets {@code considerKnownItems} to {@code false}
   * and {@code rescorer} to {@code null}.
   */
  @Override
  public List<RecommendedItem> recommend(long userID, int howMany) throws TasteException {
    return recommend(userID, howMany, false, (String[]) null);
  }

  /**
   * @param userID user for which recommendations are to be computed
   * @param howMany desired number of recommendations
   * @param considerKnownItems if true, items that the user is already associated to are candidates
   *  for recommendation. Normally this is {@code false}.
   * @param rescorerParams optional parameters to send to the server's {@code RescorerProvider}
   * @return {@link List} of recommended {@link RecommendedItem}s, ordered from most strongly recommend to least
   * @throws NoSuchUserException if the user is not known in the model
   * @throws NotReadyException if the recommender has no model available yet
   * @throws TasteException if another error occurs
   * @throws UnsupportedOperationException if rescorer is not null
   */
  public List<RecommendedItem> recommend(long userID,
                                         int howMany,
                                         boolean considerKnownItems,
                                         String[] rescorerParams) throws TasteException {

    StringBuilder urlPath = new StringBuilder();
    urlPath.append("/recommend/");
    urlPath.append(userID);
    appendCommonQueryParams(howMany, considerKnownItems, rescorerParams, urlPath);

    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(userID)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath.toString(), "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            return consumeItems(connection);
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchUserException(userID);
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }

  private static List<RecommendedItem> consumeItems(HttpURLConnection connection) throws IOException {
    List<RecommendedItem> result = Lists.newArrayList();
    BufferedReader reader = IOUtils.bufferStream(connection.getInputStream());
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        Iterator<String> tokens = COMMA.split(line).iterator();
        long itemID = Long.parseLong(tokens.next());
        float value = LangUtils.parseFloat(tokens.next());
        result.add(new GenericRecommendedItem(itemID, value));
      }
    } finally {
      Closeables.close(reader, true);
    }
    return result;
  }

  /**
   * @param userIDs users for which recommendations are to be computed
   * @param howMany desired number of recommendations
   * @param considerKnownItems if true, items that the user is already associated to are candidates
   *  for recommendation. Normally this is {@code false}.
   * @param rescorerParams optional parameters to send to the server's {@code RescorerProvider}
   * @return {@link List} of recommended {@link RecommendedItem}s, ordered from most strongly recommend to least
   * @throws NoSuchUserException if <em>none</em> of {@code userIDs} are known in the model. Otherwise unknown
   *  user IDs are ignored.
   * @throws NotReadyException if the recommender has no model available yet
   * @throws TasteException if another error occurs
   * @throws UnsupportedOperationException if rescorer is not null
   */
  public List<RecommendedItem> recommendToMany(long[] userIDs,
                                               int howMany,
                                               boolean considerKnownItems,
                                               String[] rescorerParams) throws TasteException {

    StringBuilder urlPath = new StringBuilder(32);
    urlPath.append("/recommendToMany");
    for (long userID : userIDs) {
      urlPath.append('/').append(userID);
    }
    appendCommonQueryParams(howMany, considerKnownItems, rescorerParams, urlPath);

    // Note that this assumes that all user IDs are on the same partition. It will fail at request
    // time if not since the partition of the first user doesn't contain the others.
    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(userIDs[0])) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath.toString(), "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            return consumeItems(connection);
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchUserException(Arrays.toString(userIDs));
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }

  @Override
  public List<RecommendedItem> recommendToAnonymous(long[] itemIDs, int howMany) throws TasteException {
    return recommendToAnonymous(itemIDs, null, howMany);
  }

  @Override
  public List<RecommendedItem> recommendToAnonymous(long[] itemIDs, float[] values, int howMany)
      throws TasteException {
    return recommendToAnonymous(itemIDs, values, howMany, null, null);
  }

  /**
   * Like {@link #recommendToAnonymous(long[], float[], int)}, but allows caller to specify the user for
   * which the request is being made. This information does not directly affect the computation,
   * but affects <em>routing</em> of the request in a distributed context. This is always recommended
   * when there is a user in whose context the request is being made, as it will ensure that the
   * request can take into account all the latest information from the user, including very new
   * items that may be in {@code itemIDs}.
   */
  public List<RecommendedItem> recommendToAnonymous(long[] itemIDs,
                                                    float[] values,
                                                    int howMany,
                                                    String[] rescorerParams,
                                                    Long contextUserID) throws TasteException {
    return anonymousOrSimilar(itemIDs, values, howMany, "/recommendToAnonymous", rescorerParams, contextUserID);
  }

  @Override
  public List<RecommendedItem> mostPopularItems(int howMany) throws TasteException {
    StringBuilder urlPath = new StringBuilder(32);
    urlPath.append("/mostPopularItems");
    appendCommonQueryParams(howMany, false, null, urlPath);

    // Always send to partition 0 for consistency    
    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(0L)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath.toString(), "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            return consumeItems(connection);
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
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
   * @throws NoSuchItemException if <em>none</em> of {@code itemIDs} exist in the model. Otherwise, unknown
   *  items are ignored.
   * @throws NotReadyException if the recommender has no model available yet
   * @throws TasteException if another error occurs
   */
  @Override
  public List<RecommendedItem> mostSimilarItems(long[] itemIDs, int howMany) throws TasteException {
    return mostSimilarItems(itemIDs, howMany, null, null);
  }

  /**
   * Like {@link #mostSimilarItems(long[], int)}, but allows caller to specify the user for which the request
   * is being made. This information does not directly affect the computation, but affects <em>routing</em>
   * of the request in a distributed context. This is always recommended when there is a user in whose context
   * the request is being made, as it will ensure that the request can take into account all the latest information
   * from the user, including very new items that may be in {@code itemIDs}.
   */
  public List<RecommendedItem> mostSimilarItems(long[] itemIDs,
                                                int howMany,
                                                String[] rescorerParams,
                                                Long contextUserID) throws TasteException {
    return anonymousOrSimilar(itemIDs, null, howMany, "/similarity", rescorerParams, contextUserID);
  }

  private List<RecommendedItem> anonymousOrSimilar(long[] itemIDs,
                                                   float[] values,
                                                   int howMany,
                                                   String path,
                                                   String[] rescorerParams,
                                                   Long contextUserID) throws TasteException {
    Preconditions.checkArgument(values == null || values.length == itemIDs.length,
                                "Number of values doesn't match number of items");
    StringBuilder urlPath = new StringBuilder();
    urlPath.append(path);
    for (int i = 0; i < itemIDs.length; i++) {
      urlPath.append('/').append(itemIDs[i]);
      if (values != null) {
        urlPath.append('=').append(values[i]);
      }
    }
    appendCommonQueryParams(howMany, false, rescorerParams, urlPath);

    // Requests are typically partitioned by user, but this request does not directly depend on a user.
    // If a user ID is supplied anyway, use it for partitioning since it will follow routing for other
    // requests related to that user. Otherwise just partition on (first0 item ID, which is at least
    // deterministic.
    long idToPartitionOn = contextUserID == null ? itemIDs[0] : contextUserID;
    
    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(idToPartitionOn)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath.toString(), "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            return consumeItems(connection);
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchItemException(Arrays.toString(itemIDs));
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }

  /**
   * One-argument version of {@link #mostSimilarItems(long[], int)}.
   */
  @Override
  public List<RecommendedItem> mostSimilarItems(long itemID, int howMany) throws TasteException {
    return mostSimilarItems(new long[] { itemID }, howMany);
  }

  @Override
  public float[] similarityToItem(long toItemID, long... itemIDs) throws TasteException {
    return similarityToItem(toItemID, itemIDs, null);
  }

  /**
   * Like {@link #similarityToItem(long, long[])}, but allows caller to specify the user for which the request
   * is being made. This information does not directly affect the computation, but affects <em>routing</em>
   * of the request in a distributed context. This is always recommended when there is a user in whose context
   * the request is being made, as it will ensure that the request can take into account all the latest information
   * from the user, including very new items that may be in {@code itemIDs}.
   */
  public float[] similarityToItem(long toItemID, long[] itemIDs, Long contextUserID) throws TasteException {
    StringBuilder urlPath = new StringBuilder(32);
    urlPath.append("/similarityToItem/");
    urlPath.append(toItemID);
    for (long itemID : itemIDs) {
      urlPath.append('/').append(itemID);
    }
    
    // Requests are typically partitioned by user, but this request does not directly depend on a user.
    // If a user ID is supplied anyway, use it for partitioning since it will follow routing for other
    // requests related to that user. Otherwise just partition on (first0 item ID, which is at least
    // deterministic.
    long idToPartitionOn = contextUserID == null ? itemIDs[0] : contextUserID;
    
    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(idToPartitionOn)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath.toString(), "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            BufferedReader reader = IOUtils.bufferStream(connection.getInputStream());
            try {
              float[] result = new float[itemIDs.length];
              for (int i = 0; i < itemIDs.length; i++) {
                result[i] = LangUtils.parseFloat(reader.readLine());
              }
              return result;
            } finally {
              Closeables.close(reader, true);
            }
          case HttpURLConnection.HTTP_NOT_FOUND:
            throw new NoSuchItemException(connection.getResponseMessage());
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
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
    String urlPath = "/because/" + userID + '/' + itemID + "?howMany=" + howMany;
    
    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(userID)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath, "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            return consumeItems(connection);
          case HttpURLConnection.HTTP_NOT_FOUND:
            String connectionMessage = connection.getResponseMessage();
            if (connectionMessage != null &&
                connectionMessage.contains(NoSuchUserException.class.getSimpleName())) {
              throw new NoSuchUserException(userID);
            } else {
              throw new NoSuchItemException(itemID);
            }
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
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
  public void ingest(Reader reader) throws TasteException {
    Map<Integer,Pair<Writer,HttpURLConnection>> writersAndConnections =
        Maps.newHashMapWithExpectedSize(partitions.size());
    BufferedReader buffered = IOUtils.buffer(reader);
    try {
      try {
        String line;
        while ((line = buffered.readLine()) != null) {
          if (line.isEmpty() || line.charAt(0) == '#') {
            continue;
          }
          long userID;
          try {
            userID = Long.parseLong(COMMA.split(line).iterator().next());
          } catch (NoSuchElementException nsee) {
            throw new TasteException(nsee);
          } catch (NumberFormatException nfe) {
            throw new TasteException(nfe);
          }
          int partition = LangUtils.mod(userID, partitions.size());
          Pair<Writer,HttpURLConnection> writerAndConnection = writersAndConnections.get(partition);
          if (writerAndConnection == null) {
            HttpURLConnection connection = buildConnectionToAReplica(partition);
            Writer writer = IOUtils.buildGZIPWriter(connection.getOutputStream());
            writerAndConnection = new Pair<Writer,HttpURLConnection>(writer, connection);
            writersAndConnections.put(partition, writerAndConnection);
          }
          // TODO this doesn't automatically find another replica if this write fails
          Writer writer = writerAndConnection.getFirst();
          writer.write(line);
          writer.write('\n');
        }
        for (Pair<Writer,HttpURLConnection> writerAndConnection : writersAndConnections.values()) {
          // Want to know of output stream close failed -- maybe failed to write
          writerAndConnection.getFirst().close();
          HttpURLConnection connection = writerAndConnection.getSecond();
          if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
          }
        }
      } finally {
        for (Pair<Writer,HttpURLConnection> writerAndConnection : writersAndConnections.values()) {
          Closeables.close(writerAndConnection.getFirst(), true);
          writerAndConnection.getSecond().disconnect();
        }
      }
    } catch (IOException ioe) {
      throw new TasteException(ioe);
    }
  }

  private HttpURLConnection buildConnectionToAReplica(int partition) throws TasteException {
    String urlPath = "/ingest";
    
    TasteException savedException = null;
    for (HostAndPort replica : partitions.get(partition)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath, "POST", true, true, INGEST_REQUEST_PROPS);
        connection.connect();
        return connection;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }

  @Deprecated
  @Override
  public void refresh(Collection<Refreshable> alreadyRefreshed) {
    if (alreadyRefreshed != null) {
      log.warn("Ignoring argument {}", alreadyRefreshed);
    }
    refresh();
  }

  /**
   * Requests that the Serving Layer recompute its models. This is a request, and may or may not result
   * in an update.
   */
  @Override
  public void refresh() {
    int numPartitions = partitions.size();
    for (int i = 0; i < numPartitions; i++) {
      refreshPartition(i);
    }
  }

  private void refreshPartition(int partition) {
    String urlPath = "/refresh";
    
    for (HostAndPort replica : partitions.get(partition)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath, "POST");
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
          log.warn("Unable to refresh partition {} ({} {}); continuing",
                   partition, connection.getResponseCode(), connection.getResponseMessage());
          // Yes, continue
        }
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
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
   * @return {@link #recommend(long, int, boolean, String[])} if rescorer is null
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #recommend(long, int, boolean, String[])} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> recommend(long userID,
                                         int howMany,
                                         boolean considerKnownItems,
                                         IDRescorer rescorer) throws TasteException {
    if (rescorer != null) {
      throw new UnsupportedOperationException();
    }
    return recommend(userID, howMany, considerKnownItems, (String[]) null);
  }

  @Deprecated
  @Override
  public List<RecommendedItem> recommendToMany(long[] userIDs,
                                               int howMany,
                                               boolean considerKnownItems,
                                               IDRescorer rescorer) throws TasteException {
    if (rescorer != null) {
      throw new UnsupportedOperationException();
    }
    return recommendToMany(userIDs, howMany, considerKnownItems, (String[]) null);
  }

  /**
   * Note that {@link IDRescorer} is not supported in the client now and must be null.
   *
   * @return {@link #recommendToAnonymous(long[], int)} if rescorer is null
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #recommendToAnonymous(long[], int)} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> recommendToAnonymous(long[] itemIDs,
                                                    int howMany,
                                                    IDRescorer rescorer) throws TasteException {
    if (rescorer != null) {
      throw new UnsupportedOperationException();
    }
    return recommendToAnonymous(itemIDs, howMany);
  }

  /**
   * Note that {@link IDRescorer} is not supported in the client now and must be null.
   *
   * @return {@link #recommendToAnonymous(long[], float[], int)} if rescorer is null
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #recommendToAnonymous(long[], float[], int)} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> recommendToAnonymous(long[] itemIDs,
                                                    float[] values,
                                                    int howMany,
                                                    IDRescorer rescorer) throws TasteException {
    if (rescorer != null) {
      throw new UnsupportedOperationException();
    }
    return recommendToAnonymous(itemIDs, values, howMany);
  }

  /**
   * Note that {@link IDRescorer} is not supported in the client now and must be null.
   *
   * @return {@link #mostPopularItems(int)} if rescorer is null
   * @throws UnsupportedOperationException otherwise
   * @deprecated use {@link #mostPopularItems(int)} instead
   */
  @Deprecated
  @Override
  public List<RecommendedItem> mostPopularItems(int howMany, IDRescorer rescorer) throws TasteException {
    if (rescorer != null) {
      throw new UnsupportedOperationException();
    }
    return mostPopularItems(howMany);
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
    int numPartitions = partitions.size();
    for (int i = 0; i < numPartitions; i++) {
      if (!isPartitionReady(i)) {
        return false;
      }
    }
    return true;
  }

  private boolean isPartitionReady(int partition) throws TasteException {
    String urlPath = "/ready";
    
    TasteException savedException = null;
    for (HostAndPort replica : partitions.get(partition)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath, "HEAD");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            return true;
          case HttpURLConnection.HTTP_UNAVAILABLE:
            return false;
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }

  @Override
  public void await() throws TasteException, InterruptedException {
    while (!isReady()) {
      Thread.sleep(1000L);
    }
  }
  
  @Override
  public boolean await(long time, TimeUnit unit) throws TasteException, InterruptedException {
    Preconditions.checkArgument(time >= 0L, "time must be positive: {}", time);
    Preconditions.checkNotNull(unit);
    long waitForMS = TimeUnit.MILLISECONDS.convert(time, unit);
    long waitIntervalMS = FastMath.min(1000L, waitForMS);
    Stopwatch stopwatch = new Stopwatch().start();
    while (!isReady()) {
      if (stopwatch.elapsed(TimeUnit.MILLISECONDS) > waitForMS) {
        return false;
      }
      Thread.sleep(waitIntervalMS);
    }
    return true;
  }

  @Override
  public FastIDSet getAllUserIDs() throws TasteException {
    FastIDSet result = new FastIDSet();
    int numPartitions = partitions.size();
    for (int i = 0; i < numPartitions; i++) {
      getAllIDsFromPartition(i, true, result);
    }
    return result;
  }


  @Override
  public FastIDSet getAllItemIDs() throws TasteException {
    // Yes, loop over all partitions. Most item IDs will be returned from all partitions but it's
    // possible for some to exist only on a few.
    FastIDSet result = new FastIDSet();
    int numPartitions = partitions.size();
    for (int i = 0; i < numPartitions; i++) {
      getAllIDsFromPartition(i, false, result);
    }
    return result;
  }

  private void getAllIDsFromPartition(int partition, boolean user, FastIDSet result) throws TasteException {
    String urlPath = '/' + (user ? "user" : "item") + "/allIDs";
    
    TasteException savedException = null;
    for (HostAndPort replica : partitions.get(partition)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath, "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            consumeIDs(connection, result);
            return;
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }

  private static void consumeIDs(HttpURLConnection connection, FastIDSet result) throws IOException {
    BufferedReader reader = IOUtils.bufferStream(connection.getInputStream());
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        result.add(Long.parseLong(line));
      }
    } finally {
      Closeables.close(reader, true);
    }
  }

  @Override
  public int getNumUserClusters() throws TasteException {
    return getNumClusters(true);
  }

  @Override
  public int getNumItemClusters() throws TasteException {
    return getNumClusters(false);
  }

  private int getNumClusters(boolean user) throws TasteException {
    String urlPath = '/' + (user ? "user" : "item") + "/clusters/count";
    
    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(0L)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath, "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            BufferedReader reader = IOUtils.bufferStream(connection.getInputStream());
            try {
              return Integer.parseInt(reader.readLine());
            } finally {
              Closeables.close(reader, true);
            }          
          case HttpURLConnection.HTTP_NOT_IMPLEMENTED:
            throw new UnsupportedOperationException();
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }

  @Override
  public FastIDSet getUserCluster(int n) throws TasteException {
    return getCluster(n, true);
  }

  @Override
  public FastIDSet getItemCluster(int n) throws TasteException {
    return getCluster(n, false);
  }

  private FastIDSet getCluster(int n, boolean user) throws TasteException {
    String urlPath = '/' + (user ? "user" : "item") + "/clusters/" + n;
    
    TasteException savedException = null;
    for (HostAndPort replica : choosePartitionAndReplicas(0L)) {
      HttpURLConnection connection = null;
      try {
        connection = buildConnectionToReplica(replica, urlPath, "GET");
        switch (connection.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            FastIDSet members = new FastIDSet();
            consumeIDs(connection, members);
            return members;          case HttpURLConnection.HTTP_NOT_IMPLEMENTED:
            throw new UnsupportedOperationException();
          case HttpURLConnection.HTTP_UNAVAILABLE:
            throw new NotReadyException();
          default:
            throw new TasteException(connection.getResponseCode() + " " + connection.getResponseMessage());
        }
      } catch (TasteException te) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, te.toString());        
        savedException = te;
      } catch (IOException ioe) {
        log.info("Can't access {} at {}: ({})", urlPath, replica, ioe.toString());
        savedException = new TasteException(ioe);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }
    throw savedException;
  }

  private static void appendCommonQueryParams(int howMany,
                                              boolean considerKnownItems,
                                              String[] rescorerParams,
                                              StringBuilder urlPath) {
    urlPath.append("?howMany=").append(howMany);
    if (considerKnownItems) {
      urlPath.append("&considerKnownItems=true");
    }
    if (rescorerParams != null) {
      for (String rescorerParam : rescorerParams) {
        urlPath.append("&rescorerParams=").append(IOUtils.urlEncode(rescorerParam));
      }
    }
  }

}
