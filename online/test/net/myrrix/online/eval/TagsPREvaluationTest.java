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

package net.myrrix.online.eval;

import java.io.File;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.MyrrixTest;

public final class TagsPREvaluationTest extends MyrrixTest {

  private static final Logger log = LoggerFactory.getLogger(TagsPREvaluationTest.class);

  @Test
  public void testEval() throws Exception {
    PrecisionRecallEvaluator evaluator = new PrecisionRecallEvaluator();
    MyrrixIRStatistics stats = (MyrrixIRStatistics) evaluator.evaluate(new File("testdata/tagseval"));
    log.info(stats.toString());
    // Just checking it doesn't throw an error!
  }

}
