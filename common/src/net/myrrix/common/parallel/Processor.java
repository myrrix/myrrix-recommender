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

import java.util.concurrent.ExecutionException;

/**
 * A process that can operate on a given value. Used with {@link Paralleler}.
 * 
 * @author Sean Owen
 * @since 1.0
 * @see Paralleler
 * @param <T> the type of object the instance will process
 */
public interface Processor<T> {

  /**
   * @param t value to process
   * @param count a 1-based count of the values that have been processed so far
   */
  void process(T t, long count) throws ExecutionException;
  
}
