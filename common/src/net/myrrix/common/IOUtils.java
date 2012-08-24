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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

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

  /**
   * Opens an {@link InputStream} to the file. If it appears to be compressed, because its file name ends in
   * ".gz" or ".zip" or ".deflate", then it will be decompressed accordingly
   *
   * @param file file, possibly compressed, to open
   * @return {@link InputStream} on uncompressed contents
   * @throws IOException if the stream can't be opened or is invalid or can't be read
   */
  public static InputStream openMaybeDecompressing(File file) throws IOException {
    String name = file.getName();
    InputStream in = new FileInputStream(file);
    if (name.endsWith(".gz")) {
      return new GZIPInputStream(in);
    }
    if (name.endsWith(".zip")) {
      return new ZipInputStream(in);
    }
    if (name.endsWith(".deflate")) {
      return new DeflaterInputStream(in);
    }
    return in;
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
