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

package net.myrrix.common;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Utility methods for dealing with partitions in distributed mode.
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class PartitionsUtils {

  private static final Logger log = LoggerFactory.getLogger(PartitionsUtils.class);

  private static final Pattern SEMICOLON = Pattern.compile(";");
  private static final Pattern COMMA = Pattern.compile(",");
  private static final Pattern COLON = Pattern.compile(":");

  private PartitionsUtils() {
  }

  /**
   * @param value describes all partitions, when partitioning across Serving Layers
   *  by user. Each partition may have multiple replicas. Serving Layers are specified as "host:port".
   *  Replicas are specified as many Serving Layers, separated by commas, like "rep1:port1,rep2:port2,...".
   *  Finally, partitions are specified as multiple replicas separated by semicolon, like
   *  "part1rep1:port11,part1rep2:port12;part2rep1:port21,part2rep2:port22;...". Example:
   *  "foo:80,foo2:8080;bar:8080;baz2:80,baz3:80"
   * @return {@link List} of partitions, where each partition is a {@link List} of replicas, where each
   *  replica is a host-port pair, as {@link HostAndPort}
   */
  public static List<List<HostAndPort>> parseAllPartitions(CharSequence value) {
    if (value == null) {
      return null;
    }
    List<List<HostAndPort>> allPartitions = Lists.newArrayList();
    for (CharSequence partitionString : SEMICOLON.split(value)) {
      List<HostAndPort> partition = Lists.newArrayList();
      for (CharSequence replicaString : COMMA.split(partitionString)) {
        String[] hostPort = COLON.split(replicaString);
        String host = hostPort[0];
        int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 80;
        Preconditions.checkArgument(port > 0, "port must be positive: %s", port);
        partition.add(HostAndPort.fromParts(host, port));
      }
      Preconditions.checkArgument(!partition.isEmpty(), "At least one partition must be specified");
      allPartitions.add(partition);
    }

    log(allPartitions);
    return allPartitions;
  }

  /**
   * @param port port the instance is configured to run on
   * @return a simple structure representing one partition, one replica: the local host
   *  and configured instance port
   */
  public static List<List<HostAndPort>> getDefaultLocalPartition(int port) {
    InetAddress localhost;
    try {
      localhost = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
    String host = localhost.getHostName();
    return Collections.singletonList(Collections.singletonList(HostAndPort.fromParts(host, port)));
  }

  /**
   * Reads partition information from an XML status document from the cluster, which includes information
   * on partitions and their replicas.
   * 
   * @param url URL holding cluster status as an XML document
   * @return a {@link List} of partitions, each of which is a {@link List} of replicas in a partition,
   *  each represented as a {@link HostAndPort}
   * @throws IOException if the URL can't be accessed or its XML content parsed
   */
  public static List<List<HostAndPort>> parsePartitionsFromStatus(URL url) throws IOException {

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    try {
      builder = factory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }

    Document doc;
    Reader reader = new InputStreamReader(url.openStream(), Charsets.UTF_8);
    try {
      doc = builder.parse(new InputSource(reader));
    } catch (SAXException saxe) {
      throw new IllegalStateException(saxe);
    } finally {
      reader.close();
    }

    Element docElement = doc.getDocumentElement();
    docElement.normalize();

    List<List<HostAndPort>> result = Lists.newArrayList();

    NodeList partitionElements = docElement.getElementsByTagName("partition");
    for (int i = 0; i < partitionElements.getLength(); i++) {
      List<HostAndPort> partitionResult = Lists.newArrayList();
      result.add(partitionResult);
      Element partitionElement = (Element) partitionElements.item(i);
      NodeList replicaElements = partitionElement.getElementsByTagName("replica");
      for (int j = 0; j < replicaElements.getLength(); j++) {
        Node replicaElement = replicaElements.item(j);
        String[] hostPort = COLON.split(replicaElement.getTextContent());
        partitionResult.add(HostAndPort.fromParts(hostPort[0], Integer.parseInt(hostPort[1])));
      }
    }

    return result;
  }

  private static void log(Collection<List<HostAndPort>> allPartitions) {
    if (allPartitions.isEmpty()) {
      log.debug("No partitions parsed");
    } else {
      int partitionNumber = 0;
      for (Iterable<HostAndPort> partition : allPartitions) {
        StringBuilder description = new StringBuilder();
        for (HostAndPort hostPort : partition) {
          description.append(hostPort).append(' ');
        }
        log.debug("Partition {}: {}", partitionNumber, description);
        partitionNumber++;
      }
    }
  }

}
