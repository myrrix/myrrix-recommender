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
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;

import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;

/**
 * <p>Responds to a GET request to {@code /user/allIDs} or {@code /item/allIDs}, and in turn calls
 * {@link MyrrixRecommender#getAllUserIDs()} or {@link MyrrixRecommender#getAllItemIDs()}, depending on
 * {@link #isUserIDs()}.</p>
 *
 * <p>Outputs item/score pairs in CSV or JSON format, like {@link RecommendServlet} does.</p>
 *
 * @author Sean Owen
 */
public abstract class AbstractAllIDsServlet extends AbstractMyrrixServlet {

  protected abstract boolean isUserIDs();

  @Override
  protected final void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    MyrrixRecommender recommender = getRecommender();
    try {
      FastIDSet ids = isUserIDs() ? recommender.getAllUserIDs() : recommender.getAllItemIDs();
      outputIDs(request, response, ids);
    } catch (NotReadyException nre) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, nre.toString());
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    }
  }

  protected final void outputIDs(HttpServletRequest request,
                                 ServletResponse response,
                                 FastIDSet ids) throws IOException {

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


}
