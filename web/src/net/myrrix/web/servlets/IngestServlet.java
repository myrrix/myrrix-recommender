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
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.zip.GZIPInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Charsets;
import org.apache.mahout.cf.taste.common.TasteException;

import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;

/**
 * <p>Responds to a POST request to {@code /ingest} and in turn calls
 * {@link MyrrixRecommender#ingest(java.io.Reader)}}. The content of the request body is
 * fed to this method. Note that the content may be gzipped; if so, header "Content-Encoding"
 * must have value "gzip".</p>
 *
 * @author Sean Owen
 */
public final class IngestServlet extends AbstractMyrrixServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    MyrrixRecommender recommender = getRecommender();

    Reader reader;
    String contentEncoding = request.getHeader("Content-Encoding");
    if (contentEncoding == null) {
      reader = request.getReader();
    } else if ("gzip".equals(contentEncoding)) {
      String charEncodingName = request.getCharacterEncoding();
      Charset charEncoding = charEncodingName == null ? Charsets.UTF_8 : Charset.forName(charEncodingName);
      reader = new InputStreamReader(new GZIPInputStream(request.getInputStream()), charEncoding);
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported Content-Encoding");
      return;
    }

    try {
      recommender.ingest(reader);
    } catch (NotReadyException nre) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, nre.toString());
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    }
  }

}
