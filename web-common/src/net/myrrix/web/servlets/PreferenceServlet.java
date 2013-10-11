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
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.mahout.cf.taste.common.TasteException;

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
 * @since 1.0
 */
public final class PreferenceServlet extends AbstractMyrrixServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

    CharSequence pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No path");
      return;
    }
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    long userID;
    long itemID;
    try {
      userID = Long.parseLong(pathComponents.next());
      itemID = Long.parseLong(pathComponents.next());
    } catch (NoSuchElementException nsee) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nsee.toString());
      return;
    } catch (NumberFormatException nfe) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nfe.toString());
      return;
    }
    if (pathComponents.hasNext()) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Path too long");
      return;
    }

    float prefValue;
    try {
      prefValue = readValue(request);
    } catch (IllegalArgumentException ignored) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad value");
      return;
    }

    MyrrixRecommender recommender = getRecommender();
    try {
      recommender.setPreference(userID, itemID, prefValue);
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    }
  }

  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
    CharSequence pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No path");
      return;
    }
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    long userID;
    long itemID;
    try {
      userID = Long.parseLong(pathComponents.next());
      itemID = Long.parseLong(pathComponents.next());
    } catch (NoSuchElementException nsee) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nsee.toString());
      return;
    } catch (NumberFormatException nfe) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nfe.toString());
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

  static float readValue(ServletRequest request) throws IOException {
    String line;
    BufferedReader reader = request.getReader();
    try {
      line = reader.readLine();
    } finally {
      reader.close();
    }
    if (line == null || line.isEmpty()) {
      return 1.0f;
    }
    return LangUtils.parseFloat(line);
  }

  @Override
  protected Long getUnnormalizedPartitionToServe(HttpServletRequest request) {
    CharSequence pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      return null;
    }
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    long userID;
    try {
      userID = Long.parseLong(pathComponents.next());
    } catch (NoSuchElementException ignored) {
      return null;
    } catch (NumberFormatException ignored) {
      return null;
    }
    return userID;
  }

}
