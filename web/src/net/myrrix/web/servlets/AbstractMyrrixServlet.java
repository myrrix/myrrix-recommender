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

package net.myrrix.web.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentMap;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.RandomUtils;

import net.myrrix.common.LangUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.RunningStatistics;
import net.myrrix.online.RescorerProvider;

/**
 * Superclass of {@link HttpServlet}s used in the application.
 *
 * @author Sean Owen
 */
public abstract class AbstractMyrrixServlet extends HttpServlet {

  private static final Splitter COMMA = Splitter.on(',').omitEmptyStrings().trimResults();
  static final Splitter SLASH = Splitter.on('/').omitEmptyStrings();
  private static final int DEFAULT_HOW_MANY = 10;

  private static final String KEY_PREFIX = AbstractMyrrixServlet.class.getName();
  public static final String RECOMMENDER_KEY = KEY_PREFIX + ".RECOMMENDER";
  public static final String RESCORER_PROVIDER_KEY = KEY_PREFIX + ".RESCORER_PROVIDER";
  public static final String TIMINGS_KEY = KEY_PREFIX + ".TIMINGS";
  public static final String LOCAL_INPUT_DIR_KEY = KEY_PREFIX + ".LOCAL_INPUT_DIR";
  public static final String ALL_PARTITIONS_KEY = KEY_PREFIX + ".ALL_PARTITIONS";
  public static final String PARTITION_KEY = KEY_PREFIX + ".PARTITION";

  private static final String[] NO_PARAMS = new String[0];

  private MyrrixRecommender recommender;
  private RescorerProvider rescorerProvider;
  private RunningStatistics timing;
  private List<List<Pair<String,Integer>>> allPartitions;
  private Integer thisPartition;
  private Random random;
  private ConcurrentMap<String,ResponseContentType> responseTypeCache;

  @Override
  public final void init(ServletConfig config) throws ServletException {
    super.init(config);

    ServletContext context = config.getServletContext();
    recommender = (MyrrixRecommender) context.getAttribute(RECOMMENDER_KEY);
    rescorerProvider = (RescorerProvider) context.getAttribute(RESCORER_PROVIDER_KEY);
    allPartitions = (List<List<Pair<String,Integer>>>) context.getAttribute(ALL_PARTITIONS_KEY);
    thisPartition = (Integer) context.getAttribute(PARTITION_KEY);
    random = RandomUtils.getRandom();
    responseTypeCache = Maps.newConcurrentMap();

    Map<String,RunningStatistics> timings = (Map<String,RunningStatistics>) context.getAttribute(TIMINGS_KEY);
    if (timings == null) {
      timings = Maps.newHashMap();
      context.setAttribute(TIMINGS_KEY, timings);
    }
    String key = getClass().getSimpleName();
    RunningStatistics theTiming = timings.get(key);
    if (theTiming == null) {
      theTiming = new RunningStatistics();
      timings.put(key, theTiming);
    }
    timing = theTiming;
  }

  @Override
  protected final void service(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    if (allPartitions != null) {
      Integer partitionToServe = getPartitionToServe(request, allPartitions.size());
      if (partitionToServe != null && !partitionToServe.equals(thisPartition)) {
        String redirectURL = buildRedirectToPartitionURL(request, partitionToServe);
        response.sendRedirect(redirectURL);
        return;
      }
      // else, we are the right partition to serve or no specific partition needed, so continue
    }

    long start = System.nanoTime();
    super.service(request, response);
    timing.addDatum(System.nanoTime() - start);

    int status = response.getStatus();
    if (status >= 400) {
      if (status >= 500) {
        timing.incrementServerErrors();
      } else {
        timing.incrementClientErrors();
      }
    }
  }

