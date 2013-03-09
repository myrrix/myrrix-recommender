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

package net.myrrix.client;

import java.util.List;

import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a tiny test including tags to check its results. The results are checked against the same test where
 * real items/users are used instead of tags. The results are slightly different because tags translate to 
 * IDs that fall in a different order, resulting in a slightly different initial random starting vector.
 * 
 * @author Sean Owen
 */
public final class TinyTagsTest extends AbstractClientTest {

  private static final Logger log = LoggerFactory.getLogger(TinyTagsTest.class);
  private static final double BIG_EPSILON = 0.02;

  @Override
  protected String getTestDataPath() {
    System.setProperty("model.features", "2");
    return "testdata/tiny-tags";
  }

  @Test
  public void testTinyRecommend() throws Exception {
    ClientRecommender client = getClient();
    List<RecommendedItem> recs = client.recommend(5, 2);
    log.info("{}", recs);
    assertEquals(2, recs.get(0).getItemID());
    assertEquals(3, recs.get(1).getItemID());
    assertEquals(0.8745233f, recs.get(0).getValue(), BIG_EPSILON);
    assertEquals(-0.0033516437f, recs.get(1).getValue(), BIG_EPSILON);
  }
  
  @Test
  public void testTinySimilar() throws Exception {
    ClientRecommender client = getClient();
    List<RecommendedItem> similar = client.mostSimilarItems(2, 1);
    log.info("{}", similar);
    assertEquals(4, similar.get(0).getItemID());
    assertEquals(-0.527466f, similar.get(0).getValue());
  }
  
  @Test
  public void testTinyMostPopular() throws Exception {
    ClientRecommender client = getClient();
    List<RecommendedItem> popular = client.mostPopularItems(5);
    log.info("{}", popular);
    assertEquals(2, popular.get(0).getItemID());
    assertEquals(2.0f, popular.get(0).getValue(), BIG_EPSILON);
  }
  
  @Test
  public void testTinyAnonymous() throws Exception {
    ClientRecommender client = getClient();
    List<RecommendedItem> recs = client.recommendToAnonymous(new long[] {2,3}, 1);
    log.info("{}", recs);
    assertEquals(4, recs.get(0).getItemID());
    assertEquals(-0.14083529f, recs.get(0).getValue(), BIG_EPSILON);
  }
  
  @Test
  public void testTag() throws Exception {
    ClientRecommender client = getClient();
    
    client.setUserTag(5, "bar", -10.0f);
    List<RecommendedItem> recs = client.recommend(5, 1);
    log.info("{}", recs);
    assertEquals(2, recs.get(0).getItemID());
    assertEquals(0.9864124f, recs.get(0).getValue());
    
    client.setItemTag("bing", 4);
    recs = client.recommend(5, 1);
    log.info("{}", recs);
    assertEquals(2, recs.get(0).getItemID());
    assertEquals(0.9864124f, recs.get(0).getValue());
  }

}
