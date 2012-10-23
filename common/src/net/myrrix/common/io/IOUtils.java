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

package net.myrrix.common.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

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
   *
   * @param dir directory to delete along with contents
   * @return {@code true} if all files and dirs were deleted successfully
   */
  public static boolean deleteRecursively(File dir) {
    Deque<File> stack = new ArrayDeque<File>();
    stack.push(dir);
    boolean result = true;
    while (!stack.isEmpty()) {
      File topElement = stack.peek();
      if (topElement.isDirectory()) {
        File[] directoryContents = topElement.listFiles();
        if (directoryContents != null && directoryContents.length > 0) {
          for (File fileOrSubDirectory : directoryContents) {
            stack.push(fileOrSubDirectory);
          }
        } else {
          result = result && stack.pop().delete();
        }
      } else {
        result = result && stack.pop().delete();
      }
    }
    return result;
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

  /**
   * @param in   stream to read and copy
   * @param file file to write stream's contents to
   * @throws IOException if the stream can't be read or the file can't be written
   */
  public static void copyStreamToFile(InputStream in, File file) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    try {
      ByteStreams.copy(in, out);
    } finally {
      out.close();
    }
  }

  /**
   * @param url  URL whose contents are to be read and copied
   * @param file file to write contents to
   * @throws IOException if the URL can't be read or the file can't be written
   */
  public static void copyURLToFile(URL url, File file) throws IOException {
    InputStream in = url.openStream();
    try {
      copyStreamToFile(in, file);
    } finally {
      Closeables.closeQuietly(in);
    }
  }

}
