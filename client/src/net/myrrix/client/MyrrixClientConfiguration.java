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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import org.apache.mahout.common.Pair;

import net.myrrix.common.PartitionsUtils;

/**
 * Encapsulates all configuration for a {@link ClientRecommender}.
 *
 * @author Sean Owen
 */
public final class MyrrixClientConfiguration {

  public static final String DEFAULT_HOST = "localhost";
  public static final int DEFAULT_PORT = 80;
  public static final String AUTO_PARTITION_SPEC = "auto";

  private String host;
  private int port;
  private boolean secure;
  private String contextPath;
  private String keystoreFilePath;
  private String keystorePassword;
  private String userName;
  private String password;
  private String allPartitionsSpecification;
  private List<List<Pair<String,Integer>>> partitions;

  public MyrrixClientConfiguration() {
    host = DEFAULT_HOST;
    port = DEFAULT_PORT;
  }

  /**
   * @return host containing the Serving Layer, if not in distributed mode
   */
  public String getHost() {
    return host;
  }

  /**
   * @param host Serving Layer host to communicate with. Defaults to {@link #DEFAULT_PORT}.
   */
  public void setHost(String host) {
    Preconditions.checkState(allPartitionsSpecification == null, "allPartitionsSpecification already set");
    this.host = host;
  }

  /**
   * @return port on which to access the Serving Layer, if not in distributed mode. Defaults to {@link #DEFAULT_PORT}.
   */
  public int getPort() {
    return port;
  }

  /**
   * Cannot be set if {@link #setAllPartitionsSpecification(String)} is set
   * @param port Serving Layer port to communicate with
   */
  public void setPort(int port) {
    Preconditions.checkArgument(port > 0);
    Preconditions.checkState(allPartitionsSpecification == null, "allPartitionsSpecification already set");
    this.port = port;
  }

  /**
   * @return if true, this client is accessing the Serving Layer over HTTPS, not HTTP
   */
  public boolean isSecure() {
    return secure;
  }

  /**
   * @param secure if true, this client is accessing the Serving Layer over HTTPS, not HTTP
   */
  public void setSecure(boolean secure) {
    this.secure = secure;
  }

  /**
   * @return the context path under which the target Serving Layer app is deployed (e.g.
   *  {@code http://example.org/contextPath/...}), or {@code null} if the default root context
   *  should be used.
   */
  public String getContextPath() {
    return contextPath;
  }

  public void setContextPath(String contextPath) {
    this.contextPath = contextPath;
  }

  /**
   * @return the keystore file containing the server's SSL keys. Only necessary when accessing a server with a
   *  temporary self-signed key, which is not by default trusted by the Java SSL implementation
   */
  public String getKeystoreFilePath() {
    return keystoreFilePath;
  }

  public void setKeystoreFilePath(String keystoreFilePath) {
    this.keystoreFilePath = keystoreFilePath;
  }

  /**
   * @return password for {@link #getKeystoreFilePath()}
   */
  public String getKeystorePassword() {
    return keystorePassword;
  }

  public void setKeystorePassword(String keystorePassword) {
    this.keystorePassword = keystorePassword;
  }

  /**
   * @return user name needed to access the Serving Layer, if any
   */
  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  /**
   * @return password needed to access the Serving Layer, if any
   */
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * @return specification for all servers that have partitions. Only applicable in distributed mode and returns
   *  {@code null} otherwise. May be specified as "auto", in which case {@link #getHost()} and {@link #getPort()}
   *  must be valid, since this host will be queried for partition details. Otherwise, Serving Layers are specified
   *  explicitly as "host:port" pairs. Replicas are specified as many Serving Layers, separated by commas, like
   *  "rep1:port1,rep2:port2,...". Finally, partitions are specified as multiple replicas separated by semicolon,
   *  like "part1rep1:port11,part1rep2:port12;part2rep1:port21,part2rep2:port22;...". Example:
   *  "foo:80,foo2:8080;bar:8080;baz2:80,baz3:80"
   */
  public String getAllPartitionsSpecification() {
    return allPartitionsSpecification;
  }

  /**
   * Cannot be set if {@link #setHost(String)} was already set.
   * @param allPartitionsSpecification see {@link #getAllPartitionsSpecification()}
   */
  public void setAllPartitionsSpecification(String allPartitionsSpecification) {
    this.allPartitionsSpecification = allPartitionsSpecification;
    if (AUTO_PARTITION_SPEC.equals(allPartitionsSpecification)) {
      try {
        this.partitions = parseAutoPartitionSpecification();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    } else {
      this.partitions = PartitionsUtils.parseAllPartitions(allPartitionsSpecification);
    }
  }

  private List<List<Pair<String,Integer>>> parseAutoPartitionSpecification() throws IOException {
    String scheme = isSecure() ? "https" : "http";
    URL statusURL;
    try {
      statusURL = new URL(scheme, host, port, "/status.jspx");
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e); // Can't happen
    }
    return PartitionsUtils.parsePartitionsFromStatus(statusURL);
  }

  /**
   * @return a description of all partitions. This is a parsing of {@link #getAllPartitionsSpecification()} if set,
   *  or a description of a 1-partition, 1-replica system containing the host:port specified by {@link #getHost()}
   *  and {@link #getPort()} if not
   */
  public List<List<Pair<String,Integer>>> getPartitions() {
    return partitions == null ?
        Collections.singletonList(Collections.singletonList(Pair.of(host, port))) :
        partitions;
  }

}
