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

/**
 * <p>Responds to a POST request to {@code /tag/item/[itemID]/[tag]} and in turn calls
 * {@link MyrrixRecommender#setItemTag(String, long, float)}. If the request body is empty,
 * the value is 1.0, otherwise the value in the request body's first line is used.</p>
 *
 * @author Sean Owen
 */
public final class TagItemServlet extends AbstractMyrrixServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

    String pathInfo = request.getPathInfo();
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();

    String itemTag;
    long itemID;
    try {
      itemTag = pathComponents.next();      
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

    float tagValue;
    try {
      tagValue = PreferenceServlet.readValue(request);
    } catch (IllegalArgumentException ignored) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad value");
      return;
    }

    MyrrixRecommender recommender = getRecommender();
    try {
      recommender.setItemTag(itemTag, itemID, tagValue);
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    }
  }

}
