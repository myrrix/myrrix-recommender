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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an experimental utility class which can find a nearly-optimal set of learning algorithm parameters,
 * for a given set of parameters and range of values, and a given metric to optimize. It simply performs an  
 * exhaustive search.
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
    Preconditions.checkArgument(!parameterRanges.isEmpty());
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
    
    List<String> testResultLines = Lists.newArrayListWithCapacity(numTests);
    
    Map<String,Number> bestParameterValues = Maps.newHashMap();
    double bestValue = minimize ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
    
    for (int test = 0; test < numTests; test++) {
      
      StringBuilder testResultLine = new StringBuilder();      
      for (int prop = 0; prop < numProperties; prop++) {
        String property = propertyNames[prop];
        Number parameterValue = getParameterValueToTry(parameterValuesToTry, test, prop);
        String propertyString = parameterValue.toString();
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
      testResultLines.add(testResultLine.toString());
      
      if (minimize ? testValue < bestValue : testValue > bestValue) {
        bestValue = testValue;
        for (int prop = 0; prop < numProperties; prop++) {
          String property = propertyNames[prop];
          Number parameterValue = getParameterValueToTry(parameterValuesToTry, test, prop);
          bestParameterValues.put(property, parameterValue);
        }
      }
    }
    
    for (String testResultLine : testResultLines) {
      log.info(testResultLine);
    }
    
    log.info("Best parameter values are {}", bestParameterValues);
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

}
