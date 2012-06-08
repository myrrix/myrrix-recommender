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

import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.mahout.common.Pair;

/**
 * Utility methods for dealing with partitions in distributed mode.
 */
public final class PartitionsUtils {

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
   *  replica is a host-port pair, as {@link String} and {@link Integer}
   */
  public static List<List<Pair<String,Integer>>> parseAllPartitions(CharSequence value) {
    if (value == null) {
      return null;
    }
    List<List<Pair<String,Integer>>> allPartitions = Lists.newArrayList();
    for (String partitionString : SEMICOLON.split(value)) {
      List<Pair<String,Integer>> partition = Lists.newArrayList();
      for (String replicaString : COMMA.split(partitionString)) {
        String[] hostPort = COLON.split(replicaString);
        String host = hostPort[0];
        Integer port = Integer.valueOf(hostPort[1]);
        Preconditions.checkArgument(port > 0);
        Pair<String,Integer> replica = new Pair<String,Integer>(host, port);
        partition.add(replica);
      }
      Preconditions.checkArgument(!partition.isEmpty());
      allPartitions.add(partition);
    }
    return allPartitions;
  }

}
