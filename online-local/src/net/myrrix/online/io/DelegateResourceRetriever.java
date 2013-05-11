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

/**
 * An implementation that does nothing; not necessary/used in this version.
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class DelegateResourceRetriever implements ResourceRetriever {

  @Override
  public void init(String bucket) {
    // Do nothing
  }

  /**
   * @return null
   */
  @Override
  public File getRescorerJar(String instanceID) {
    // Not available / necessary in this version
    return null;
  }
  
  /**
   * @return null
   */
  @Override
  public File getClientThreadJar(String instanceID) {
    // Not available / necessary in this version    
    return null;
  }

  /**
   * @return null
   */
  @Override
  public File getKeystoreFile(String instanceID) {
    // Not available / necessary in this version
    return null;
  }

}
