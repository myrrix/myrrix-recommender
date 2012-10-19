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

import java.util.concurrent.Callable;

import com.google.common.base.Preconditions;

/**
 * Encapsulates a reference to something that is instantiated the first time it is needed. Instead
 * of providing an initial value, a {@link Callable} that can create the thing is provided.
 */
public final class LazyReference<V> {

  private V value;
  private final Callable<V> retriever;

  public LazyReference(Callable<V> retriever) {
    Preconditions.checkNotNull(retriever);
    this.retriever = retriever;
  }

  /**
   * @return object that is returned by the provided {@link Callable}
   */
  public synchronized V get() {
    if (value == null) {
      try {
        value = retriever.call();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    return value;
  }

  /**
   * @return object that is returned by the provided {@link Callable}, if it has already been created
   *  previously, or {@code null} otherwise
   */
  public synchronized V maybeGet() {
    return value;
  }

}
