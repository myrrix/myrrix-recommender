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
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    List<RecommendedItem> recs = client.recommend(0L, 1);
    log.info("{}", recs);
    assertEquals(449L, recs.get(0).getItemID());
  }

  @Test
  public void testRecommend() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> recs = client.recommend(1L, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(421L, recs.get(0).getItemID());
    assertEquals(1.2138307f, recs.get(0).getValue());
    assertEquals(919L, recs.get(1).getItemID());
    assertEquals(1.1859218f, recs.get(1).getValue());
    assertEquals(477L, recs.get(2).getItemID());
    assertEquals(1.1580303f, recs.get(2).getValue());

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

    assertEquals(421L, recs.get(0).getItemID());
    assertEquals(1.2138307f, recs.get(0).getValue());
    assertEquals(919L, recs.get(1).getItemID());
    assertEquals(1.1859218f, recs.get(1).getValue());
    assertEquals(477L, recs.get(2).getItemID());
    assertEquals(1.1580303f, recs.get(2).getValue());
  }

  @Test
  public void testRecommendToMany() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> recs = client.recommendToMany(new long[] {1L, 3L}, 3, false, null);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(270L, recs.get(0).getItemID());
    assertEquals(1.0426435f, recs.get(0).getValue());
    assertEquals(302L, recs.get(1).getItemID());
    assertEquals(1.0192398f, recs.get(1).getValue());
    assertEquals(288L, recs.get(2).getItemID());
    assertEquals(1.0047475f, recs.get(2).getValue());
  }

  @Test
  public void testMostSimilar() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> similar = client.mostSimilarItems(1L, 3);

    assertNotNull(similar);
    assertEquals(3, similar.size());

    log.info("{}", similar);

    assertEquals(181L, similar.get(0).getItemID());
    assertEquals(0.96329296f, similar.get(0).getValue());
    assertEquals(50L, similar.get(1).getItemID());
    assertEquals(0.96242565f, similar.get(1).getValue());
    assertEquals(100L, similar.get(2).getItemID());
    assertEquals(0.96033472f, similar.get(2).getValue());

    try {
      client.mostSimilarItems(0L, 3);
      fail();
    } catch (NoSuchItemException nsie) {
      // good
    }
  }

  @Test
  public void testEstimate() throws Exception {

    ClientRecommender client = getClient();
    float[] estimates = client.estimatePreferences(10L, 90L, 91L, 92L);

    assertNotNull(estimates);
    assertEquals(3, estimates.length);

    log.info(Arrays.toString(estimates));

    assertEquals(0.6964008f, estimates[0]);
    assertEquals(0.88412833f, estimates[1]);
    assertEquals(0.9059035f, estimates[2]);

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
    assertEquals(0.92528331f, because.get(0).getValue());
    assertEquals(258L, because.get(1).getItemID());
    assertEquals(0.86642462f, because.get(1).getValue());
    assertEquals(268L, because.get(2).getItemID());
    assertEquals(0.85865098f, because.get(2).getValue());

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
    List<RecommendedItem> recs = client.recommendToAnonymous(new long[] {100L}, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(286L, recs.get(0).getItemID());
    assertEquals(0.010693396f, recs.get(0).getValue());
    assertEquals(258L, recs.get(1).getItemID());
    assertEquals(0.010458233f, recs.get(1).getValue());
    assertEquals(288L, recs.get(2).getItemID());
    assertEquals(0.010337293f, recs.get(2).getValue());

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
    assertEquals(449L, recs.get(0).getItemID());

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
    assertEquals(181L, recs.get(0).getItemID());

    client.removePreference(0L, 2L);
    recs = client.recommend(0L, 1);
    assertEquals(181L, recs.get(0).getItemID());

    client.removePreference(0L, 1L);
    try {
      client.recommend(0L, 1);
      fail();
    } catch (NoSuchUserException nsue) {
      // good
    }
  }

}
