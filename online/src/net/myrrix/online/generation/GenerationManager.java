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

import java.io.Closeable;
import java.io.IOException;

import org.apache.mahout.cf.taste.common.Refreshable;

/**
 * An implementation of {@link GenerationManager} is responsible for interacting with successive generations of the
 * underlying recommender model. It sends updates to the component responsible for computing the model,
 * and manages switching in new models when they become available. For example,
 * {@code LocalGenerationManager} computes generations on the local machine,
 * using data stored in the local file system.
 *
 * @author Sean Owen
 */
public interface GenerationManager extends Closeable, Refreshable {

  /**
   * @return an instance of the latest {@link Generation} that has been made available by the
   * implementation.
   */
  Generation getCurrentGeneration();

  /**
   * Sends a new user / item association to the component responsible for later recomputing
   * the model based on this, and other, updates.
   *
   * @param userID user involved in new association
   * @param itemID item involved
   * @param value strength of the user/item association; must be positive
   * @param bulk if true, part of a bulk update and should expect many method calls in a row
   * @throws IOException if an error occurs while sending the update
   */
  void append(long userID, long itemID, float value, boolean bulk) throws IOException;

  /**
   * Records that the user-item association should be removed. This is different from recording a
   * negative association.
   *
   * @param userID user involved in new association
   * @param itemID item involved
   * @param bulk if true, part of a bulk update and should expect many method calls in a row
   * @throws IOException if an error occurs while sending the update
   */
  void remove(long userID, long itemID, boolean bulk) throws IOException;

  /**
   * Signals the end of a string of updates from a bulk update, like an {@code ingest} request.
   */
  void bulkDone() throws IOException;

  /**
   * @return instance ID of the recommender system that this object is managing
   */
  String getInstanceID();

  /**
   * @return bucket used by the recommender system
   */
  String getBucket();
  
}
