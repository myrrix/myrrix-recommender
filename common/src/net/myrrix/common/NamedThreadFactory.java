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

package net.myrrix.common;

import java.util.concurrent.ThreadFactory;

/**
 * Creates threads with a given name prefix and daemon status.
 *
 * @author Sean Owen
 */
public final class NamedThreadFactory implements ThreadFactory {

  private final boolean daemon;
  private final String prefix;
  private int nextID;

  public NamedThreadFactory(boolean daemon, String prefix) {
    this.daemon = daemon;
    this.prefix = prefix;
  }

  @Override
  public Thread newThread(Runnable runnable) {
    Thread t = new Thread(runnable);
    t.setDaemon(daemon);
    t.setName(prefix + '-' + nextID);
    nextID++;
    return t;
  }

}
