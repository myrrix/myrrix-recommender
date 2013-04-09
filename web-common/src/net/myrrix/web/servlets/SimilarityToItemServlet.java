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
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.TasteException;

import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;

/**
 * <p>Responds to a GET request to {@code /similarityToItem/[toItemID]/itemID1(/[itemID2]/...)},
 * and in turn calls {@link MyrrixRecommender#similarityToItem(long, long...)} with the supplied values.</p>
 *
 * <p>Unknown item IDs are ignored, unless all are unknown or {@code toItemID} is unknown, in which case a
 * {@link HttpServletResponse#SC_BAD_REQUEST} status is returned.</p>
 *
 * <p>The output are similarities, in the same order as the item IDs, one per line.</p>
 *
 * @author Sean Owen
 */
public final class SimilarityToItemServlet extends AbstractMyrrixServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

    String pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No path");      
    }
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    long toItemID;
    List<Long> itemIDsList = Lists.newArrayList();
    try {
      toItemID = Long.parseLong(pathComponents.next());
      while (pathComponents.hasNext()) {
        itemIDsList.add(Long.parseLong(pathComponents.next()));
      }
    } catch (NoSuchElementException nsee) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nsee.toString());
      return;
    } catch (NumberFormatException nfe) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nfe.toString());
      return;
    }
    if (itemIDsList.isEmpty()) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No items");
      return;
    }
    long[] itemIDs = new long[itemIDsList.size()];
    for (int i = 0; i < itemIDs.length; i++) {
      itemIDs[i] = itemIDsList.get(i);
    }

    MyrrixRecommender recommender = getRecommender();
    try {
      float[] similarities = recommender.similarityToItem(toItemID, itemIDs);
      Writer out = response.getWriter();
      for (float similarity : similarities) {
        out.write(Float.toString(similarity));
        out.write('\n');        
      }
    } catch (NoSuchItemException nsie) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, nsie.toString());
    } catch (NotReadyException nre) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, nre.toString());
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    }
  }

}
