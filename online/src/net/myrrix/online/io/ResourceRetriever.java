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

package net.myrrix.online.io;

import java.io.File;
import java.io.IOException;

/**
 * Implementations of this interface can specialize to be able to retrieve special resources
 * needed at runtime, which are usually loaded from a local file, but may need to be loaded
 * at runtime from odd places like a distributed file system.
 *
 * @author Sean Owen
 */
public interface ResourceRetriever {

  /**
   * @param bucket due to initialization order and where the class is used, it is necessary to tell
   *  implementations the bucket name being used directly
   */
  void init(String bucket);

  /**
   * @return a local file containing a copy of the remote {@code rescorer.jar}
   */
  File getRescorerJar(String instanceID) throws IOException;
  
  /**
   * @return a local file containing a copy of the remote {@code clientthread.jar}
   */
  File getClientThreadJar(String instanceID) throws IOException;

  /**
   * @return a local file containing a copy of the remote {@code keystore.ks}
   */
  File getKeystoreFile(String instanceID) throws IOException;

}
