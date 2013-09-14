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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipInputStream;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import net.myrrix.common.ClassUtils;

/**
 * Simple utility methods related to I/O.
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class IOUtils {

  private IOUtils() {
  }

  /**
   * @param raw string to URL-encode
   * @return the URL encoding of the argument, using the UTF-8 encoding if necessary to interpret
   *  characters as bytes
   */
  public static String urlEncode(String raw) {
    try {
      return URLEncoder.encode(raw, Charsets.UTF_8.name());
    } catch (UnsupportedEncodingException uee) {
      // Can't happen for UTF-8
      throw new AssertionError(uee);
    }
  }

  /**
   * Attempts to recursively delete a directory. This may not work across symlinks.
   *
   * @param dir directory to delete along with contents
   * @return {@code true} if all files and dirs were deleted successfully
   */
  public static boolean deleteRecursively(File dir) {
    if (dir == null) {
      return false;
    }
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
      return new InflaterInputStream(in);
    }
    if (name.endsWith(".bz2") || name.endsWith(".bzip2")) {
      return new BZip2CompressorInputStream(in);
    }
    return in;
  }
  
  /**
   * @param file file, possibly compressed, to open
   * @return {@link Reader} on uncompressed contents
   * @throws IOException if the stream can't be opened or is invalid or can't be read
   * @see #openMaybeDecompressing(File) 
   */
  public static Reader openReaderMaybeDecompressing(File file) throws IOException {
    return new InputStreamReader(openMaybeDecompressing(file), Charsets.UTF_8);
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
      in.close();
    }
  }

  /**
   * @param url URL whose contents are to be read
   * @return the contents of the URL, interpreted as a UTF-8 encoded string
   * @throws IOException if the URL can't be read or the file can't be written
   */
  public static String readSmallTextFromURL(URL url) throws IOException {
    Reader in = new InputStreamReader(url.openStream(), Charsets.UTF_8);
    try {
      return CharStreams.toString(in);
    } finally {
      in.close();
    }
  }

  /**
   * @param delegate {@link OutputStream} to wrap
   * @return a {@link GZIPOutputStream} wrapping the given {@link OutputStream}. It attempts to use the new 
   *  Java 7 version that actually responds to {@link OutputStream#flush()} as expected. If not available,
   *  uses the previous version ({@link GZIPOutputStream#GZIPOutputStream(OutputStream)})
   */
  public static GZIPOutputStream buildGZIPOutputStream(OutputStream delegate) throws IOException {
    // In Java 7, GZIPOutputStream's flush() behavior can be made more as expected. Use it if possible
    // but fall back if not to the usual version
    try {
      return ClassUtils.loadInstanceOf(GZIPOutputStream.class, 
                                       new Class<?>[] {OutputStream.class, boolean.class},
                                       new Object[] {delegate, true});
    } catch (IllegalStateException ignored) {
      return new GZIPOutputStream(delegate);
    } 
  }

  /**
   * @see #buildGZIPOutputStream(OutputStream) 
   */
  public static GZIPOutputStream buildGZIPOutputStream(File file) throws IOException {
    return buildGZIPOutputStream(new FileOutputStream(file));
  }
  
  /**
   * @param delegate {@link OutputStream} to wrap
   * @return the result of {@link #buildGZIPOutputStream(OutputStream)} as a {@link Writer} that encodes
   *  using UTF-8 encoding
   */
  public static Writer buildGZIPWriter(OutputStream delegate) throws IOException {
    return new OutputStreamWriter(buildGZIPOutputStream(delegate), Charsets.UTF_8);
  }

  /**
   * @see #buildGZIPWriter(OutputStream)
   */
  public static Writer buildGZIPWriter(File file) throws IOException {
    return buildGZIPWriter(new FileOutputStream(file, false));
  }

  /**
   * Wraps its argument in {@link BufferedReader} if not already one.
   */
  public static BufferedReader buffer(Reader maybeBuffered) {
    return maybeBuffered instanceof BufferedReader 
        ? (BufferedReader) maybeBuffered 
        : new BufferedReader(maybeBuffered);
  }

  /**
   * @return a {@link BufferedReader} on the stream, using UTF-8 encoding
   */
  public static BufferedReader bufferStream(InputStream in) {
    return new BufferedReader(new InputStreamReader(in, Charsets.UTF_8));
  }

  /**
   * @return true iff the given file is a gzip-compressed file with no content; the file itself may not
   *  be empty because it contains gzip headers and footers
   * @throws IOException if the file is not a gzip file or can't be read
   */
  public static boolean isGZIPFileEmpty(File f) throws IOException {
    InputStream in = new GZIPInputStream(new FileInputStream(f));
    try {
      return in.read() == -1;
    } finally {
      in.close();
    }
  }
  
  /**
   * @return object of type T that was serialized into the given file
   */
  public static <T extends Serializable> T readObjectFromFile(File f, Class<T> clazz) throws IOException {
    ObjectInputStream in = new ObjectInputStream(openMaybeDecompressing(f));
    try {
      @SuppressWarnings("unchecked")
      T result = (T) in.readObject();
      return result;
    } catch (ClassNotFoundException cnfe) {
      throw new IllegalStateException(cnfe);
    } finally {
      in.close();
    }
  }

  /**
   * Serializes an object, with gzip compression, to a given file.
   */
  public static <T extends Serializable> void writeObjectToFile(File f, T t) throws IOException {
    Preconditions.checkArgument(f.getName().endsWith(".gz"), "File should end in .gz: %s", f);
    ObjectOutputStream out = new ObjectOutputStream(buildGZIPOutputStream(f));
    try {
      out.writeObject(t);
    } finally {
      out.close();
    }
  }

}
