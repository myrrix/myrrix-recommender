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

import com.lexicalscope.jewel.cli.Option;

/**
 * Arguments for {@link Runner}.
 *
 * @author Sean Owen
 * @since 1.0
 */
public interface RunnerArgs {

  @Option(defaultToNull = true, description = "Working directory for input and intermediate files")
  File getLocalInputDir();

  @Option(defaultToNull = true, description = "Bucket storing data to access")
  String getBucket();

  @Option(defaultToNull = true, description = "Instance ID to access")
  String getInstanceID();

  @Option(defaultValue = "80", description = "HTTP port number")
  int getPort();

  @Option(defaultValue = "443", description = "HTTPS port number")
  int getSecurePort();

  @Option(defaultToNull = true, description = "Non-root context path to deploy endpoints under")
  String getContextPath();

  @Option(description = "Disables all API methods that add or change data")
  boolean isReadOnly();

  @Option(defaultToNull = true, description = "User name needed to authenticate to this instance")
  String getUserName();

  @Option(defaultToNull = true, description = "Password to authenticate to this instance")
  String getPassword();

  @Option(description = "User name and password only apply to admin and console resources")
  boolean isConsoleOnlyPassword();

  @Option(defaultToNull = true, description = "Test SSL certificate keystore to accept")
  File getKeystoreFile();

  @Option(defaultToNull = true, description = "Password for keystoreFile")
  String getKeystorePassword();
  
  @Option(defaultToNull = true, 
          description = "Max number of requests per minute from a host before it is temporarily blocked")
  Integer getHostRequestLimit();

  @Option(defaultToNull = true, description = "RescorerProvider implementation class")
  String getRescorerProviderClass();

  @Option(defaultToNull = true, description = "ClientThread implementation class")
  String getClientThreadClass();
  
  @Option(defaultToNull = true,
          description = "All partitions, as comma-separated host:port (e.g. foo1:8080,foo2:80,bar1:8081), " +
                        "or \"auto\" (distributed mode only)")
  String getAllPartitions();

  @Option(defaultToNull = true, description = "Server's partition number (0-based)")
  Integer getPartition();

  @Option(defaultToNull = true, description = "License key file [subject].lic")
  File getLicenseFile();

  @Option(helpRequest = true)
  boolean getHelp();

}
