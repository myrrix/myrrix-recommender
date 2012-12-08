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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import com.google.common.base.Charsets;
import org.apache.mahout.cf.taste.common.TasteException;

import net.myrrix.common.MyrrixRecommender;

/**
 * <p>Responds to a POST request to {@code /ingest} and in turn calls
 * {@link MyrrixRecommender#ingest(java.io.Reader)}}. The content of the request body is
 * fed to this method. Note that the content may be gzipped; if so, header "Content-Encoding"
 * must have value "gzip".</p>
 *
 * <p>Alternatively, CSV data may be POSTed here as if part of a web browser file upload. In this case
 * the "Content-Type" should be "multipart/form-data", and the payload encoded accordingly. The uploaded
 * file may be gzipped or zipped.</p>
 *
 * @author Sean Owen
 */
public final class IngestServlet extends AbstractMyrrixServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    MyrrixRecommender recommender = getRecommender();

    boolean fromBrowserUpload = request.getContentType().startsWith("multipart/form-data");

    Reader reader;
    if (fromBrowserUpload) {

      Collection<Part> parts = request.getParts();
      if (parts == null || parts.isEmpty()) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No form data");
        return;
      }
      Part part = parts.iterator().next();
      String partContentType = part.getContentType();
      InputStream in = part.getInputStream();
      if ("application/zip".equals(partContentType)) {
        in = new ZipInputStream(in);
      } else if ("application/x-gzip".equals(partContentType)) {
        in = new GZIPInputStream(in);
      }
      reader = new InputStreamReader(in, Charsets.UTF_8);

    } else {

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

    }

    try {
      recommender.ingest(reader);
    } catch (IllegalArgumentException iae) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, iae.toString());
      return;
    } catch (NoSuchElementException nsee) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nsee.toString());
      return;
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
      return;
    }

    String referer = request.getHeader("Referer");
    if (fromBrowserUpload && referer != null) {
      response.sendRedirect(referer);
    }

  }

}
