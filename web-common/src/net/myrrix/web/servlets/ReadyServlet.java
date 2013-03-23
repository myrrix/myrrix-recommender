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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.mahout.cf.taste.common.TasteException;

/**
 * <p>Responds to a HEAD or GET request to {@code /ready} and in turn calls
 * {@link net.myrrix.common.MyrrixRecommender#isReady()}. Returns "OK" or "Unavailable" status depending on
 * whether the recommender is ready.</p>
 *
 * @author Sean Owen
 */
public final class ReadyServlet extends AbstractMyrrixServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    doHead(request, response);
  }

  @Override
  protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
    boolean isReady;
    try {
      isReady = getRecommender().isReady();
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
      return;
    }
    if (isReady) {
      response.setStatus(HttpServletResponse.SC_OK);
    } else {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
  }

}
