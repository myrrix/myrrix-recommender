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

package net.myrrix.web;

import java.io.File;

import com.google.common.base.Preconditions;

/**
 * Configuration for a {@link Runner}. This configures the Serving Layer server instance that it runs.
 *
 * @author Sean Owen
 */
public final class RunnerConfiguration {

  public static final int DEFAULT_PORT = 80;
  public static final int DEFAULT_SECURE_PORT = 443;
  public static final String AUTO_PARTITION_SPEC = "auto";

  private String bucket;
  private String instanceID;
  private int port;
  private int securePort;
  private boolean readOnly;
  private File keystoreFile;
  private String keystorePassword;
  private File localInputDir;
  private String userName;
  private String password;
  private boolean consoleOnlyPassword;
  private String rescorerProviderClassName;
  private Integer partition;
  private String allPartitionsSpecification;
  private File licenseFile;

  public RunnerConfiguration() {
    port = DEFAULT_PORT;
    securePort = DEFAULT_SECURE_PORT;
  }

  /**
   * @return bucket name under which to locate Computation Layer data in distributed mode.
   *  Used with {@link #getInstanceID()}.
   */
  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  /**
   * @return instance ID that the Serving Layer will use, when interacting with a Computation Layer in
   *  distributed mode. Used with {@link #getBucket()}.
   */
  public String getInstanceID() {
    return instanceID;
  }

  public void setInstanceID(String instanceID) {
    this.instanceID = instanceID;
  }

  /**
   * @return port on which the Serving Layer listens for non-secure HTTP connections
   */
  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    Preconditions.checkArgument(port >= 0, "port must be nonnegative: %s", port);
    this.port = port;
  }

  /**
   * @return port on which the Serving Layer listens for secure HTTPS connections, if applicable
   */
  public int getSecurePort() {
    return securePort;
  }

  public void setSecurePort(int securePort) {
    Preconditions.checkArgument(securePort >= 0, "securePort must be nonnegative: %s", securePort);
    this.securePort = securePort;
  }

  /**
   * @return if true, disable API methods that add or change data
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  /**
   * @return the keystore file containing the server's SSL keys. Only necessary when accessing a server with a
   *  temporary self-signed key, which is not by default trusted by the Java SSL implementation
   */
  public File getKeystoreFile() {
    return keystoreFile;
  }

  public void setKeystoreFile(File keystoreFile) {
    this.keystoreFile = keystoreFile;
  }

  /**
   * @return password for {@link #getKeystoreFile()}, if applicable
   */
  public String getKeystorePassword() {
    return keystorePassword;
  }

  public void setKeystorePassword(String keystorePassword) {
    this.keystorePassword = keystorePassword;
  }

  /**
   * @return local directory from which input is read, to which output is written, and in which intermediate
   *  model files are stored
   */
  public File getLocalInputDir() {
    return localInputDir;
  }

  public void setLocalInputDir(File localInputDir) {
    this.localInputDir = localInputDir;
  }

  /**
   * @return user name which must be supplied to access the Serving Layer with HTTP DIGEST authentication,
   *  if applicable; none is required if this is not specified
   */
  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  /**
   * @return password which must be supplied to access the Serving Layer with HTTP DIGEST authentication,
   *  if applicable; none is required if this is not specified
   */
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * @return true if username and password only apply to admin / console resources
   */
  public boolean isConsoleOnlyPassword() {
    return consoleOnlyPassword;
  }

  public void setConsoleOnlyPassword(boolean consoleOnlyPassword) {
    this.consoleOnlyPassword = consoleOnlyPassword;
  }

  /**
   * @return the name of an implementation of {@link net.myrrix.online.RescorerProvider}, if one should be
   *  used to modify the results of the recommendations and similarity computations, or {@code null} if
   *  none is to be used. The class must be made available on the server's classpath. This may also be specified 
   *  as a comma-separated list of class names, in which case all will be applied, in the given order.
   */
  public String getRescorerProviderClassName() {
    return rescorerProviderClassName;
  }

  public void setRescorerProviderClassName(String rescorerProviderClassName) {
    this.rescorerProviderClassName = rescorerProviderClassName;
  }

  /**
   * @return the partition number of this Serving Layer server, among all partitions. Only applicable in distributed
   *  mode, and returns {@code null} otherwise.
   * @see #getAllPartitionsSpecification()
   */
  public Integer getPartition() {
    return partition;
  }

  public void setPartition(Integer partition) {
    if (partition != null) {
      Preconditions.checkArgument(partition >= 0, "Partition should be at least 0");
    }
    this.partition = partition;
  }

  /**
   * @return specification for all servers that have partitions. Only applicable in distributed mode and returns
   *  {@code null} otherwise. May be specified as "auto", in distributed mode only, in which case it will attempt
   *  to discover partitions automatically. Note that this further only works with Amazon AWS / S3
   *  Otherwise, Serving Layers are specified explicitly as "host:port" pairs.
   *  Replicas are specified as many Serving Layers, separated by commas, like "rep1:port1,rep2:port2,...".
   *  Finally, partitions are specified as multiple replicas separated by semicolon, like
   *  "part1rep1:port11,part1rep2:port12;part2rep1:port21,part2rep2:port22;...". Example:
   *  "foo:80,foo2:8080;bar:8080;baz2:80,baz3:80"
   * @see #getPartition()
   */
  public String getAllPartitionsSpecification() {
    return allPartitionsSpecification;
  }

  public void setAllPartitionsSpecification(String allPartitionsSpecification) {
    this.allPartitionsSpecification = allPartitionsSpecification;
  }

  /**
   * @return (Optional in standalone mode). location of a license file named [subject].lic, where [subject] is the
   *  subject name authorized in the license. The license file should be valid at the time the app is
   *  run, and contain authorization to use the amount of parallelism (max simultaneous Hadoop workers)
   *  requested.
   */
  public File getLicenseFile() {
    return licenseFile;
  }

  public void setLicenseFile(File licenseFile) {
    this.licenseFile = licenseFile;
  }


}
