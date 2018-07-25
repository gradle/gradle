/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.test.fixtures.server.http

import com.google.common.io.ByteStreams
import org.mortbay.util.IO
import org.mortbay.util.URIUtil

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.atomic.AtomicBoolean

class DropConnectionFilter implements Filter {
    private static final String HTTP_METHOD_PUT = "PUT"
    private FilterConfig filterConfig
    private final long dropConnectionForPutBytes
    private final HttpBuildCacheServer buildCache

    private final AtomicBoolean fired = new AtomicBoolean()

    DropConnectionFilter(long dropConnectionForPutBytes, HttpBuildCacheServer buildCache) {
        this.dropConnectionForPutBytes = dropConnectionForPutBytes
        this.buildCache = buildCache
    }

    @Override
    void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig
    }

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest && response instanceof HttpServletResponse)) {
            chain.doFilter(request, response)
            return
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request
        HttpServletResponse httpResponse = (HttpServletResponse) response

        if (httpRequest.getMethod().equals(HTTP_METHOD_PUT) && fired.compareAndSet(false, true)) {
            doPut(httpRequest, httpResponse)
        } else {
            chain.doFilter(httpRequest, httpResponse)
        }
    }

    private File locateFile(HttpServletRequest request) {
        return new File(filterConfig.getServletContext().getRealPath(URIUtil.addPaths(request.getServletPath(), request.getPathInfo())))
    }

    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        File file = locateFile(request)

        if (file.exists()) {
            boolean success = file.delete() // replace file if it exists
            if (!success) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
        }

        new FileOutputStream(file).withStream { out ->
            def stream = request.getInputStream()
            IO.copy(ByteStreams.limit(stream, dropConnectionForPutBytes), out)
            stream.close()

            // Drop the current connection (by dropping all), but accept subsequent connections.
            buildCache.stop()
            buildCache.dropConnectionForPutAfterBytes(-1)
            buildCache.start()
        }
    }

    @Override
    void destroy() {
    }
}
