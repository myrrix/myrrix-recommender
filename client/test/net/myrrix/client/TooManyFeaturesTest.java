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

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests case where {@code model.features} is  too high and should not make a valid model.
 * 
 * @author Sean Owen
 */
public final class TooManyFeaturesTest extends AbstractClientTest {

  @Override
  protected String getTestDataPath() {
    return "testdata/tiny";
  }
  
  @Override
  protected boolean callAwait() {
    return false;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    System.setProperty("model.features", "6");
    super.setUp();
  }

  @Test
  public void testWaitForBuild() throws Exception {
    ClientRecommender client = getClient();
    client.await(3, TimeUnit.SECONDS);
    assertFalse(client.isReady());
  }

}
