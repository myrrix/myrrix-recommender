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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A bean encapsulating some characteristics of the JVM's runtime environment.
 *
 * @author Sean Owen
 */
public final class JVMEnvironment {

  private final Runtime runtime;
  private String hostName;

  public JVMEnvironment() {
    runtime = Runtime.getRuntime();
  }

  /**
   * @return number of logical processors available to the JVM.
   */
  public int getNumProcessors() {
    return runtime.availableProcessors();
  }

  /**
   * @return approximate heap used, in megabytes
   */
  public int getUsedMemoryMB() {
    return (int) ((runtime.totalMemory() - runtime.freeMemory()) / 1000000);
  }

  /**
   * @return maximum size that the heap may grow to, in megabytes
   */
  public int getMaxMemoryMB() {
    return (int) (runtime.maxMemory() / 1000000);
  }

  public int getPercentUsedMemory() {
    return 100 * getUsedMemoryMB() / getMaxMemoryMB();
  }

  public String getHostName() {
    if (hostName == null) {
      try {
        hostName = InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException uhe) {
        // Can't happen
        throw new IllegalStateException(uhe);
      }
    }
    return hostName;
  }

  @Override
  public String toString() {
    return hostName + " : " + getNumProcessors() + " processors, " + getUsedMemoryMB() +
        "MB used of " + getMaxMemoryMB() + "MB";
  }

}
