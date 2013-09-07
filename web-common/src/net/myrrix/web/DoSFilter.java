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

package net.myrrix.web;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.myrrix.common.parallel.ExecutorUtils;

/**
 * A {@link Filter} that rejects requests from hosts that are sending too many requests. This is measured
 * as a number of requests in the last minute. If the limit is exceeded, requests from the host are rejected
 * with HTTP status {@link HttpServletResponse#SC_FORBIDDEN}. The host remians banned for about an hour.
 * 
 * @author Sean Owen
 * @since 1.0
 */
public final class DoSFilter implements Filter {

  public static final String MAX_ACCESS_PER_HOST_PER_MIN_KEY = "maxAccessPerHostPerMin";
  private static final int DEFAULT_MAX_ACCESS_PER_HOST_PER_MIN = 1000;

  private final ConcurrentMap<String,AtomicInteger> numRecentAccesses;
  private final Collection<String> bannedIPAddresses;
  private int maxAccessPerHostPerMin;
  private ScheduledExecutorService executor;

  public DoSFilter() {
    numRecentAccesses = Maps.newConcurrentMap();
    bannedIPAddresses = Sets.newSetFromMap(Maps.<String,Boolean>newConcurrentMap());
  }

  @Override
  public void init(FilterConfig filterConfig) {
    String maxAccessPerHostPerMinString = filterConfig.getInitParameter(MAX_ACCESS_PER_HOST_PER_MIN_KEY);
    maxAccessPerHostPerMin = 
        maxAccessPerHostPerMinString == null ? 
        DEFAULT_MAX_ACCESS_PER_HOST_PER_MIN : 
        Integer.parseInt(maxAccessPerHostPerMinString);
    Preconditions.checkArgument(maxAccessPerHostPerMin > 0, 
                                "Bad max accesses per host per min: {}", 
                                maxAccessPerHostPerMin);
    
    executor = Executors.newSingleThreadScheduledExecutor();
    executor.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        numRecentAccesses.clear();
      }
    }, 1, 1, TimeUnit.MINUTES);
    executor.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        bannedIPAddresses.clear();
      }
    }, 1, 1, TimeUnit.HOURS);
  }

  @Override
  public void doFilter(ServletRequest request,
                       ServletResponse response,
                       FilterChain chain) throws IOException, ServletException {
    if (isBanned(request)) {
      HttpServletResponse servletResponse = (HttpServletResponse) response;
      servletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
    } else {
      chain.doFilter(request, response);
    }
  }

  private boolean isBanned(ServletRequest request) {
    String remoteIPAddressString = request.getRemoteAddr();
    if (bannedIPAddresses.contains(remoteIPAddressString)) {
      return true;
    }
    AtomicInteger count = numRecentAccesses.putIfAbsent(remoteIPAddressString, new AtomicInteger(0));
    if (count.incrementAndGet() > maxAccessPerHostPerMin) {
      bannedIPAddresses.add(remoteIPAddressString);
      return true;
    }
    return false;
  }

  @Override
  public void destroy() {
    ExecutorUtils.shutdownNowAndAwait(executor);
    numRecentAccesses.clear();
    bannedIPAddresses.clear();
  }

}
