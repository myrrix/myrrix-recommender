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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates a reference to something that is created the first time it is needed. Instead
 * of providing an initial value, a {@link Callable} is given that can create the thing is provided.
 * This can also periodically re-create or reload the value.
 *
 * @author Sean Owen
 */
public final class ReloadingReference<V> {

  private static final Logger log = LoggerFactory.getLogger(ReloadingReference.class);

  public static final long NO_RELOAD = -1L;

  private V value;
  private final Callable<V> retriever;
  private long lastRetrieval;
  private final long originalDurationMS;
  private long currentDurationMS;
  private final Lock lock;

  public ReloadingReference(Callable<V> retriever) {
    this(retriever, NO_RELOAD, null);
  }

  /**
   * @param retriever object that can supply a new, current value of the reference
   * @param duration time after which a new value will be loaded, or {@link #NO_RELOAD} to not reload
   *  after the initial value is loaded
   * @param timeUnit units of time for duration
   */
  public ReloadingReference(Callable<V> retriever, long duration, TimeUnit timeUnit) {
    Preconditions.checkNotNull(retriever);
    this.retriever = retriever;
    if (duration == NO_RELOAD) {
      originalDurationMS = NO_RELOAD;
    } else {
      Preconditions.checkArgument(duration > 0, "Duration must be positive: %s", duration);
      Preconditions.checkNotNull(timeUnit);
      originalDurationMS = TimeUnit.MILLISECONDS.convert(duration, timeUnit);
    }
    currentDurationMS = originalDurationMS;
    lock = new ReentrantLock();
  }

  /**
   * Like {@link #get()}, but when a value already exists, will only wait for the given amount of time before
   * just proceeding to return the existing value instead of waiting for a new value to load.
   */
  public V get(long timeout, TimeUnit timeUnit) {
    try {
      if (lock.tryLock(timeout, timeUnit)) {
        try {
          doGet();
        } finally {
          lock.unlock();
        }
      }
      // timed out
    } catch (InterruptedException ie) {
      // interrupted
    }
    V theValue = value;
    return theValue == null ? get() : theValue;
  }

  /**
   * @return object that is returned by the provided {@link Callable}. If not yet created, it will block and
   *  wait for creation. If already created, it will return the existing value. The value will be re-created
   *  periodically, if the object has been configured to, and when this is needed, this method will again
   *  block while the value is re-created.
   */
  public V get() {
    lock.lock();
    try {
      doGet();
    } finally {
      lock.unlock();
    }
    return value;
  }

  private void doGet() {
    boolean reloading = originalDurationMS > 0L;
    long now = reloading ? System.currentTimeMillis() : 0L;
    if (value == null || (reloading && now > lastRetrieval + currentDurationMS)) {
      try {
        value = retriever.call();
        Preconditions.checkState(value != null);
      } catch (Exception e) {
        // Kind of arbitrary exponential backoff -- 2x after each error up to 16x
        // If too many errors, or error on first retrieval, die
        if (currentDurationMS >= originalDurationMS * 16 || value == null) {
          // Too many errors, start failing
          throw new IllegalStateException(e);          
        }
        // else log and quietly back off
        log.warn("Retrieval failed; using previous cached value", e);
        currentDurationMS *= 2;
      }
      lastRetrieval = System.currentTimeMillis();      
      // Reset backoff
      currentDurationMS = originalDurationMS;      
    }
  }

  /**
   * @return object that is returned by the provided {@link Callable}, if it has already been created
   *  previously, or {@code null} otherwise
   */
  public V maybeGet() {
    return value;
  }

  /**
   * Clears the reference, requiring a load on next access.
   */
  public void clear() {
    lock.lock();
    try {
      value = null;
    } finally {
      lock.unlock();
    }
  }

}
