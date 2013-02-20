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

package net.myrrix.online.factorizer;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import net.myrrix.common.collection.FastByIDMap;

/**
 * Implementations of this interface can factor a matrix into two matrices {@code X} and {@code Y},
 * typically of much lower rank.
 *
 * @author Sean Owen
 */
public interface MatrixFactorizer extends Callable<Void> {

  /** Default number of features to use when building the model. */
  int DEFAULT_FEATURES = 30;

  /**
   * Run the factorization algorithm to completion. Results are available from
   * {@link #getX()} and {@link #getY()} afterwards.
   *
   * @return {@code null}
   * @throws ExecutionException if an exception occurs during factorization;
   *  see {@link ExecutionException#getCause()} for reason
   * @throws InterruptedException if algorithm cannot complete because its computation
   *  was interrupted
   */
  @Override
  Void call() throws ExecutionException, InterruptedException;

  /**
   * Use the given matrix as the initial state of {@code X}. May be ignored.
   *
   * @param previousX initial matrix state
   */
  void setPreviousX(FastByIDMap<float[]> previousX);

  /**
   * Use the given matrix as the initial state of {@code Y}. May be ignored.
   *
   * @param previousY initial matrix state
   */
  void setPreviousY(FastByIDMap<float[]> previousY);

  /**
   * Typically called after {@link #call()} has finished.
   *
   * @return the current user-feature matrix, X
   */
  FastByIDMap<float[]> getX();

  /**
   * Typically called after {@link #call()} has finished.
   *
   * @return the current item-feature matrix, Y
   */
  FastByIDMap<float[]> getY();

}
