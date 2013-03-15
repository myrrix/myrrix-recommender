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

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.collection.FastIDSet;

public final class SimpleTest extends AbstractClientTest {

  private static final Logger log = LoggerFactory.getLogger(SimpleTest.class);

  @Override
  protected String getTestDataPath() {
    return "testdata/grouplens100K";
  }

  @Test
  public void testAllIDs() throws Exception {
    ClientRecommender client = getClient();
    FastIDSet allUserIDs = client.getAllUserIDs();
    FastIDSet allItemIDs = client.getAllItemIDs();
    assertEquals(943, allUserIDs.size());
    assertTrue(allUserIDs.contains(1L));
    assertEquals(1682, allItemIDs.size());
    assertTrue(allItemIDs.contains(421L));
  }

  @Test
  public void testIngest() throws Exception {
    StringReader reader = new StringReader("0,1\n0,2,3.0\n");

    ClientRecommender client = getClient();
    client.ingest(reader);

    List<RecommendedItem> recs = client.recommend(0L, 3);
    log.info("{}", recs);
    assertEquals(117L, recs.get(0).getItemID());
  }

  @Test
  public void testRecommend() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> recs = client.recommend(1L, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(475L, recs.get(0).getItemID());
    assertEquals(582L, recs.get(1).getItemID());
    assertEquals(403L, recs.get(2).getItemID());

    try {
      client.recommend(0L, 3);
      fail();
    } catch (NoSuchUserException nsue) {
      // good
    }

    recs = client.recommend(1L, 3, true, (String[]) null);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(179L, recs.get(0).getItemID());
    assertEquals(475L, recs.get(1).getItemID());
    assertEquals(135L, recs.get(2).getItemID());
  }

  @Test
  public void testRecommendToMany() throws Exception {

    ClientRecommender client = getClient();
    // Adding non-existent item to make sure it is ignored
    List<RecommendedItem> recs =
        client.recommendToMany(new long[] {1L, 3L, Integer.MAX_VALUE}, 3, false, (String[]) null);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(286L, recs.get(0).getItemID());
    assertEquals(288L, recs.get(1).getItemID());
    assertEquals(302L, recs.get(2).getItemID());
  }

  @Test(expected = NoSuchUserException.class)
  public void testRecommendToManyNonexistent1() throws Exception {
    getClient().recommendToMany(new long[] {Integer.MAX_VALUE}, 3, false, (String[]) null);
  }

  @Test(expected = NoSuchUserException.class)
  public void testRecommendToManyNonexistent2() throws Exception {
    getClient().recommendToMany(new long[] {Integer.MAX_VALUE, Integer.MIN_VALUE}, 3, false, (String[]) null);
  }

  @Test
  public void testRecommendToAnonymous() throws Exception {

    ClientRecommender client = getClient();
    // Adding non-existent item to make sure it is ignored
    List<RecommendedItem> recs = client.recommendToAnonymous(new long[] {1L, 3L, Integer.MAX_VALUE}, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(151L, recs.get(0).getItemID());
    assertEquals(50L, recs.get(1).getItemID());
    assertEquals(181L, recs.get(2).getItemID());
  }

  @Test(expected = NoSuchItemException.class)
  public void testRecommendToAnonymousNonexistent1() throws Exception {
    getClient().recommendToAnonymous(new long[] {Integer.MAX_VALUE}, 3);
  }

  @Test(expected = NoSuchItemException.class)
  public void testRecommendToAnonymousNonexistent2() throws Exception {
    getClient().recommendToAnonymous(new long[] {Integer.MAX_VALUE, Integer.MIN_VALUE}, 3);
  }

  @Test
  public void testMostPopular() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> popular = client.mostPopularItems(3);

    assertNotNull(popular);
    assertEquals(3, popular.size());

    log.info("{}", popular);

    assertEquals(50L, popular.get(0).getItemID());
    assertEquals(258L, popular.get(1).getItemID());
    assertEquals(100L, popular.get(2).getItemID());
    assertEquals(583.0f, popular.get(0).getValue());
    assertEquals(509.0f, popular.get(1).getValue());
    assertEquals(508.0f, popular.get(2).getValue());
  }

  @Test
  public void testMostSimilar() throws Exception {

    ClientRecommender client = getClient();
    // Adding non-existent item to make sure it is ignored
    List<RecommendedItem> similar = client.mostSimilarItems(new long[] {449L, Integer.MAX_VALUE}, 3);

    assertNotNull(similar);
    assertEquals(3, similar.size());

    log.info("{}", similar);

    assertEquals(229L, similar.get(0).getItemID());
    assertEquals(450L, similar.get(1).getItemID()); 
    assertEquals(227L, similar.get(2).getItemID());    
  }

