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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.common.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>This is an experimental utility class which can find a nearly-optimal set of learning algorithm parameters,
 * for a given set of parameters and range of values, and a given metric to optimize. It simply performs an  
 * exhaustive search.</p>
 * 
 * <p>It can be run on the command line with:</p>
 * 
 * <p>{@code java net.myrrix.online.eval.ParameterOptimizer dataDirectory property=min:max [property2=min2:max2 ...]}</p>
 * 
 * @author Sean Owen
 */
public final class ParameterOptimizer implements Callable<Map<String,Number>> {
  
  private static final Logger log = LoggerFactory.getLogger(ParameterOptimizer.class);
  
  private final Map<String,ParameterRange> parameterRanges;
  private final int numSteps;  
  private final Callable<? extends Number> evaluator;
  private final boolean minimize;
  
  public ParameterOptimizer(Map<String,ParameterRange> parameterRanges,
                            Callable<? extends Number> evaluator) {
    this(parameterRanges, 4, evaluator, false);
  }

  /**
   * @param parameterRanges mapping between names of {@link System} properties whose parameters will be optimized
   *  (e.g. {@code model.als.lambda}), and a {@link ParameterRange} describing a range of parameter values to try
   * @param numSteps number of different values of each parameter to try. Note that with m parameters, and
   *  n steps, running time will scale proportionally to n^m
   * @param evaluator the objective to maximize (or minimize). This typically wraps a call to something like
   *  {@link PrecisionRecallEvaluator}
   * @param minimize if {@code true}, find values that maximize {@code evaluator}'s value, otherwise minimize
   */
  public ParameterOptimizer(Map<String,ParameterRange> parameterRanges, 
                            int numSteps,
                            Callable<? extends Number> evaluator,
                            boolean minimize) {
    Preconditions.checkNotNull(parameterRanges);
    Preconditions.checkArgument(!parameterRanges.isEmpty(), "parameterRanges is empty");
    Preconditions.checkArgument(numSteps >= 2);
    Preconditions.checkNotNull(evaluator);
    this.parameterRanges = parameterRanges;
    this.numSteps = numSteps;
    this.evaluator = evaluator;
    this.minimize = minimize;
  }

  /**
   * @return {@link #findGoodParameterValues()}
   */
  @Override
  public Map<String,Number> call() throws ExecutionException {
    return findGoodParameterValues();
  }

  /**
   * @return a {@link Map} between the values of the given {@link System} properties and the best value found
   *  during search
   * @throws ExecutionException if an error occurs while calling {@code evaluator}; the cause is the
   *  underlying exception
   */
  public Map<String,Number> findGoodParameterValues() throws ExecutionException {
    
    int numProperties = parameterRanges.size();
    String[] propertyNames = new String[numProperties];    
    Number[][] parameterValuesToTry = new Number[numProperties][];
    int index = 0;
    for (Map.Entry<String,ParameterRange> entry : parameterRanges.entrySet()) {
      propertyNames[index] = entry.getKey();
      parameterValuesToTry[index] = entry.getValue().buildSteps(numSteps);
      index++;
    }
    
    int numTests = 1;
    for (int i = 0; i < numProperties; i++) {
      numTests *= numSteps;
    }
    
    List<Pair<Double,String>> testResultLinesByValue = Lists.newArrayListWithCapacity(numTests);
    
    Map<String,Number> bestParameterValues = Maps.newHashMap();
    double bestValue = minimize ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
    
    for (int test = 0; test < numTests; test++) {
      
      StringBuilder testResultLine = new StringBuilder();      
      for (int prop = 0; prop < numProperties; prop++) {
        String property = propertyNames[prop];
        Number parameterValue = getParameterValueToTry(parameterValuesToTry, test, prop);
        String propertyString = parameterValue.toString();
        log.info("Setting {}={}", property, propertyString);
        System.setProperty(property, propertyString);
        testResultLine.append('[').append(property).append('=').append(propertyString).append("] ");        
      }
      
      double testValue;
      try {
        testValue = evaluator.call().doubleValue();
      } catch (Exception e) {
        throw new ExecutionException(e);
      }
      testResultLine.append("= ").append(testValue);
      testResultLinesByValue.add(Pair.of(testValue, testResultLine.toString()));
      log.info("{}", testResultLine);
      
      if (minimize ? testValue < bestValue : testValue > bestValue) {
        log.info("New best value {}", testValue);
        bestValue = testValue;
        for (int prop = 0; prop < numProperties; prop++) {
          String property = propertyNames[prop];
          Number parameterValue = getParameterValueToTry(parameterValuesToTry, test, prop);
          bestParameterValues.put(property, parameterValue);
        }
      }
      
      Collections.sort(testResultLinesByValue, Collections.reverseOrder());
      for (Pair<Double,String> result : testResultLinesByValue) {
        log.info("{}", result.getSecond());
      }
      log.info("Best parameter values so far are {}", bestParameterValues);      
    }
    
    log.info("Final best parameter values are {}", bestParameterValues);
    return bestParameterValues;
  }

  private Number getParameterValueToTry(Number[][] parameterValuesToTry, int test, int prop) {
    int whichValueToTry = test;
    for (int i = 0; i < prop; i++) {
      whichValueToTry /= numSteps;
    }
    whichValueToTry %= numSteps;
    return parameterValuesToTry[prop][whichValueToTry];
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: dataDirectory property=min:max [property2=min2:max2 ...]");
    }
    
    final File dataDir = new File(args[0]);
    
    Map<String,ParameterRange> parameterRanges = Maps.newHashMapWithExpectedSize(args.length);
    for (int i = 1; i < args.length; i++) {
      String[] propValue = args[i].split("=");
      String systemProperty = propValue[0];
      String[] minMax = propValue[1].split(":");
      ParameterRange range;
      try {
        int min = Integer.parseInt(minMax[0]);
        int max = Integer.parseInt(minMax[1]);
        range = new ParameterRange(min, max, true);
      } catch (NumberFormatException ignored) {
        double min = Double.parseDouble(minMax[0]);
        double max = Double.parseDouble(minMax[1]);
        range = new ParameterRange(min, max, false);
      }
      parameterRanges.put(systemProperty, range);
    }
    
    Callable<Number> evaluator = new Callable<Number>() {
      @Override
      public Number call() throws IOException, TasteException, InterruptedException {
        PrecisionRecallEvaluator prEvaluator = new PrecisionRecallEvaluator();
        MyrrixIRStatistics stats = (MyrrixIRStatistics) prEvaluator.evaluate(dataDir);
        return stats.getMeanAveragePrecision();
      }
    };
    
    ParameterOptimizer optimizer = new ParameterOptimizer(parameterRanges, evaluator);
    Map<String,Number> optimalValues = optimizer.findGoodParameterValues();
    System.out.println(optimalValues);
  }

}
