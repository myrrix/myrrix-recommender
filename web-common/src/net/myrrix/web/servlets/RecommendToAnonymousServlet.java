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
import java.util.List;
import java.util.NoSuchElementException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Lists;
import org.apache.commons.math3.util.Pair;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.common.LangUtils;
import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;
import net.myrrix.online.RescorerProvider;

/**
 * <p>Responds to a GET request to
 * {@code /recommendToAnonymous/[itemID1(=value1)](/[itemID2(=value2)]/...)?howMany=n[&rescorerParams=...]},
 * and in turn calls {@link MyrrixRecommender#recommendToAnonymous(long[], float[], int, IDRescorer)}
 * with the supplied values. That is, 1 or more item IDs are supplied, which may each optionally correspond to
 * a value or else default to 1. If howMany is not specified, defaults to
 * {@link AbstractMyrrixServlet#DEFAULT_HOW_MANY}.</p>
 *
 * <p>Unknown item IDs are ignored, unless all are unknown, in which case a
 * {@link HttpServletResponse#SC_BAD_REQUEST} status is returned.</p>
 *
 * <p>Outputs item/score pairs in CSV or JSON format, like {@link RecommendServlet} does.</p>
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class RecommendToAnonymousServlet extends AbstractMyrrixServlet {
  
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

    CharSequence pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No path");      
    }
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    Pair<long[],float[]> itemIDsAndValue;
    try {
      itemIDsAndValue = parseItemValuePairs(pathComponents);
    } catch (NoSuchElementException nsee) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nsee.toString());
      return;
    } catch (NumberFormatException nfe) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nfe.toString());
      return;
    }

    if (itemIDsAndValue.getFirst().length == 0) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No items");
      return;
    }

    long[] itemIDs = itemIDsAndValue.getFirst();
    float[] values = itemIDsAndValue.getSecond();
    
    MyrrixRecommender recommender = getRecommender();
    RescorerProvider rescorerProvider = getRescorerProvider();
    try {
      IDRescorer rescorer = rescorerProvider == null ? null :
          rescorerProvider.getRecommendToAnonymousRescorer(itemIDs, recommender, getRescorerParams(request));
      Iterable<RecommendedItem> recommended =
          recommender.recommendToAnonymous(itemIDs, values, getHowMany(request), rescorer);
      output(request, response, recommended);
    } catch (NotReadyException nre) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, nre.toString());
    } catch (NoSuchItemException nsie) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, nsie.toString());
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    } catch (IllegalArgumentException iae) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, iae.toString());
    }
  }
  
  static Pair<long[],float[]> parseItemValuePairs(Iterator<String> pathComponents) {
    List<Pair<Long,Float>> itemValuePairs = Lists.newArrayListWithCapacity(1);
    while (pathComponents.hasNext()) {
      itemValuePairs.add(parseItemValue(pathComponents.next()));
    }
    
    int size = itemValuePairs.size();
    long[] itemIDs = new long[size];
    float[] values = new float[size];
    for (int i = 0; i < size; i++) {
      Pair<Long,Float> itemValuePair = itemValuePairs.get(i);
      itemIDs[i] = itemValuePair.getFirst();
      Float value = itemValuePair.getSecond();
      values[i] = value == null ? 1.0f : value;
    }
    
    return new Pair<long[],float[]>(itemIDs, values);
  }

  private static Pair<Long,Float> parseItemValue(String s) {
    int equals = s.indexOf('=');
    if (equals < 0) {
      return new Pair<Long,Float>(Long.parseLong(s), null);
    }
    return new Pair<Long,Float>(Long.parseLong(s.substring(0, equals)), 
                                LangUtils.parseFloat(s.substring(equals + 1)));
  }

}
