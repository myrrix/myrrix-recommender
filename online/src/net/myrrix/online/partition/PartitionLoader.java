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

package net.myrrix.online.partition;

import java.util.List;

import com.google.common.net.HostAndPort;

/**
 * Implementations of this interface are able to automatically load information about partitions,
 * or the group of Serving Layers deployed to cooperate to serve requests. This is only applicable
 * in distributed mode, and the implementation used in local mode does nothing.
 *
 * @author Sean Owen
 * @since 1.0
 */
public interface PartitionLoader {

  /**
   * @param defaultPort port that is assumed if none is specified for a replica
   * @param bucket bucket for which partitions and replicas are loaded
   * @param instanceID instance for which partitions and replicas are loaded
   * @return {@link List} of partitions, where each partition is a {@link List} of replicas, where each
   *  replica is a host-port pair, as {@link HostAndPort}
   */
  List<List<HostAndPort>> loadPartitions(int defaultPort, String bucket, String instanceID);

}
