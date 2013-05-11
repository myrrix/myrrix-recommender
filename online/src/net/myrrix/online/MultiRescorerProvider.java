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
import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;

import net.myrrix.common.MyrrixRecommender;

/**
 * Convenience implementation that will aggregate the behavior of multiple {@link RescorerProvider}s.
 * It will filter an item if any of the given instances filter it, and will rescore by applying
 * the rescorings in the given order.
 *
 * @author Sean Owen
 * @since 1.0
 * @see MultiRescorer
 * @see MultiLongPairRescorer
 */
public final class MultiRescorerProvider extends AbstractRescorerProvider {
  
  private final RescorerProvider[] providers;
  
  public MultiRescorerProvider(RescorerProvider... providers) {
    Preconditions.checkNotNull(providers);
    Preconditions.checkArgument(providers.length > 0, "providers is empty");
    this.providers = providers;
  }
  
  public MultiRescorerProvider(List<RescorerProvider> providers) {
    Preconditions.checkNotNull(providers);
    Preconditions.checkArgument(!providers.isEmpty());
    this.providers = providers.toArray(new RescorerProvider[providers.size()]);
  }
  
  @Override
  public IDRescorer getRecommendRescorer(long[] userIDs, MyrrixRecommender recommender, String... args) {
    List<IDRescorer> rescorers = Lists.newArrayListWithCapacity(providers.length);
    for (RescorerProvider provider : providers) {
      IDRescorer rescorer = provider.getRecommendRescorer(userIDs, recommender, args);
      if (rescorer != null) {
        rescorers.add(rescorer);
      }
    }
    return buildRescorer(rescorers);
  }

  @Override
  public IDRescorer getRecommendToAnonymousRescorer(long[] itemIDs, MyrrixRecommender recommender, String... args) {
    List<IDRescorer> rescorers = Lists.newArrayListWithCapacity(providers.length);
    for (RescorerProvider provider : providers) {
      IDRescorer rescorer = provider.getRecommendToAnonymousRescorer(itemIDs, recommender, args);
      if (rescorer != null) {
        rescorers.add(rescorer);
      }
    }
    return buildRescorer(rescorers);  
  }

  @Override
  public IDRescorer getMostPopularItemsRescorer(MyrrixRecommender recommender, String... args) {
    List<IDRescorer> rescorers = Lists.newArrayListWithCapacity(providers.length);
    for (RescorerProvider provider : providers) {
      IDRescorer rescorer = provider.getMostPopularItemsRescorer(recommender, args);
      if (rescorer != null) {
        rescorers.add(rescorer);
      }
    }
    return buildRescorer(rescorers); 
  }
  
  private static IDRescorer buildRescorer(List<IDRescorer> rescorers) {
    int numRescorers = rescorers.size();
    if (numRescorers == 0) {
      return null;
    }
    if (numRescorers == 1) {
      return rescorers.get(0);
    }
    return new MultiRescorer(rescorers);
  }

  @Override
  public Rescorer<LongPair> getMostSimilarItemsRescorer(MyrrixRecommender recommender, String... args) {
    List<Rescorer<LongPair>> rescorers = Lists.newArrayListWithCapacity(providers.length);
    for (RescorerProvider provider : providers) {
      Rescorer<LongPair> rescorer = provider.getMostSimilarItemsRescorer(recommender, args);
      if (rescorer != null) {
        rescorers.add(rescorer);
      }
    }
    int numRescorers = rescorers.size();
    if (numRescorers == 0) {
      return null;
    }
    if (numRescorers == 1) {
      return rescorers.get(0);
    }
    return new MultiLongPairRescorer(rescorers);
  }

}
