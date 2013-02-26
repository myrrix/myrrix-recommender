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

package net.myrrix.client.async;

import java.io.Closeable;
import java.io.File;
import java.io.Reader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.parallel.ExecutorUtils;
import net.myrrix.common.MyrrixRecommender;

/**
 * A wrapper around a {@link MyrrixRecommender} that executes update operations asynchronously
 * in a separate thread and return immediately. Exceptions are not thrown as a result, but are
 * logged. Callers should call {@link #close()} when done to stop the thread pool used internally for
 * execution.
 *
 * @author Sean Owen
 */
public final class AsyncRecommenderUpdater implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(AsyncRecommenderUpdater.class);

  private final MyrrixRecommender delegate;
  private final ExecutorService executor;

  /**
   * @param delegate underlying recommender to wrap
   */
  public AsyncRecommenderUpdater(MyrrixRecommender delegate) {
    Preconditions.checkNotNull(delegate);
    this.delegate = delegate;
    this.executor = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("AsyncRecommenderUpdater-%s").build());
  }

  /**
   * @see MyrrixRecommender#ingest(Reader)
   */
  public void ingest(final Reader reader) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          delegate.ingest(reader);
        } catch (Exception e) {
          log.warn("Unexpected error in async update", e);
        }
      }
    });
  }

  /**
   * @see MyrrixRecommender#ingest(File)
   */
  public void ingest(final File file) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          delegate.ingest(file);
        } catch (Exception e) {
          log.warn("Unexpected error in async update", e);
        }
      }
    });
  }

  /**
   * @see MyrrixRecommender#setPreference(long, long)
   */
  public void setPreference(final long userID, final long itemID) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          delegate.setPreference(userID, itemID);
        } catch (Exception e) {
          log.warn("Unexpected error in async update", e);
        }
      }
    });
  }

  /**
   * @see MyrrixRecommender#setPreference(long, long, float)
   */
  public void setPreference(final long userID, final long itemID, final float value) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          delegate.setPreference(userID, itemID, value);
        } catch (Exception e) {
          log.warn("Unexpected error in async update", e);
        }
      }
    });
  }

  /**
   * @see MyrrixRecommender#removePreference(long, long)
   */
  public void removePreference(final long userID, final long itemID) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          delegate.removePreference(userID, itemID);
        } catch (Exception e) {
          log.warn("Unexpected error in async update", e);
        }
      }
    });
  }

  /**
   * @see MyrrixRecommender#refresh()
   */
  public void refresh() {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          delegate.refresh();
        } catch (Exception e) {
          log.warn("Unexpected error in async update", e);
        }
      }
    });
  }

  @Override
  public void close() {
    ExecutorUtils.shutdownNowAndAwait(executor);
  }

}
