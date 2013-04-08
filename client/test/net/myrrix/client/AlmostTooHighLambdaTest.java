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

import java.io.File;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests case where {@code model.als.lambda} is almost too high but should still allow a valid model.
 * 
 * @author Sean Owen
 */
public final class AlmostTooHighLambdaTest extends AbstractClientTest {

  @Override
  protected String getTestDataPath() {
    return "testdata/highlambda";
  }

  @Override
  @Before
  public void setUp() throws Exception {
    System.setProperty("model.als.lambda", "0.9");
    super.setUp();
  }

  @Test
  public void testWaitForBuild() throws Exception {
    ClientRecommender client = getClient();
    client.ingest(new File(getTestTempDir(), "myrrix.model.csv"));
  }

}
