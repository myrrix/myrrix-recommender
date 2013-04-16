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

package net.myrrix.online.generation;

import java.util.concurrent.locks.Lock;

import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.common.collection.FastIDSet;

/**
 * Contains logic for merging a new generation into the current live one.
 */
final class GenerationLoader {
  
  private static final Logger log = LoggerFactory.getLogger(GenerationLoader.class);

  private final FastIDSet recentlyActiveUsers;
  private final FastIDSet recentlyActiveItems;
  private final Object lockForRecent;
  
  GenerationLoader(FastIDSet recentlyActiveUsers,
                   FastIDSet recentlyActiveItems,
                   Object lockForRecent) {

    this.recentlyActiveUsers = recentlyActiveUsers;
    this.recentlyActiveItems = recentlyActiveItems;
    // This must be acquired to access the 'recent' fields above:
    this.lockForRecent = lockForRecent;
  }
  
  void loadModel(Generation currentGeneration,
                 FastByIDMap<float[]> newX,
                 FastByIDMap<float[]> newY,
                 FastByIDMap<FastIDSet> updatedKnownItemIDs,
                 FastIDSet updatedItemTagIDs,
                 FastIDSet updatedUserTagIDs) {
    
    updateTagIDs(currentGeneration.getItemTagIDs(), updatedItemTagIDs, currentGeneration.getXLock().writeLock());
    updateTagIDs(currentGeneration.getUserTagIDs(), updatedUserTagIDs, currentGeneration.getYLock().writeLock());
    
    updateMap(currentGeneration.getX(), newX, currentGeneration.getXLock().writeLock());
    updateMap(currentGeneration.getY(), newY, currentGeneration.getYLock().writeLock());
    
    if (updatedKnownItemIDs != null) {
      updateMap(currentGeneration.getKnownItemIDs(), updatedKnownItemIDs, currentGeneration.getKnownItemLock().writeLock());
    }
    
    FastIDSet updatedXKeys = keysToSet(newX);
    FastIDSet updatedYKeys = keysToSet(newY);
    FastIDSet updatedUserIDsForKnownItems = updatedKnownItemIDs == null ? null : keysToSet(updatedKnownItemIDs);
    
    synchronized (lockForRecent) {
      // Not recommended to set this to 'false' -- may be useful in rare cases      
      if (Boolean.parseBoolean(System.getProperty("model.removeNotUpdatedData", "true"))) {
        log.info("Pruning old entries...");        
        removeNotUpdated(currentGeneration.getX().keySetIterator(),
                         updatedXKeys,
                         recentlyActiveUsers,
                         currentGeneration.getXLock().writeLock());
        removeNotUpdated(currentGeneration.getY().keySetIterator(),
                         updatedYKeys,
                         recentlyActiveItems,
                         currentGeneration.getYLock().writeLock());
        if (updatedUserIDsForKnownItems != null && currentGeneration.getKnownItemIDs() != null) {
          removeNotUpdated(currentGeneration.getKnownItemIDs().keySetIterator(),
                           updatedUserIDsForKnownItems,
                           recentlyActiveUsers,
                           currentGeneration.getKnownItemLock().writeLock());
        }
        removeNotUpdated(currentGeneration.getItemTagIDs().iterator(),
                         updatedItemTagIDs,
                         recentlyActiveUsers,
                         currentGeneration.getXLock().writeLock());
        removeNotUpdated(currentGeneration.getUserTagIDs().iterator(),
                         updatedUserTagIDs,
                         recentlyActiveItems,
                         currentGeneration.getYLock().writeLock());
      }
      recentlyActiveUsers.clear();
      recentlyActiveItems.clear();
    }
    
    log.info("Recomputing generation state...");
    currentGeneration.recomputeState();
    
    log.info("All model elements loaded, {} users and {} items", 
             currentGeneration.getNumUsers(), currentGeneration.getNumItems());
  }
  
  private static void updateTagIDs(FastIDSet currentTagIDs, FastIDSet updatedTagIDs, Lock writeLock) {
    writeLock.lock();
    try {
      currentTagIDs.addAll(updatedTagIDs);
    } finally {
      writeLock.unlock();
    }
  }

  private static <T> void updateMap(FastByIDMap<T> knownItemIDs, FastByIDMap<T> updated, Lock writeLock) {
    writeLock.lock();
    try {
      for (FastByIDMap.MapEntry<T> entry : updated.entrySet()) {
        knownItemIDs.put(entry.getKey(), entry.getValue());
      }
    } finally {
      writeLock.unlock();
    }
  }
  
  private static FastIDSet keysToSet(FastByIDMap<?> map) {
    FastIDSet result = new FastIDSet(map.size(), 1.25f);
    LongPrimitiveIterator it = map.keySetIterator();
    while (it.hasNext()) {
      result.add(it.nextLong());
    }
    return result;
  }

  private static void removeNotUpdated(LongPrimitiveIterator it,
                                       FastIDSet updated,
                                       FastIDSet recentlyActive,
                                       Lock writeLock) {
    writeLock.lock();
    try {
      while (it.hasNext()) {
        long id = it.nextLong();
        if (!updated.contains(id) && !recentlyActive.contains(id)) {
          it.remove();
        }
      }
    } finally {
      writeLock.unlock();
    }
  }
  
}
