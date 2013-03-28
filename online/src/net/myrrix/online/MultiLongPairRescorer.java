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
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;

/**
 * Convenience implementation that will aggregate the behavior of multiple {@link Rescorer}s
 * over {@link LongPair}. It will filter an item if any of the given instances filter it, and will rescore 
 * by applying the rescorings in the given order.
 *
 * @author Sean Owen
 * @see MultiRescorer
 * @see MultiRescorerProvider
 */
public final class MultiLongPairRescorer implements Rescorer<LongPair> {

  private final Rescorer<LongPair>[] rescorers;
  
  public MultiLongPairRescorer(Rescorer<LongPair>... rescorers) {
    Preconditions.checkNotNull(rescorers);
    Preconditions.checkState(rescorers.length > 0);
    this.rescorers = rescorers;
  }

  public MultiLongPairRescorer(List<Rescorer<LongPair>> rescorers) {
    Preconditions.checkNotNull(rescorers);
    Preconditions.checkState(!rescorers.isEmpty());
    this.rescorers = rescorers.toArray((Rescorer<LongPair>[]) new Rescorer[rescorers.size()]);
  }

  @Override
  public double rescore(LongPair itemIDs, double value) {
    for (Rescorer<LongPair> rescorer : rescorers) {
      value = rescorer.rescore(itemIDs, value);
      if (Double.isNaN(value)) {
        return Double.NaN;
      }
    }
    return value;
  }

  @Override
  public boolean isFiltered(LongPair itemIDs) {
    for (Rescorer<LongPair> rescorer : rescorers) {
      if (rescorer.isFiltered(itemIDs)) {
        return true;
      }
    }
    return false;
  }

}
