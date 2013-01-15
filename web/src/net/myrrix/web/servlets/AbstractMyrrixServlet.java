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
import java.util.SortedMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.common.Pair;

import net.myrrix.common.LangUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.ReloadingReference;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.random.RandomUtils;
import net.myrrix.online.RescorerProvider;
import net.myrrix.web.common.stats.ServletStats;

/**
 * Superclass of {@link HttpServlet}s used in the application. All API methods return the following 
 * HTTP statuses in certain situations:
 *
 * <ul>
 *  <li>{@code 302 Temporary Redirect} if, in a distributed environment, another partition should 
 *  handle the request</li>
 *  <li>{@code 400 Bad Request} if the arguments are invalid, like a non-numeric ID</li>
 *  <li>{@code 401 Unauthorized} if a username/password is required, but not supplied correctly 
 *  in the request via HTTP DIGEST</li>
 *  <li>{@code 405 Method Not Allowed} if an incorrect HTTP method is used, like {@code GET} 
 *  where {@code POST} is required</li>
 *  <li>{@code 500 Internal Server Error} if an unexpected server-side exception occurs</li>
 *  <li>{@code 503 Service Unavailable} if no model is yet available to serve requests</li>
 * </ul>
 *
 * @author Sean Owen
 */
public abstract class AbstractMyrrixServlet extends HttpServlet {

  private static final Splitter COMMA = Splitter.on(',').omitEmptyStrings().trimResults();
  static final Splitter SLASH = Splitter.on('/').omitEmptyStrings();
  static final int DEFAULT_HOW_MANY = 10;

  private static final String KEY_PREFIX = AbstractMyrrixServlet.class.getName();
  public static final String READ_ONLY_KEY = KEY_PREFIX + ".READ_ONLY";
  public static final String RECOMMENDER_KEY = KEY_PREFIX + ".RECOMMENDER";
  public static final String RESCORER_PROVIDER_KEY = KEY_PREFIX + ".RESCORER_PROVIDER";
  public static final String TIMINGS_KEY = KEY_PREFIX + ".TIMINGS";
  public static final String LOCAL_INPUT_DIR_KEY = KEY_PREFIX + ".LOCAL_INPUT_DIR";
  public static final String ALL_PARTITIONS_REF_KEY = KEY_PREFIX + ".ALL_PARTITIONS";
  public static final String PARTITION_KEY = KEY_PREFIX + ".PARTITION";

  private static final String[] NO_PARAMS = new String[0];

  private MyrrixRecommender recommender;
  private RescorerProvider rescorerProvider;
  private ServletStats timing;
  private ReloadingReference<List<List<Pair<String,Integer>>>> allPartitions;
  private int thisPartition;
  private ConcurrentMap<String,ResponseContentType> responseTypeCache;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    ServletContext context = config.getServletContext();
    recommender = (MyrrixRecommender) context.getAttribute(RECOMMENDER_KEY);
    rescorerProvider = (RescorerProvider) context.getAttribute(RESCORER_PROVIDER_KEY);

    @SuppressWarnings("unchecked")
    ReloadingReference<List<List<Pair<String,Integer>>>> theAllPartitions =
        (ReloadingReference<List<List<Pair<String,Integer>>>>) context.getAttribute(ALL_PARTITIONS_REF_KEY);
    allPartitions = theAllPartitions;

    thisPartition = (Integer) context.getAttribute(PARTITION_KEY);
    responseTypeCache = Maps.newConcurrentMap();

    Map<String,ServletStats> timings;
    synchronized (context) {
      timings = (Map<String,ServletStats>) context.getAttribute(TIMINGS_KEY);
      if (timings == null) {
        timings = Maps.newTreeMap();
        context.setAttribute(TIMINGS_KEY, timings);
      }
    }