  private String buildRedirectToPartitionURL(HttpServletRequest request, int toPartition) {

    List<Pair<String,Integer>> replicas = allPartitions.get(toPartition);
    int chosenReplica = random.nextInt(replicas.size());
    Pair<String,Integer> hostPort = replicas.get(chosenReplica);

    StringBuilder redirectURL = new StringBuilder();
    redirectURL.append(request.isSecure() ? "https" : "http").append("://");
    redirectURL.append(hostPort.getFirst()).append(':').append(hostPort.getSecond());
    redirectURL.append(request.getRequestURI());
    String query = request.getQueryString();
    if (query != null) {
      redirectURL.append('?').append(query);
    }

    return redirectURL.toString();
  }

  protected Integer getPartitionToServe(HttpServletRequest request, int numPartitions) {
    return null; // Default: any partition is OK
  }

  protected final MyrrixRecommender getRecommender() {
    return recommender;
  }

  protected final RescorerProvider getRescorerProvider() {
    return rescorerProvider;
  }

  public final RunningStatistics getTiming() {
    return timing;
  }

  protected static int getHowMany(ServletRequest request) {
    String howManyString = request.getParameter("howMany");
    return howManyString == null ? DEFAULT_HOW_MANY : Integer.parseInt(howManyString);
  }

  protected static String[] getRescorerParams(ServletRequest request) {
    String rescorerParamsString = request.getParameter("rescorerParams");
    return rescorerParamsString == null ? NO_PARAMS : new String[] { rescorerParamsString };
  }

  protected static boolean getConsiderKnownItems(ServletRequest request) {
    return Boolean.valueOf(request.getParameter("considerKnownItems"));
  }

  protected void output(HttpServletRequest request,
                        ServletResponse response,
                        Iterable<RecommendedItem> items) throws IOException {

    PrintWriter writer = response.getWriter();
    switch (determineResponseType(request)) {
      case JSON:
        writer.write('[');
        boolean first = true;
        for (RecommendedItem item : items) {
          if (first) {
            first = false;
          } else {
            writer.write(',');
          }
          writer.write('[');
          writer.write(Long.toString(item.getItemID()));
          writer.write(',');
          writer.write(Float.toString(item.getValue()));
          writer.write(']');
        }
        writer.write(']');
        break;
      case CSV:
        for (RecommendedItem item : items) {
          writer.write(Long.toString(item.getItemID()));
          writer.write(',');
          writer.write(Float.toString(item.getValue()));
          writer.write('\n');
        }
        break;
      default:
        throw new IllegalStateException("Unknown response type");
    }
  }

  private ResponseContentType determineResponseType(HttpServletRequest request) {

    String acceptHeader = request.getHeader("Accept");
    ResponseContentType cached = responseTypeCache.get(acceptHeader);
    if (cached != null) {
      return cached;
    }

    SortedMap<Double,ResponseContentType> types = Maps.newTreeMap();
    if (acceptHeader != null) {
      for (String accept : COMMA.split(acceptHeader)) {
        double preference;
        String type;
        int semiColon = accept.indexOf(';');
        if (semiColon < 0) {
          preference = 1.0;
          type = accept;
        } else {
          String valueString = accept.substring(semiColon + 1).trim();
          if (valueString.startsWith("q=")) {
            valueString = valueString.substring(2);
          }
          try {
            preference = LangUtils.parseDouble(valueString);
          } catch (IllegalArgumentException iae) {
            preference = 1.0;
          }
          type = accept.substring(semiColon);
        }
        ResponseContentType parsedType = null;
        if ("text/csv".equals(type) || "text/plain".equals(type)) {
          parsedType = ResponseContentType.CSV;
        } else if ("application/json".equals(type)) {
          parsedType = ResponseContentType.JSON;
        }
        if (parsedType != null) {
          types.put(preference, parsedType);
        }
      }
    }

    ResponseContentType finalType = ResponseContentType.JSON;
    if (!types.isEmpty()) {
      finalType = types.values().iterator().next();
    }

    responseTypeCache.putIfAbsent(acceptHeader, finalType);
    return finalType;
  }

  private enum ResponseContentType {
    JSON,
    CSV,
  }

}
