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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Charsets;
import net.myrrix.web.InitListener;
import net.myrrix.common.log.MemoryHandler;

/**
 * Prints recent log messages to the response.
 * 
 * @author Sean Owen
 * @since 1.0
 */
public final class LogServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    MemoryHandler logHandler = (MemoryHandler) getServletContext().getAttribute(InitListener.LOG_HANDLER);
    response.setContentType("text/plain");
    response.setCharacterEncoding(Charsets.UTF_8.name());
    Writer out = response.getWriter();
    Iterable<String> lines = logHandler.getLogLines();
    synchronized (lines) {
      for (String line : lines) {
        out.write(line); // Already has newline
      }
    }
  }

}
