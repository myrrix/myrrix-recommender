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

package net.myrrix.common.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.ClassUtils;

/**
 * Subclasses can handle registering of handlers for OS signals. This abstraction only tries to load
 * its subclass at runtime, since it may not exist on all platforms.
 *
 * @author Sean Owen
 */
public abstract class SignalManager {

  private static final Logger log = LoggerFactory.getLogger(SignalManager.class);

  private static final String IMPL_NAME = "net.myrrix.common.signal.SignalManagerPOSIXImpl";

  abstract void doRegister(Runnable handler, SignalType... types);

  public static void register(Runnable handler, SignalType... types) {
    if (ClassUtils.classExists(IMPL_NAME)) {
      SignalManager manager = ClassUtils.loadInstanceOf(IMPL_NAME, SignalManager.class);
      manager.doRegister(handler, types);
    } else {
      log.warn("Unable to handle OS signals on this platform. Using Ctrl-C or kill to stop the server may " +
               "result in loss of data");
    }
  }

}
