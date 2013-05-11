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

package net.myrrix.online.eval;

/**
 * Implementations of this interface encapsulate the result of an evaluation process. The details may vary
 * across processes, but all are guaranteed to provide at least one score, where bigger is better.
 *
 * @author Sean Owen
 * @since 1.0
 */
public interface EvaluationResult {

  /**
   * @return some value, whose nature depends on the implementation, which quantifies the evaluation. Bigger
   *  is better.
   */
  double getScore();

}
