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

import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.apache.mahout.common.LongPair;
import org.junit.Test;

import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.MyrrixTest;
import net.myrrix.online.example.rescorer.FilterHalfRescorerProvider;

public final class MultiRescorerProviderTest extends MyrrixTest {
  
  @Test
  public void testMultiRecommendRescorer() {
    RescorerProvider multi = 
        new MultiRescorerProvider(new SimpleModRescorerProvider(2), new SimpleModRescorerProvider(3));
    
    IDRescorer provider = multi.getRecommendRescorer(new long[]{5}, (MyrrixRecommender) null);
    assertNull(provider);
    
    provider = multi.getRecommendRescorer(new long[]{2}, (MyrrixRecommender) null);
    assertNotNull(provider);
    assertFalse(provider instanceof MultiRescorer);
    assertTrue(provider.isFiltered(3));
    assertFalse(provider.isFiltered(2));

    provider = multi.getRecommendRescorer(new long[]{6}, (MyrrixRecommender) null);
    assertNotNull(provider);
    assertTrue(provider instanceof MultiRescorer);
    assertTrue(provider.isFiltered(3));
    assertTrue(provider.isFiltered(2));
    assertFalse(provider.isFiltered(12));
  }
  
  @Test
  public void testMultiRecommendToAnonymousRescorer() {
    RescorerProvider multi = 
        new MultiRescorerProvider(new SimpleModRescorerProvider(2), new SimpleModRescorerProvider(3));
    
    IDRescorer provider = multi.getRecommendToAnonymousRescorer(new long[]{5}, (MyrrixRecommender) null);
    assertNull(provider);
    
    provider = multi.getRecommendToAnonymousRescorer(new long[]{2}, (MyrrixRecommender) null);
    assertNotNull(provider);
    assertFalse(provider instanceof MultiRescorer);
    assertTrue(provider.isFiltered(3));
    assertFalse(provider.isFiltered(2));

    provider = multi.getRecommendToAnonymousRescorer(new long[]{6}, (MyrrixRecommender) null);
    assertNotNull(provider);
    assertTrue(provider instanceof MultiRescorer);
    assertTrue(provider.isFiltered(3));
    assertTrue(provider.isFiltered(2));
    assertFalse(provider.isFiltered(12));
  }
  
  @Test
  public void testMultiMostPopularItemsRescorer() {
    RescorerProvider multi = 
        new MultiRescorerProvider(new SimpleModRescorerProvider(2), new SimpleModRescorerProvider(3));
    IDRescorer provider = multi.getMostPopularItemsRescorer(null);
    assertNotNull(provider);
    assertTrue(provider instanceof MultiRescorer);
    assertTrue(provider.isFiltered(3));
    assertTrue(provider.isFiltered(2));
    assertFalse(provider.isFiltered(6));    
  }
  
  @Test
  public void testMultiMostSimilarItemsRescorer() {
    RescorerProvider multi = 
        new MultiRescorerProvider(new SimpleModRescorerProvider(2), new SimpleModRescorerProvider(3));
    Rescorer<LongPair> provider = multi.getMostSimilarItemsRescorer((MyrrixRecommender) null);
    assertNotNull(provider);
    assertTrue(provider instanceof MultiLongPairRescorer);
    assertTrue(provider.isFiltered(new LongPair(2,3))); 
    assertTrue(provider.isFiltered(new LongPair(2,6)));  
    assertFalse(provider.isFiltered(new LongPair(6,12)));
  }
  
  @Test
  public void testLoad() {
    String name = FilterHalfRescorerProvider.class.getName();
    assertNotNull(AbstractRescorerProvider.loadRescorerProviders(name + ',' + name, null));
  }
  
}
