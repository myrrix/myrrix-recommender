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

package net.myrrix.common.io;

import java.io.InputStream;

/**
 * {@link InputStream} which returns no bytes.
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class NullInputStream extends InputStream {

  public static final NullInputStream INSTANCE = new NullInputStream();

  @Override
  public int read() {
    return -1;
  }

  @Override
  public int read(byte[] b) {
    return -1;
  }

  @Override
  public int read(byte[] b, int off, int len) {
    return -1;
  }

  @Override
  public long skip(long n) {
    return 0L;
  }

  @Override
  public int available() {
    return 0;
  }

  @Override
  public void close() {
    // Do nothing
  }

  @Override
  public void mark(int i) {
    // Do nothing
  }

  @Override
  public void reset() {
     // Do nothing
  }

  @Override
  public boolean markSupported() {
    return false;
  }

}
