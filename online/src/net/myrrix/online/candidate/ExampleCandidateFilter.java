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

package net.myrrix.online.candidate;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;

/**
 * A simple sketch of a {@link CandidateFilter} that reloads some kind of data periodically from a
 * remote resource.
 * 
 * @author Sean Owen
 * @since 1.0
 */
public final class ExampleCandidateFilter implements CandidateFilter {
  
  private final FastByIDMap<float[]> Y;
  private FastIDSet someCurrentIDs;
  private final ReadWriteLock lock;
  
  public ExampleCandidateFilter(FastByIDMap<float[]> Y) {
    this.Y = Y;
    lock = new ReentrantReadWriteLock();
    int reloadMinutes = 15;
    ScheduledExecutorService executor = 
        Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setDaemon(true).build());
    executor.scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        doLoad();
      }
    }, reloadMinutes, reloadMinutes, TimeUnit.MINUTES);
    doLoad();
  }
  
  private void doLoad() {
    FastIDSet newIDs = new FastIDSet();
    // Load something into it
    newIDs.add(1L);
    // ...
    someCurrentIDs = newIDs;
  }
  
  @Override
  public Collection<Iterator<FastByIDMap.MapEntry<float[]>>> getCandidateIterator(float[][] userVectors) {
    Lock readLock = lock.readLock();
    readLock.lock();
    try {
      return Collections.singleton(Iterators.transform(someCurrentIDs.iterator(), new RetrieveEntryFunction()));
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public void addItem(long itemID) {
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      someCurrentIDs.add(itemID);
    } finally {
      writeLock.unlock();
    }
  }

  private final class RetrieveEntryFunction implements Function<Long,FastByIDMap.MapEntry<float[]>> {
    @Override
    public FastByIDMap.MapEntry<float[]> apply(Long itemIDObj) {
      final long key = itemIDObj;
      final float[] value = Y.get(key);
      return new FastByIDMap.MapEntry<float[]>() {
        @Override
        public long getKey() {
          return key;
        }
        @Override
        public float[] getValue() {
          return value;
        }
      };
    }
  }
  
}
