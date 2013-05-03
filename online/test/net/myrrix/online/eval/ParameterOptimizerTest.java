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
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

import com.google.common.collect.Maps;
import org.apache.mahout.cf.taste.common.TasteException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.myrrix.common.MyrrixTest;

/**
 * Simple proof of concept test using {@link ParameterOptimizer}.
 *
 * @author Sean Owen
 */
public final class ParameterOptimizerTest extends MyrrixTest {

  private static final Logger log = LoggerFactory.getLogger(ParameterOptimizerTest.class);

  @Test
  public void testFindGoodParameterValues() throws Exception {
    Map<String,ParameterRange> propertyRanges = Maps.newHashMap();
    propertyRanges.put("model.als.lambda", new ParameterRange(0.0001, 0.1));
    propertyRanges.put("model.features", new ParameterRange(10, 40));
    Callable<Number> evaluator = new Callable<Number>() {
      @Override
      public Number call() throws IOException, TasteException, InterruptedException {
        PrecisionRecallEvaluator prEvaluator = new PrecisionRecallEvaluator();
        MyrrixIRStatistics stats = 
            (MyrrixIRStatistics) prEvaluator.evaluate(new File("testdata/grouplens100K"), 0.9, 0.5, null);
        return stats.getMeanAveragePrecision();
      }
    };
    ParameterOptimizer optimizer = new ParameterOptimizer(propertyRanges, evaluator);
    Map<String,Number> parameterValues = optimizer.findGoodParameterValues();
    log.info(parameterValues.toString());
    assertEquals(0.1, parameterValues.get("model.als.lambda").doubleValue());
    assertEquals(23, parameterValues.get("model.features").intValue());
  }

}
