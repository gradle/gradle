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

class DropConnectionFilter implements Filter {
    private static final String HTTP_METHOD_PUT = "PUT"
    private static final String HTTP_METHOD_GET = "GET"
    private static final String HTTP_METHOD_DELETE = "DELETE"
    private FilterConfig filterConfig
    private final long dropConnectionForPutBytes
    private final HttpBuildCacheServer buildCache

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

        if (httpRequest.getMethod().equals(HTTP_METHOD_GET)) {
            chain.doFilter(httpRequest, httpResponse)
        } else if (httpRequest.getMethod().equals(HTTP_METHOD_PUT)) {
            doPut(httpRequest, httpResponse)
        } else if (httpRequest.getMethod().equals(HTTP_METHOD_DELETE)) {
            chain.doFilter(request, response)
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

        FileOutputStream out = new FileOutputStream(file)
        try {
            def stream = request.getInputStream()
            IO.copy(ByteStreams.limit(stream, dropConnectionForPutBytes), out)
            stream.close()
            buildCache.stop()
        }
        finally {
            out.close()
        }

        response.setStatus(HttpServletResponse.SC_NO_CONTENT)
    }

    @Override
    void destroy() {
    }
}
