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

public final class SimpleTest extends AbstractClientTest {

  private static final Logger log = LoggerFactory.getLogger(SimpleTest.class);

  @Override
  protected String getTestDataPath() {
    return "testdata/grouplens100K";
  }

  @Test
  public void testIngest() throws Exception {
    StringReader reader = new StringReader("0,1\n0,2,3.0\n");

    ClientRecommender client = getClient();
    client.ingest(reader);

    List<RecommendedItem> recs = client.recommend(0L, 1);
    log.info("{}", recs);
    assertEquals(450L, recs.get(0).getItemID());
  }

  @Test
  public void testRecommend() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> recs = client.recommend(1L, 3);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(421L, recs.get(0).getItemID());
    assertEquals(1.052002, recs.get(0).getValue(), EPSILON);
    assertEquals(558L, recs.get(1).getItemID());
    assertEquals(1.041733, recs.get(1).getValue(), EPSILON);
    assertEquals(919L, recs.get(2).getItemID());
    assertEquals(1.0302302, recs.get(2).getValue(), EPSILON);

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
    assertEquals(1.052002, recs.get(0).getValue(), EPSILON);
    assertEquals(558L, recs.get(1).getItemID());
    assertEquals(1.041733, recs.get(1).getValue(), EPSILON);
    assertEquals(919L, recs.get(2).getItemID());
    assertEquals(1.0302302, recs.get(2).getValue(), EPSILON);
  }

  @Test
  public void testRecommendToMany() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> recs = client.recommendToMany(new long[] {1L, 3L}, 3, false, null);

    assertNotNull(recs);
    assertEquals(3, recs.size());

    log.info("{}", recs);

    assertEquals(288L, recs.get(0).getItemID());
    assertEquals(0.9875564, recs.get(0).getValue(), EPSILON);
    assertEquals(302L, recs.get(1).getItemID());
    assertEquals(0.98385394, recs.get(1).getValue(), EPSILON);
    assertEquals(313L, recs.get(2).getItemID());
    assertEquals(0.97271514, recs.get(2).getValue(), EPSILON);
  }

  @Test
  public void testMostSimilar() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> similar = client.mostSimilarItems(1L, 3);

    assertNotNull(similar);
    assertEquals(3, similar.size());

    log.info("{}", similar);

    assertEquals(181L, similar.get(0).getItemID());
    assertEquals(0.9938064, similar.get(0).getValue(), EPSILON);
    assertEquals(50L, similar.get(1).getItemID());
    assertEquals(0.992679, similar.get(1).getValue(), EPSILON);
    assertEquals(222L, similar.get(2).getItemID());
    assertEquals(0.99233454, similar.get(2).getValue(), EPSILON);

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

    assertEquals(0.8138986, estimates[0], EPSILON);
    assertEquals(0.86908484, estimates[1], EPSILON);
    assertEquals(0.92171675, estimates[2], EPSILON);

    try {
      client.estimatePreference(0L, 90L);
      fail();
    } catch (NoSuchItemException nsie) {
      // good
    }
    try {
      client.estimatePreference(10L, 0L);
      fail();
    } catch (NoSuchItemException nsie) {
      // good
    }
  }

  @Test
  public void testBecause() throws Exception {

    ClientRecommender client = getClient();
    List<RecommendedItem> because = client.recommendedBecause(1L, 321, 3);

    assertNotNull(because);
    assertEquals(3, because.size());

    log.info("{}", because);

    assertEquals(269L, because.get(0).getItemID());
    assertEquals(0.98161334, because.get(0).getValue(), EPSILON);
    assertEquals(268L, because.get(1).getItemID());
    assertEquals(0.9668592, because.get(1).getValue(), EPSILON);
    assertEquals(258L, because.get(2).getItemID());
    assertEquals(0.9641519, because.get(2).getValue(), EPSILON);

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
    assertEquals(0.0074557513, recs.get(0).getValue(), EPSILON);
    assertEquals(127L, recs.get(1).getItemID());
    assertEquals(0.007198733, recs.get(1).getValue(), EPSILON);
    assertEquals(269L, recs.get(2).getItemID());
    assertEquals(0.0071009295, recs.get(2).getValue(), EPSILON);

    try {
      client.recommendToAnonymous(new long[]{0L}, 3);
      fail();
    } catch (NoSuchItemException nsie) {
      // good
    }
  }

  @Test
  public void testSetRemove() throws Exception {

    ClientRecommender client = getClient();

    client.setPreference(0L, 1L);

    List<RecommendedItem> recs = client.recommend(0L, 1);
    log.info("{}", recs);
    assertEquals(127L, recs.get(0).getItemID());

    client.setPreference(0L, 2L, 3.0f);

    recs = client.recommend(0L, 1);
    log.info("{}", recs);
    assertEquals(450L, recs.get(0).getItemID());

    client.removePreference(0L, 1L);

    recs = client.recommend(0L, 1);
    log.info("{}", recs);
    assertEquals(450L, recs.get(0).getItemID());
  }

}
