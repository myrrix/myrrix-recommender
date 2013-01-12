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
import org.apache.mahout.cf.taste.recommender.IDRescorer;

import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;
import net.myrrix.online.RescorerProvider;

/**
 * <p>Responds to a GET request to {@code /mostPopularItems(?howMany=n)}
 * and in turn calls
 * {@link MyrrixRecommender#mostPopularItems(int)}. If {@code howMany} is not specified, defaults to
 * {@link AbstractMyrrixServlet#DEFAULT_HOW_MANY}.
 *
 * <p>Output is as in {@link RecommendServlet}.</p>
 *
 * @author Sean Owen
 */
public final class MostPopularItemsServlet extends AbstractMyrrixServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    MyrrixRecommender recommender = getRecommender();
    RescorerProvider rescorerProvider = getRescorerProvider();
    IDRescorer rescorer = rescorerProvider == null ? null :
        rescorerProvider.getMostPopularItemsRescorer(recommender, getRescorerParams(request));
    try {
      output(request, response, recommender.mostPopularItems(getHowMany(request), rescorer));
    } catch (NotReadyException nre) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, nre.toString());
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    } catch (IllegalArgumentException iae) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, iae.toString());
    }
  }

}
