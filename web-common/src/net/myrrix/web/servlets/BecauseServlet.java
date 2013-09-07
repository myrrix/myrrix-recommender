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
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;

/**
 * <p>Responds to a GET request to {@code /because/[userID]/[itemID]?howMany=n}, and in turn calls
 * {@link MyrrixRecommender#recommendedBecause(long, long, int)}. If howMany is not specified, defaults to
  * {@link AbstractMyrrixServlet#DEFAULT_HOW_MANY}.</p>
 *
 * <p>Outputs item/score pairs in CSV or JSON format, like {@link RecommendServlet} does.</p>
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class BecauseServlet extends AbstractMyrrixServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

    CharSequence pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No path");      
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

    MyrrixRecommender recommender = getRecommender();
    try {
      Iterable<RecommendedItem> similar = recommender.recommendedBecause(userID, itemID, getHowMany(request));
      output(request, response, similar);
    } catch (NoSuchUserException nsue) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, nsue.toString());
    } catch (NoSuchItemException nsie) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, nsie.toString());
    } catch (NotReadyException nre) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, nre.toString());
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    } catch (IllegalArgumentException iae) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, iae.toString());
    } catch (UnsupportedOperationException uoe) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, uoe.toString());
    }
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