  @Test(expected = NoSuchItemException.class)
  public void testSimilarToNonexistent1() throws Exception {
    getClient().mostSimilarItems(Integer.MAX_VALUE, 3);
  }

  @Test(expected = NoSuchItemException.class)
  public void testSimilarToNonexistent2() throws Exception {
    getClient().mostSimilarItems(new long[] {Integer.MAX_VALUE, Integer.MIN_VALUE}, 3);
  }

  @Test
  public void testEstimate() throws Exception {

    ClientRecommender client = getClient();
    float[] estimates = client.estimatePreferences(10L, 90L, 91L, 92L);

    assertNotNull(estimates);
    assertEquals(3, estimates.length);

    log.info("{}", Arrays.toString(estimates));

    assertEquals(0.3006489f, estimates[0]);
    assertEquals(0.42647615f, estimates[1]);
    assertEquals(0.66089016f, estimates[2]);

    // Non-existent
    assertEquals(0.0f, client.estimatePreference(0L, 90L));
    assertEquals(0.0f, client.estimatePreference(10L, 0L));
  }

  @Test
  public void testBecause() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> because = client.recommendedBecause(1L, 321, 3);

    assertNotNull(because);
    assertEquals(3, because.size());

    log.info("{}", because);

    assertEquals(269L, because.get(0).getItemID());
    assertEquals(116L, because.get(1).getItemID());
    assertEquals(242L, because.get(2).getItemID());

    try {
      client.recommendedBecause(0L, 222L, 3);
      fail();
    } catch (NoSuchItemException nsie) {
      // good
    }
    try {
      client.recommendedBecause(1L, 0L, 3);
      fail();
    } catch (NoSuchItemException nsie) {
      // good
    }
  }

  @Test
  public void testAnonymous() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> recs = client.recommendToAnonymous(new long[] {190L}, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(83L, recs.get(0).getItemID());
    assertEquals(213L, recs.get(1).getItemID());
    assertEquals(86L, recs.get(2).getItemID());

    try {
      client.recommendToAnonymous(new long[]{0L}, 3);
      fail();
    } catch (NoSuchItemException nsie) {
      // good
    }
  }

  @Test
  public void testAnonymous2() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> recs =
        client.recommendToAnonymous(new long[] {190L}, new float[] {1.0f}, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(83L, recs.get(0).getItemID());
    assertEquals(213L, recs.get(1).getItemID());
    assertEquals(86L, recs.get(2).getItemID());

    try {
      client.recommendToAnonymous(new long[]{0L}, 3);
      fail();
    } catch (NoSuchItemException nsie) {
      // good
    }
  }

  @Test
  public void testAnonymous3() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> recs =
        client.recommendToAnonymous(new long[] {190L, 200L}, new float[] {2.0f, 3.0f}, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(234L, recs.get(0).getItemID());
    assertEquals(185L, recs.get(1).getItemID());
    assertEquals(176L, recs.get(2).getItemID());

    try {
      client.recommendToAnonymous(new long[]{0L}, 3);
      fail();
    } catch (NoSuchItemException nsie) {
      // good
    }
  }

  @Test
  public void testSet() throws Exception {
    ClientRecommender client = getClient();

    client.setPreference(0L, 1L);
    List<RecommendedItem> recs = client.recommend(0L, 1);
    assertEquals(50L, recs.get(0).getItemID());

    client.setPreference(0L, 2L, 3.0f);
    recs = client.recommend(0L, 1);
    assertEquals(117L, recs.get(0).getItemID());

    client.setPreference(0L, 2L, -3.0f);
    recs = client.recommend(0L, 1);
    assertEquals(117L, recs.get(0).getItemID());

    client.setPreference(0L, 1L, -1.0f);
    // Don't really know/care what will be recommend at this point; the feature vec is nearly 0
    assertEquals(1, client.recommend(0L, 1).size());
  }

  @Test
  public void testSetRemove() throws Exception {
    ClientRecommender client = getClient();

    client.setPreference(0L, 1L);
    List<RecommendedItem> recs = client.recommend(0L, 1);
    assertEquals(50L, recs.get(0).getItemID());

    client.setPreference(0L, 2L, 1.0f);
    recs = client.recommend(0L, 1);
    assertEquals(50L, recs.get(0).getItemID());

    client.removePreference(0L, 2L);
    recs = client.recommend(0L, 1);
    assertEquals(50L, recs.get(0).getItemID());

    client.removePreference(0L, 1L);
    try {
      client.recommend(0L, 1);
      fail();
    } catch (NoSuchUserException nsue) {
      // good
    }
  }

}
