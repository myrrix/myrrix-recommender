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

package net.myrrix.client.eval;

import java.io.File;

import com.google.common.base.Preconditions;
import com.google.common.io.PatternFilenameFilter;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.MyrrixTest;

/**
 * Simple proof of concept test using {@link EstimatedStrengthEvaluator}.
 *
 * @author Sean Owen
 */
public final class EstimatedStrengthEvaluationTest extends MyrrixTest {

  private static final Logger log = LoggerFactory.getLogger(EstimatedStrengthEvaluationTest.class);

  private final File dataDir;

  public EstimatedStrengthEvaluationTest() {
    this.dataDir = new File("testdata/grouplens10M");
  }

  @Test
  public void testEval() throws Exception {
    File[] originalDataFiles = dataDir.listFiles(new PatternFilenameFilter(".+\\.csv(\\.(zip|gz))?"));
    Preconditions.checkState(originalDataFiles != null && originalDataFiles.length == 1,
                             "Expected one input file in %s", dataDir);
    EstimatedStrengthEvaluator evaluator = new EstimatedStrengthEvaluator(originalDataFiles[0], getTestTempDir(), 0.9, 0.1);
    EvaluationResult stats = evaluator.evaluate();
    log.info(stats.toString());
    assertTrue(stats.getScore() > 0.5);
  }

}