    String key = getClass().getSimpleName();
    ServletStats theTiming = timings.get(key);
    if (theTiming == null) {
      theTiming = new ServletStats();
      timings.put(key, theTiming);
    }
    timing = theTiming;
  }

  @Override
  protected final void service(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    if (allPartitions != null) {
      List<List<Pair<String,Integer>>> thePartitions = allPartitions.get(1, TimeUnit.SECONDS);
      Long unnormalizedPartitionToServe = getUnnormalizedPartitionToServe(request);
      if (unnormalizedPartitionToServe != null) {
        int partitionToServe = LangUtils.mod(unnormalizedPartitionToServe, thePartitions.size());
        if (partitionToServe != thisPartition) {
          String redirectURL = buildRedirectToPartitionURL(request,
                                                           partitionToServe,
                                                           unnormalizedPartitionToServe,
                                                           thePartitions);
          response.sendRedirect(redirectURL);
          return;
        }
      }
      // else, we are the right partition to serve or no specific partition needed, so continue
      // Note that the client will send any traffic that does not require a particular partition to partition 0.
      // This logic just says that any partition is happy to try to answer such a request rather than forward it
      // to partition 0, but, it is a difference in behavior.
    }

    long start = System.nanoTime();
    super.service(request, response);
    timing.addTiming(System.nanoTime() - start);

    int status = response.getStatus();
    if (status >= 400) {
      if (status >= 500) {
        timing.incrementServerErrors();
      } else {
        timing.incrementClientErrors();
      }
    }
  }

  private static String buildRedirectToPartitionURL(HttpServletRequest request,
                                                    int toPartition,
                                                    long unnormalizedPartitionToServe,
                                                    List<List<Pair<String, Integer>>> thePartitions) {

    List<Pair<String,Integer>> replicas = thePartitions.get(toPartition);
    // Also determine (first) replica by hashing to preserve a predictable order of access
    // through the replicas for a given ID
    int chosenReplica = LangUtils.mod(RandomUtils.md5HashToLong(unnormalizedPartitionToServe), replicas.size());
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

  /**
   * @param request request containing info that may determine which partition needs to serve it
   * @return {@code null} if any partition may serve, or an integral value that should be used to
   *  determine the partiiton. This is usually an ID value, which will be possibly hashed and
   *  reduced modulo the number of partitions.
   */
  protected Long getUnnormalizedPartitionToServe(HttpServletRequest request) {
    return null; // Default: any partition is OK
  }

  protected final MyrrixRecommender getRecommender() {
    return recommender;
  }

  protected final RescorerProvider getRescorerProvider() {
    return rescorerProvider;
  }

  public final ServletStats getTiming() {
    return timing;
  }

  protected static int getHowMany(ServletRequest request) {
    String howManyString = request.getParameter("howMany");
    if (howManyString == null) {
      return DEFAULT_HOW_MANY;
    }
    int howMany = Integer.parseInt(howManyString);
    Preconditions.checkArgument(howMany > 0, "howMany must be positive");
    return howMany;
  }

  protected static String[] getRescorerParams(ServletRequest request) {
    String[] rescorerParams = request.getParameterValues("rescorerParams");
    return rescorerParams == null ? NO_PARAMS : rescorerParams;
  }

  protected static boolean getConsiderKnownItems(ServletRequest request) {
    return Boolean.valueOf(request.getParameter("considerKnownItems"));
  }

  /**
   * <p>Outputs items in CSV or JSON format based on the HTTP {@code Accept} header.</p>
   *
   * <p>CSV output contains one recommendation per line, and each line is of the form {@code itemID, strength},
   * like {@code 325, 0.53}. Strength is an opaque indicator of the relative quality of the recommendation.</p>
   *
   * <p>JSON output is an array of arrays, with each sub-array containing an item ID and strength.
   * Example: {@code [[325, 0.53], [98, 0.499]]}.</p>
   */
  protected final void output(HttpServletRequest request,
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

  /**
   * Determines the appropriate content type for the response based on request headers. At the moment these
   * are chosen from the values in {@link ResponseContentType}.
   */
  final ResponseContentType determineResponseType(HttpServletRequest request) {

    String acceptHeader = request.getHeader("Accept");
    if (acceptHeader == null) {
      return ResponseContentType.JSON;
    }
    ResponseContentType cached = responseTypeCache.get(acceptHeader);
    if (cached != null) {
      return cached;
    }

    SortedMap<Double,ResponseContentType> types = Maps.newTreeMap();
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

    ResponseContentType finalType;
    if (types.isEmpty()) {
      finalType = ResponseContentType.JSON;
    } else {
      finalType = types.values().iterator().next();
    }

    responseTypeCache.putIfAbsent(acceptHeader, finalType);
    return finalType;
  }

  /**
   * Outputs IDs in CSV or JSON format. When outputting CSV, one ID is written per line. When outputting
   * JSON, the output is an array of IDs.
   */
  final void outputIDs(HttpServletRequest request,ServletResponse response, FastIDSet ids) throws IOException {

    PrintWriter writer = response.getWriter();
    LongPrimitiveIterator it = ids.iterator();
    switch (determineResponseType(request)) {
      case JSON:
        writer.write('[');
        boolean first = true;
        while (it.hasNext()) {
          if (first) {
            first = false;
          } else {
            writer.write(',');
          }
          writer.write(Long.toString(it.nextLong()));
        }
        writer.write(']');
        break;
      case CSV:
        while (it.hasNext()) {
          writer.write(Long.toString(it.nextLong()));
          writer.write('\n');
        }
        break;
      default:
        throw new IllegalStateException("Unknown response type");
    }
  }

  /**
   * Available content types / formats for response bodies.
   */
  enum ResponseContentType {
    JSON,
    CSV,
  }

}
