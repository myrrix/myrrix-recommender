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

package net.myrrix.web;

import java.util.Collection;
import java.util.Queue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import com.google.common.collect.Lists;

/**
 * Simple {@link Handler} that records recent log lines in memory.
 */
public final class MemoryHandler extends Handler {

  private static final int NUM_LINES = 1000;

  private final Queue<String> logLines;

  public MemoryHandler() {
    logLines = Lists.newLinkedList();
    setFormatter(new SimpleFormatter());
    setLevel(Level.FINE);
  }

  public Collection<String> getLogLines() {
    return logLines;
  }

  @Override
  public void publish(LogRecord logRecord) {
    String line = getFormatter().format(logRecord);
    synchronized (logLines) {
      logLines.add(line);
      for (int i = 0; i < logLines.size() - NUM_LINES; i++) {
        logLines.remove();
      }
    }
  }

  @Override
  public void flush() {
  }

  @Override
  public void close() {
    logLines.clear();
  }

}
