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

package net.myrrix.online.generation;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;

/**
 * Compares {@link File} objects by their last modified time. It orders by
 * time ascending.
 *
 * @author Sean Owen
 */
final class ByLastModifiedComparator implements Comparator<File>, Serializable {

  @Override
  public int compare(File a, File b) {
    long aModified = a.lastModified();
    long bModified = b.lastModified();
    if (aModified < bModified) {
      return -1;
    }
    if (aModified > bModified) {
      return 1;
    }
    return 0;
  }

}
