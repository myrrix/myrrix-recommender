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

package net.myrrix.online;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.mahout.cf.taste.recommender.IDRescorer;

import net.myrrix.common.collection.FastIDSet;

/**
 * A simple sketch of a {@link IDRescorer} that reloads some kind of data periodically from a
 * remote resource.
 * 
 * @author Sean Owen
 */
public final class ExampleRescorer implements IDRescorer {
  
  private FastIDSet someCurrentIDs;
  
  public ExampleRescorer() {
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
  public double rescore(long itemID, double rawValue) {
    return someCurrentIDs.contains(itemID) ? rawValue : Double.NaN;
  }

  @Override
  public boolean isFiltered(long itemID) {
    return !someCurrentIDs.contains(itemID);
  }

}
