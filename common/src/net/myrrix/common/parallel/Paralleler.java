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

package net.myrrix.common.parallel;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A convenient way of executing a large number of small tasks in parallel. The values to be operated on
 * are provided as an {@link Iterator} over values, along with a function that does something to each value,
 * as a {@link Processor}.</p>
 * 
 * <p>By default the work is performed on each value in a number of threads equal to the number of cores on the
 * machines, when calling {@link #runInParallel()}. The operations may also be invoked with a given 
 * {@link ExecutorService} and parallelism with {@link #runInParallel(ExecutorService, int)}.</p>
 * 
 * @author Sean Owen
 * @since 1.0
 * @param <T> type of value to operate on
 */
public final class Paralleler<T> {
  
  private static final Logger log = LoggerFactory.getLogger(Paralleler.class);
  
  private final String name;
  private final Iterator<T> values;
  private final Processor<T> processor;
  
  /**
   * Creates an instance which will apply a {@link Processor} to many values.
   * 
   * @param values values to be processed with {@code processor}
   * @param processor {@link Processor} to apply to each value
   */
  public Paralleler(Iterator<T> values, Processor<T> processor) {
    this(values, processor, null);
  }
  
  /**
   * Creates an instance which will apply a {@link Processor} to many values.
   * 
   * @param values values to be processed with {@code processor}
   * @param processor {@link Processor} to apply to each value
   * @param name thread name prefix
   */
  public Paralleler(Iterator<T> values, Processor<T> processor, String name) {
    Preconditions.checkNotNull(values);
    Preconditions.checkNotNull(processor);    
    this.values = values;
    this.processor = processor;
    this.name = name;    
  }

  /**
   * Run {@link Processor}, but only in 1 thread, on values.
   * 
   * @throws ExecutionException if any execution fails
   */
  public void runInSerial() throws ExecutionException {
    AtomicLong count = new AtomicLong(0);
    while (values.hasNext()) {
      processor.process(values.next(), count.incrementAndGet());
    }
  }

  /**
   * Run {@link Processor}, in as many threads as available cores, on values.
   * 
   * @throws ExecutionException if any execution fails
   */
  public void runInParallel() throws InterruptedException, ExecutionException {
    ThreadFactoryBuilder builder = new ThreadFactoryBuilder();
    if (name != null) {
      builder.setNameFormat(name + "-%s");
    }
    int numCores = Runtime.getRuntime().availableProcessors();    
    ExecutorService executor = Executors.newFixedThreadPool(numCores, builder.build());
    try {
      runInParallel(executor, numCores);
    } finally {
      executor.shutdownNow();
    }
  }
  
  /**
   * Run {@link Processor}, in as many threads as available cores, on values. The given 
   * {@link ExecutorService} and number of threads are used instead of a new one.
   * 
   * @throws ExecutionException if any execution fails
   */
  public void runInParallel(ExecutorService executor, int parallelism) 
      throws InterruptedException, ExecutionException {
    Collection<Callable<Object>> callables = Lists.newArrayListWithCapacity(parallelism);
    AtomicLong count = new AtomicLong(0);
    for (int i = 0; i < parallelism; i++) {
      callables.add(new ParallelWorker(count));
    }
    
    Collection<Future<?>> futures = Lists.newArrayListWithCapacity(parallelism);    
    boolean done = false;
    try {
      for (Callable<?> callable : callables) {
        futures.add(executor.submit(callable));
      }
      for (Future<?> f : futures) {
        if (!f.isDone()) {
          try {
            f.get();
          } catch (ExecutionException e) {
            log.warn("Exception from worker {}", name, e.getCause());
            throw e;
          }
        }
      }
      done = true;
    } finally {
      if (!done) {
        for (Future<?> f : futures) {
          f.cancel(true);
        }
      }
    }
  }

  private final class ParallelWorker implements Callable<Object> {
    
    private final AtomicLong count;
    
    private ParallelWorker(AtomicLong count) {
      this.count = count;
    }
    
    @Override
    public Object call() throws ExecutionException {
      while (true) {
        T value;
        synchronized (values) {
          if (!values.hasNext()) {
            return null;
          }
          value = values.next();
        }
        processor.process(value, count.incrementAndGet());
      }
    }
    
  }
  
}
