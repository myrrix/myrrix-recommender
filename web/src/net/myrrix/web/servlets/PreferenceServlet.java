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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.io.Closeables;
import org.apache.commons.math3.util.FastMath;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.WeightedRunningAverage;

import net.myrrix.common.LangUtils;
import net.myrrix.common.MyrrixRecommender;

/**
 * <p>Responds to a POST request to {@code /pref/[userID]/[itemID]} and in turn calls
 * {@link MyrrixRecommender#setPreference(long, long, float)}. If the request body is empty,
 * the value is 1.0, otherwise the value in the request body's first line is used.</p>
 *
 * <p>Also responds to a DELETE request to the same path, with the same defaults. This corresponds
 * to calling {@link MyrrixRecommender#removePreference(long, long)} instead.</p>
 *
 * @author Sean Owen
 */
public final class PreferenceServlet extends AbstractMyrrixServlet {

  public static final String AVG_ESTIMATE_ERROR_KEY = PreferenceServlet.class.getName() + ".AVG_ESTIMATE_ERROR";

  private WeightedRunningAverage avgEstimateError;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    avgEstimateError = new WeightedRunningAverage();
    config.getServletContext().setAttribute(AVG_ESTIMATE_ERROR_KEY, avgEstimateError);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

    String pathInfo = request.getPathInfo();
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();

    long userID;
    long itemID;
    try {
      userID = Long.parseLong(pathComponents.next());
      itemID = Long.parseLong(pathComponents.next());
    } catch (NoSuchElementException nsee) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nsee.toString());
      return;
    }

    float prefValue;
    try {
      prefValue = readValue(request);
    } catch (IllegalArgumentException iae) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad value");
      return;
    }

    MyrrixRecommender recommender = getRecommender();
    try {

      // Experimental: see what the recommender thought before adding the datum, whether it would
      // have expected this. It "should" reproduce a 1, and the closer the better. Compute the
      // average difference from 1. Where the estimate is > 1, count it as a 0 difference.
      if (prefValue > 0.0f) {
        try {
          float estimate = recommender.estimatePreference(userID, itemID);
          avgEstimateError.addDatum(FastMath.max(0.0, 1.0 - estimate), prefValue);
        } catch (NoSuchUserException nsue) {
          // continue
        } catch (NoSuchItemException nsie) {
          // continue
        }
      }

      // Now set value
      recommender.setPreference(userID, itemID, prefValue);

    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    }
  }

  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String pathInfo = request.getPathInfo();
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();

    long userID;
    long itemID;
    try {
      userID = Long.parseLong(pathComponents.next());
      itemID = Long.parseLong(pathComponents.next());
    } catch (NoSuchElementException nsee) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nsee.toString());
      return;
    }

    MyrrixRecommender recommender = getRecommender();
    try {
      recommender.removePreference(userID, itemID);
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    }
  }

  private static float readValue(ServletRequest request) throws IOException {
    String line;
    BufferedReader reader = request.getReader();
    try {
      line = reader.readLine();
    } finally {
      Closeables.closeQuietly(reader);
    }
    return line != null && !line.isEmpty() ? LangUtils.parseFloat(line) : 1.0f;
  }

  @Override
  protected Integer getPartitionToServe(HttpServletRequest request, int numPartitions) {
    String pathInfo = request.getPathInfo();
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    long userID;
    try {
      userID = Long.parseLong(pathComponents.next());
    } catch (NoSuchElementException nsee) {
      return null;
    }
    return LangUtils.mod(userID, numPartitions);
  }

}
