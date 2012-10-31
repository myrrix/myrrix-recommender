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

/**
 * An implementation that uses the possibly platform-specific {@code sun.misc} libraries to register
 * signal handlers.
 *
 * @author Sean Owen
 */
@SuppressWarnings("all")
public final class SignalManagerPOSIXImpl extends SignalManager {

  private static final Logger log = LoggerFactory.getLogger(SignalManagerPOSIXImpl.class);

  // This is going to generate compiler warnings. No real way around it.

  @Override
  void doRegister(final Runnable handler, SignalType... types) {
    for (SignalType type : types) {
      sun.misc.Signal.handle(new sun.misc.Signal(type.toString()), new sun.misc.SignalHandler() {
        @Override
        public void handle(sun.misc.Signal signal) {
          log.info("Caught signal {} ({})", signal.getName(), signal.getNumber());
          handler.run();
        }
      });
    }
  }

}
