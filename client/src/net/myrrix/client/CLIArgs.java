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

import java.io.File;
import java.util.List;

import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

/**
 * Command line argument object for {@link CLI}.
 *
 * @author Sean Owen
 * @since 1.0
 */
public interface CLIArgs {

  @Option(description = "Verbose logging")
  boolean isVerbose();

  @Option(defaultValue = "localhost", description = "Serving Layer host name")
  String getHost();

  @Option(defaultValue = "80", description = "Serving Layer port number")
  int getPort();

  @Option(description = "If set, use HTTPS instead of HTTP")
  boolean isSecure();

  @Option(defaultToNull = true, description = "User name to authenticate to Serving Layer")
  String getUserName();

  @Option(defaultToNull = true, description = "Password to authenticate to Serving Layer")
  String getPassword();

  @Option(defaultToNull = true, description = "Test SSL certificate keystore to accept")
  File getKeystoreFile();

  @Option(defaultToNull = true, description = "Password for keystoreFile")
  String getKeystorePassword();

  @Option(defaultToNull = true, description = "All partitions, as comma-separated host:port " +
                                              "(e.g. foo1:8080,foo2:80,bar1:8081), or 'auto'")
  String getAllPartitions();

  @Option(defaultToNull = true,
          description = "May be specified several times to specify arguments to the server's rescorer implementation")
  List<String> getRescorerParams();

  @Option(defaultToNull = true, description = "Non-root URL context path under which Serving Layer is deployed")
  String getContextPath();

  @Option(description = "Use String user IDs in client API")
  boolean isTranslateUser();

  @Option(defaultToNull = true, description = "Use String item IDs in client API. " +
                                              "File argument contains list of all known item IDs or is set to 'oneWay'")
  String getTranslateItem();

  @Option(helpRequest = true)
  boolean getHelp();

  @Unparsed
  List<String> getCommands();

  @Option(defaultValue = "10", description = "How many recommendations or items to retrieve")
  int getHowMany();

  @Option(description = "Allow items which the user is already connected to in " +
                        "the result of the recommend method")
  boolean isConsiderKnownItems();

  @Option(defaultToNull = true,
          description = "For commands that do not directly involve a user, " +
                        "specifies the user for which the request is made for routing")
  String getContextUserID();

}
