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

import org.apache.mahout.cf.taste.common.TasteException;

import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;
import net.myrrix.common.collection.FastIDSet;

/**
 * <p>Responds to a request for user or item cluster info, depending on the value of {@link #isUser()}. When
 * serving user clusters, it responds to GET request to {@code /user/clusters/count} or {@code /user/clusters/[n]},
 * where n is a cluster number. It responds to analogous requests to {@code /item/*} for items.</p>
 *
 * <p>When the final argument is "count", outputs a single number which is the count of clusters. When the
 * argument is a number, returns the </p>
 *
 * <p>Outputs IDs in CSV or JSON format. When outputting CSV, one ID is written per line. When outputting
 * JSON, the output is an array of IDs.</p>
 * 
 * <p>This is only supported in distributed mode, and when computing clusters in the Computation Layer.</p>
 *
 * @author Sean Owen
 * @since 1.0
 */
public abstract class AbstractClusterServlet extends AbstractMyrrixServlet {

  protected abstract boolean isUser();

  @Override
  protected final void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

    String pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No path");      
    }
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    String countOrN;
    try {
      countOrN = pathComponents.next();
    } catch (NoSuchElementException nsee) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nsee.toString());
      return;
    }
    if (pathComponents.hasNext()) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Path too long");
      return;
    }

    MyrrixRecommender recommender = getRecommender();
    try {

      if ("count".equals(countOrN)) {
        int count = isUser() ? recommender.getNumUserClusters() : recommender.getNumItemClusters();
        response.getWriter().println(count);
      } else {
        int n;
        try {
          n = Integer.parseInt(countOrN);
        } catch (NumberFormatException nfe) {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, nfe.toString());
          return;
        }
        try {
          FastIDSet ids = isUser() ? recommender.getUserCluster(n) : recommender.getItemCluster(n);
          outputIDs(request, response, ids);
        } catch (IndexOutOfBoundsException ioobe) {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, ioobe.toString());
        }
      }

    } catch (UnsupportedOperationException uoe) {
      response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, uoe.toString());
    } catch (NotReadyException nre) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, nre.toString());
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    }
  }


}
