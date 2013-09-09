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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.math3.util.Pair;
import org.apache.mahout.cf.taste.common.TasteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>This is an experimental utility class which can find a nearly-optimal set of learning algorithm parameters,
 * for a given set of parameters and range of values, and a given metric to optimize. It simply performs an  
 * exhaustive search.</p>
 * 
 * <p>It can be run on the command line with:</p>
 * 
 * <p>{@code java net.myrrix.online.eval.ParameterOptimizer dataDirectory numSteps evaluationPercentage
 *  property=min:max [property2=min2:max2 ...]}</p>
 *  
 * <ul>
 *   <li>{@code dataDirectory}: directory containing test data</li>
 *   <li>{@code numSteps}: number of different values of each parameter to try. Generally use 3-5.</li>
 *   <li>{@code evaluationPercentage}: fraction of all data to use in the test. Lower this to down-sample
 *    a very large data set. Must be in (0,1].</li>
 *   <li>{@code property=min:max}: repeated argument specifying a system property and the range of values 
 *    to try, inclusive</li>
 * </ul>
 * 
 * @author Sean Owen
 * @since 1.0
 */
public final class ParameterOptimizer implements Callable<Map<String,Number>> {
  
  private static final Logger log = LoggerFactory.getLogger(ParameterOptimizer.class);
  private static final Pattern EQUALS = Pattern.compile("=");
  private static final Pattern COLON = Pattern.compile(":");

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
   *  n steps, running time will scale proportionally to n<sup>m</sup>
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
    for (Number[] toTry : parameterValuesToTry) {
      numTests *= toTry.length;
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

      Number evaluatorResult;
      try {
        evaluatorResult = evaluator.call();
      } catch (Exception e) {
        throw new ExecutionException(e);
      }
      if (evaluatorResult == null) {
        continue;
      }
      double testValue = evaluatorResult.doubleValue();
      testResultLine.append("= ").append(testValue);
      testResultLinesByValue.add(new Pair<Double,String>(testValue, testResultLine.toString()));
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
      
      Collections.sort(testResultLinesByValue, new Comparator<Pair<Double,String>>() {
        @Override
        public int compare(Pair<Double,String> a, Pair<Double,String> b) {
          if (a.getFirst() > b.getFirst()) {
            return -1;
          }
          if (a.getFirst() < b.getFirst()) {
            return 1;
          }
          return 0;
        }
      });
      
      for (Pair<Double,String> result : testResultLinesByValue) {
        log.info("{}", result.getSecond());
      }
      log.info("Best parameter values so far are {}", bestParameterValues);      
    }
    
    log.info("Final best parameter values are {}", bestParameterValues);
    return bestParameterValues;
  }

  private static Number getParameterValueToTry(Number[][] parameterValuesToTry, int test, int prop) {
    int whichValueToTry = test;
    for (int i = 0; i < prop; i++) {
      whichValueToTry /= parameterValuesToTry[i].length;
    }
    whichValueToTry %= parameterValuesToTry[prop].length;
    return parameterValuesToTry[prop][whichValueToTry];
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 4) {
      System.err.println(
          "Usage: dataDirectory numSteps evaluationPercentage property=min:max [property2=min2:max2 ...]");
      return;
    }
    
    final File dataDir = new File(args[0]);
    Preconditions.checkArgument(dataDir.exists() && dataDir.isDirectory(), "Not a directory: %s", dataDir);
    Preconditions.checkArgument(dataDir.listFiles().length > 0, "No files in: %s", dataDir);
    int numSteps = Integer.parseInt(args[1]);
    Preconditions.checkArgument(numSteps >= 2, "# steps must be at least 2: %s", numSteps);
    final double evaluationPercentage = Double.parseDouble(args[2]);
    Preconditions.checkArgument(evaluationPercentage > 0.0 && evaluationPercentage <= 1.0,
                                "evaluationPercentage must be in (0,1]: %s", evaluationPercentage);
    
    Map<String,ParameterRange> parameterRanges = Maps.newHashMapWithExpectedSize(args.length);
    for (int i = 3; i < args.length; i++) {
      String[] propValue = EQUALS.split(args[i]);
      String systemProperty = propValue[0];
      String[] minMax = COLON.split(propValue[1]);
      ParameterRange range;
      try {
        int min = Integer.parseInt(minMax[0]);
        int max = Integer.parseInt(minMax.length == 1 ? minMax[0] : minMax[1]);
        range = new ParameterRange(min, max);
      } catch (NumberFormatException ignored) {
        double min = Double.parseDouble(minMax[0]);
        double max = Double.parseDouble(minMax.length == 1 ? minMax[0] : minMax[1]);
        range = new ParameterRange(min, max);
      }
      parameterRanges.put(systemProperty, range);
    }
    
    Callable<Number> evaluator = new Callable<Number>() {
      @Override
      public Number call() throws IOException, TasteException, InterruptedException {
        MyrrixIRStatistics stats = (MyrrixIRStatistics)
            new PrecisionRecallEvaluator().evaluate(dataDir, 0.9, evaluationPercentage, null);
        return stats == null ? null : stats.getMeanAveragePrecision();
      }
    };
    
    ParameterOptimizer optimizer = new ParameterOptimizer(parameterRanges, numSteps, evaluator, false);
    Map<String,Number> optimalValues = optimizer.findGoodParameterValues();
    System.out.println(optimalValues);
  }

}
