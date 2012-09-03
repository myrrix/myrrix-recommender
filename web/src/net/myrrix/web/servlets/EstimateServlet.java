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
import java.util.Iterator;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;

import net.myrrix.common.LangUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;

/**
 * <p>Responds to a GET request to {@code /estimate/[userID]/[itemID]} and in turn calls
 * {@link MyrrixRecommender#estimatePreference(long, long)}.</p>
 *
 * <p>Outputs the result of the method call as a value on one line.</p>
 *
 * <p>This servlet can also compute several estimates at once. Send a GET request to
 * {@code /estimate/[userID]/[itemID1](/[itemID2]/...)}. The output are estimates, in the same
 * order as the item ID, one per line.</p>
 *
 * @author Sean Owen
 */
public final class EstimateServlet extends AbstractMyrrixServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

    String pathInfo = request.getPathInfo();
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    long userID = Long.parseLong(pathComponents.next());

    List<Long> itemIDsList = Lists.newArrayList();
    while (pathComponents.hasNext()) {
      itemIDsList.add(Long.parseLong(pathComponents.next()));
    }
    long[] itemIDs = new long[itemIDsList.size()];
    for (int i = 0; i < itemIDs.length; i++) {
      itemIDs[i] = itemIDsList.get(i);
    }

    MyrrixRecommender recommender = getRecommender();
    try {
      float[] estimates = recommender.estimatePreferences(userID, itemIDs);
      PrintWriter out = response.getWriter();
      for (float estimate : estimates) {
        out.println(Float.toString(estimate));
      }
    } catch (NoSuchUserException nsue) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, nsue.toString());
    } catch (NoSuchItemException nsie) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, nsie.toString());
    } catch (NotReadyException nre) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, nre.toString());
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    }
  }

  @Override
  protected Integer getPartitionToServe(HttpServletRequest request, int numPartitions) {
    String pathInfo = request.getPathInfo();
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    long userID = Long.parseLong(pathComponents.next());
    return LangUtils.mod(userID, numPartitions);
  }

}
