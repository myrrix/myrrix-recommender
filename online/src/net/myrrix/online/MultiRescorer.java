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

import java.util.List;

import com.google.common.base.Preconditions;
import org.apache.mahout.cf.taste.recommender.IDRescorer;

/**
 * Convenience implementation that will aggregate the behavior of multiple {@link IDRescorer}s.
 * It will filter an item if any of the given instances filter it, and will rescore by applying
 * the rescorings in the given order.
 *
 * @author Sean Owen
 * @see MultiLongPairRescorer
 * @see MultiRescorerProvider
 */
public final class MultiRescorer implements IDRescorer {

  private final IDRescorer[] rescorers;

  public MultiRescorer(List<IDRescorer> rescorers) {
    Preconditions.checkNotNull(rescorers);
    Preconditions.checkState(!rescorers.isEmpty(), "rescorers is empty");
    this.rescorers = rescorers.toArray(new IDRescorer[rescorers.size()]);
  }

  @Override
  public double rescore(long itemID, double value) {
    for (IDRescorer rescorer : rescorers) {
      value = rescorer.rescore(itemID, value);
      if (Double.isNaN(value)) {
        return Double.NaN;
      }
    }
    return value;
  }

  @Override
  public boolean isFiltered(long itemID) {
    for (IDRescorer rescorer : rescorers) {
      if (rescorer.isFiltered(itemID)) {
        return true;
      }
    }
    return false;
  }

}
