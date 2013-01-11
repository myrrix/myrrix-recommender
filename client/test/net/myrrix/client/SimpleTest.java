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
    assertEquals(1016L, recs.get(0).getItemID());
  }

  @Test
  public void testRecommend() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> recs = client.recommend(1L, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(116L, recs.get(0).getItemID());
    assertEquals(709L, recs.get(1).getItemID());
    assertEquals(473L, recs.get(2).getItemID());
    assertEquals(1.0162915f, recs.get(0).getValue());
    assertEquals(1.0112138f, recs.get(1).getValue());
    assertEquals(1.0086571f, recs.get(2).getValue());

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

    assertEquals(248L, recs.get(0).getItemID());
    assertEquals(20L, recs.get(1).getItemID());
    assertEquals(116L, recs.get(2).getItemID());
    assertEquals(1.0250105f, recs.get(0).getValue());
    assertEquals(1.0181392f, recs.get(1).getValue());
    assertEquals(1.0162915f, recs.get(2).getValue());
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

    assertEquals(302L, recs.get(0).getItemID());
    assertEquals(258L, recs.get(1).getItemID());
    assertEquals(288L, recs.get(2).getItemID());
    assertEquals(0.947787f, recs.get(0).getValue());
    assertEquals(0.93904513f, recs.get(1).getValue());
    assertEquals(0.9388422f, recs.get(2).getValue());
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

    assertEquals(249L, recs.get(0).getItemID());
    assertEquals(1016L, recs.get(1).getItemID());
    assertEquals(24L, recs.get(2).getItemID());
    assertEquals(0.007965666f, recs.get(0).getValue());
    assertEquals(0.007924121f, recs.get(1).getValue());
    assertEquals(0.0076457425f, recs.get(2).getValue());
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
    assertEquals(100L, popular.get(1).getItemID());
    assertEquals(181L, popular.get(2).getItemID());
    assertEquals(501.0f, popular.get(0).getValue());
    assertEquals(406.0f, popular.get(1).getValue());
    assertEquals(379.0f, popular.get(2).getValue());
  }

  @Test
  public void testMostSimilar() throws Exception {

    ClientRecommender client = getClient();
    // Adding non-existent item to make sure it is ignored
    List<RecommendedItem> similar = client.mostSimilarItems(new long[] {449L, Integer.MAX_VALUE}, 3);

    assertNotNull(similar);
    assertEquals(3, similar.size());

    log.info("{}", similar);

    assertEquals(380L, similar.get(0).getItemID());
    assertEquals(229L, similar.get(1).getItemID());
    assertEquals(227L, similar.get(2).getItemID());
    assertEquals(0.9569881f, similar.get(0).getValue());
    assertEquals(0.94163465f, similar.get(1).getValue());
    assertEquals(0.9291256f, similar.get(2).getValue());
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

    assertEquals(0.71336436f, estimates[0]);
    assertEquals(0.88419616f, estimates[1]);
    assertEquals(0.8799375f, estimates[2]);

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
    assertEquals(268L, because.get(1).getItemID());
    assertEquals(270L, because.get(2).getItemID());
    assertEquals(0.8747143f, because.get(0).getValue());
    assertEquals(0.86328393f, because.get(1).getValue());
    assertEquals(0.8524117f, because.get(2).getValue());

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

    assertEquals(529L, recs.get(0).getItemID());
    assertEquals(855L, recs.get(1).getItemID());
    assertEquals(45L, recs.get(2).getItemID());
    assertEquals(0.0028280937f, recs.get(0).getValue());
    assertEquals(0.0028104493f, recs.get(1).getValue());
    assertEquals(0.0027638678f , recs.get(2).getValue());

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

    assertEquals(529L, recs.get(0).getItemID());
    assertEquals(855L, recs.get(1).getItemID());
    assertEquals(45L, recs.get(2).getItemID());
    assertEquals(0.0028280937f, recs.get(0).getValue());
    assertEquals(0.0028104493f, recs.get(1).getValue());
    assertEquals(0.0027638678f , recs.get(2).getValue());

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

    assertEquals(646L, recs.get(0).getItemID());
    assertEquals(183L, recs.get(1).getItemID());
    assertEquals(657L, recs.get(2).getItemID());
    assertEquals(0.0060613565f, recs.get(0).getValue());
    assertEquals(0.0059376727f, recs.get(1).getValue());
    assertEquals(0.005851781f, recs.get(2).getValue());

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
    assertEquals(1016L, recs.get(0).getItemID());

    client.setPreference(0L, 2L, -3.0f);
    recs = client.recommend(0L, 1);
    assertEquals(1016L, recs.get(0).getItemID());

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
    assertEquals(1016L, recs.get(0).getItemID());

    client.removePreference(0L, 2L);
    recs = client.recommend(0L, 1);
    assertEquals(1016L, recs.get(0).getItemID());

    client.removePreference(0L, 1L);
    try {
      client.recommend(0L, 1);
      fail();
    } catch (NoSuchUserException nsue) {
      // good
    }
  }

}
