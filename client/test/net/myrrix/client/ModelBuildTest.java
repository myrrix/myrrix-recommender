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
import java.io.StringReader;

import org.apache.mahout.common.iterator.FileLineIterable;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelBuildTest extends AbstractClientTest {

  private static final Logger log = LoggerFactory.getLogger(SimpleTest.class);

  private static final int FEATURES = 30;

  @Override
  protected String getTestDataPath() {
    return "testdata/empty";
  }

  @Override
  protected boolean callAwait() {
    return false;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    System.setProperty("model.features", Integer.toString(FEATURES));
    super.setUp();
  }

  @Test
  public void testWaitForBuild() throws Exception {
    ClientRecommender client = getClient();
    File testDataDir = new File("testdata/grouplens100K-45/filtered.csv");
    int count = 0;
    for (String line : new FileLineIterable(testDataDir)) {
      client.ingest(new StringReader(line));
      if ((++count % 3) == 0) {
        log.info("Ingested {} users", count);
        client.refresh(null);
        Thread.sleep(1000L);
        if (count >= FEATURES) {
          assertTrue(client.isReady());
          break;
        } else {
          assertFalse(client.isReady());
        }
      }
    }
  }

}
