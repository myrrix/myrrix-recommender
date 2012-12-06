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
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.collection.FastIDSet;

public final class SimpleTest extends AbstractClientTest {

  private static final Logger log = LoggerFactory.getLogger(SimpleTest.class);

  @Override
  protected String getTestDataPath() {
    return "testdata/grouplens100K-45";
  }

  @Override
  @Before
  public void setUp() throws Exception {
    System.setProperty("model.iterations", "50");
    super.setUp();
  }

  @Test
  public void testAllIDs() throws Exception {
    ClientRecommender client = getClient();
    FastIDSet allUserIDs = client.getAllUserIDs();
    FastIDSet allItemIDs = client.getAllItemIDs();
    assertEquals(942, allUserIDs.size());
    assertTrue(allUserIDs.contains(1L));
    assertEquals(1447, allItemIDs.size());
    assertTrue(allItemIDs.contains(421L));
  }

  @Test
  public void testIngest() throws Exception {
    StringReader reader = new StringReader("0,1\n0,2,3.0\n");

    ClientRecommender client = getClient();
    client.ingest(reader);

    List<RecommendedItem> recs = client.recommend(0L, 3);
    log.info("{}", recs);
    assertEquals(274L, recs.get(0).getItemID());
  }

  @Test
  public void testRecommend() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> recs = client.recommend(1L, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(549L, recs.get(0).getItemID());
    assertEquals(727L, recs.get(1).getItemID());
    assertEquals(584L, recs.get(2).getItemID());
    assertEquals(1.4847757f, recs.get(0).getValue());
    assertEquals(1.4189683f, recs.get(1).getValue());
    assertEquals(1.3309146f, recs.get(2).getValue());

    try {
      client.recommend(0L, 3);
      fail();
    } catch (NoSuchUserException nsue) {
      // good
    }

    recs = client.recommend(1L, 3, true, null);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(549L, recs.get(0).getItemID());
    assertEquals(727L, recs.get(1).getItemID());
    assertEquals(584L, recs.get(2).getItemID());
    assertEquals(1.4847757f, recs.get(0).getValue());
    assertEquals(1.4189683f, recs.get(1).getValue());
    assertEquals(1.3309146f, recs.get(2).getValue());
  }

  @Test
  public void testRecommendToMany() throws Exception {

    ClientRecommender client = getClient();
    // Adding non-existent item to make sure it is ignored
    List<RecommendedItem> recs = client.recommendToMany(new long[] {1L, 3L, Integer.MAX_VALUE}, 3, false, null);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(258L, recs.get(0).getItemID());
    assertEquals(288L, recs.get(1).getItemID());
    assertEquals(313L, recs.get(2).getItemID());
    assertEquals(1.1147355f, recs.get(0).getValue());
    assertEquals(1.0954059f, recs.get(1).getValue());
    assertEquals(1.0852712f, recs.get(2).getValue());
  }

  @Test(expected = NoSuchUserException.class)
  public void testRecommendToManyNonexistent1() throws Exception {
    getClient().recommendToMany(new long[] {Integer.MAX_VALUE}, 3, false, null);
  }

  @Test(expected = NoSuchUserException.class)
  public void testRecommendToManyNonexistent2() throws Exception {
    getClient().recommendToMany(new long[] {Integer.MAX_VALUE, Integer.MIN_VALUE}, 3, false, null);
  }

  @Test
  public void testRecommendToAnonymous() throws Exception {

    ClientRecommender client = getClient();
    // Adding non-existent item to make sure it is ignored
    List<RecommendedItem> recs = client.recommendToAnonymous(new long[] {1L, 3L, Integer.MAX_VALUE}, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(181L, recs.get(0).getItemID());
    assertEquals(50L, recs.get(1).getItemID());
    assertEquals(127L, recs.get(2).getItemID());
    assertEquals(0.023732195f, recs.get(0).getValue());
    assertEquals(0.02364187f, recs.get(1).getValue());
    assertEquals(0.023541192f, recs.get(2).getValue());
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
  public void testMostSimilar() throws Exception {

    ClientRecommender client = getClient();
    // Adding non-existent item to make sure it is ignored
    List<RecommendedItem> similar = client.mostSimilarItems(new long[] {449L, Integer.MAX_VALUE}, 3);

    assertNotNull(similar);
    assertEquals(3, similar.size());

    log.info("{}", similar);

    assertEquals(229L, similar.get(0).getItemID());
    assertEquals(227L, similar.get(1).getItemID());
    assertEquals(380L, similar.get(2).getItemID());
    assertEquals(0.8844487f, similar.get(0).getValue());
    assertEquals(0.81553394f, similar.get(1).getValue());
    assertEquals(0.7953288f, similar.get(2).getValue());
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

    assertEquals(-0.008231872f, estimates[0]);
    assertEquals(0.6707238f, estimates[1]);
    assertEquals(0.93468416f, estimates[2]);

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

    assertEquals(221L, because.get(0).getItemID());
    assertEquals(33L, because.get(1).getItemID());
    assertEquals(268L, because.get(2).getItemID());
    assertEquals(0.4937572f, because.get(0).getValue());
    assertEquals(0.35079598f, because.get(1).getValue());
    assertEquals(0.35050637f, because.get(2).getValue());

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

    assertEquals(198L, recs.get(0).getItemID());
    assertEquals(45L, recs.get(1).getItemID());
    assertEquals(510L, recs.get(2).getItemID());
    assertEquals(0.014930906f, recs.get(0).getValue());
    assertEquals(0.014744747f, recs.get(1).getValue());
    assertEquals(0.014339817f, recs.get(2).getValue());

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
    assertEquals(274L, recs.get(0).getItemID());

    client.setPreference(0L, 2L, -3.0f);
    recs = client.recommend(0L, 1);
    assertEquals(50L, recs.get(0).getItemID());

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
    assertEquals(222L, recs.get(0).getItemID());

    client.removePreference(0L, 2L);
    recs = client.recommend(0L, 1);
    assertEquals(2L, recs.get(0).getItemID());

    client.removePreference(0L, 1L);
    try {
      client.recommend(0L, 1);
      fail();
    } catch (NoSuchUserException nsue) {
      // good
    }
  }

}
