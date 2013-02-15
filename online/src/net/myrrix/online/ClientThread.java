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

import java.io.Closeable;

import net.myrrix.common.MyrrixRecommender;

/**
 * <p>Implementations of this interface describe a process that runs in the Serving Layer in its own
 * thread, with direct access to the {@link MyrrixRecommender}. This may be useful for applications that
 * want to implement a server side "client" of particular services, like a poll/pull of external data.</p>
 * 
 * <p>Be sure to stop the thread reliably in {@link Closeable#close()}.</p>
 * 
 * @author Sean Owen
 */
public interface ClientThread extends Runnable, Closeable {

  /**
   * @param recommender called to give a reference to the recommender, which can be stored and used during
   *  running of the thread. This will be called before {@link Runnable#run()}  
   */
  void setRecommender(MyrrixRecommender recommender);

}
