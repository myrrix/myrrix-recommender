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
 * Runs a test over small data that needs, really, at most 5 features. Tests whether the framework will 
 * reduce from the default of 30 down to 5 automatically.
 * 
 * @author Sean Owen
 */
public final class ReducedFeaturesTest extends AbstractClientTest {

  private static final Logger log = LoggerFactory.getLogger(ReducedFeaturesTest.class);

  @Override
  protected String getTestDataPath() {
    return "testdata/tiny";
  }

  @Test
  public void testTinyRecommend() throws Exception {
    ClientRecommender client = getClient();
    assertEquals(5, Integer.parseInt(System.getProperty("model.features")));
    List<RecommendedItem> recs = client.recommend(5, 2);
    log.info("{}", recs);
    assertEquals(2, recs.get(0).getItemID());
    assertEquals(4, recs.get(1).getItemID());
    assertEquals(0.095224485f, recs.get(0).getValue());
    assertEquals(0.034361064f, recs.get(1).getValue());
  }

}
