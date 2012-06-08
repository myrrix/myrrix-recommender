/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.myrrix.common;

import java.io.File;
import java.io.FileFilter;

/**
 * Simple utility methods related to I/O.
 *
 * @author Sean Owen
 */
public final class IOUtils {

  private IOUtils() {
  }

  /**
   * Attempts to recursively delete a directory. This may not work across symlinks.
   */
  public static void deleteRecursively(File dir) {
    new DeletingVisitor().accept(dir);
  }

  // This is from Mahout actually:

  private static final class DeletingVisitor implements FileFilter {
    @Override
    public boolean accept(File f) {
      if (f != null) {
        if (!f.isFile()) {
          f.listFiles(this);
        }
        f.delete();
      }
      return false;
    }
  }

}
