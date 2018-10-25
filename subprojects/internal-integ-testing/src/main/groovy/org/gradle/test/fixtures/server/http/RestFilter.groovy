/*
 * Copyright 2018 the original author or authors.
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

import org.eclipse.jetty.util.IO
import org.eclipse.jetty.util.URIUtil

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.UnavailableException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class RestFilter implements Filter {
    private static final String HTTP_METHOD_PUT="PUT"
    private static final String HTTP_METHOD_GET="GET"
    private static final String HTTP_METHOD_DELETE="DELETE"

    private FilterConfig filterConfig
    private long maxPutSize

    /* ------------------------------------------------------------ */
    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    void init(FilterConfig filterConfig) throws UnavailableException {
        this.filterConfig = filterConfig
        String tmp = filterConfig.getInitParameter("maxPutSize")
        if (tmp != null) {
            maxPutSize = Long.parseLong(tmp)
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * @param request
     * @return
     */
    private File locateFile(HttpServletRequest request) {
        return new File(filterConfig.getServletContext().getRealPath(URIUtil.addPaths(request.getServletPath(), request.getPathInfo())))
    }

    /* ------------------------------------------------------------ */
    /* (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest&&response instanceof HttpServletResponse)) {
            chain.doFilter(request,response)
            return
        }

        HttpServletRequest httpRequest = (HttpServletRequest)request
        HttpServletResponse httpResponse = (HttpServletResponse)response

        if (httpRequest.getMethod().equals(HTTP_METHOD_GET)) {
            chain.doFilter(httpRequest,httpResponse)
        } else if (httpRequest.getMethod().equals(HTTP_METHOD_PUT)) {
            doPut(httpRequest,httpResponse)
        } else if (httpRequest.getMethod().equals(HTTP_METHOD_DELETE)) {
            doDelete(httpRequest,httpResponse)
        } else {
            chain.doFilter(httpRequest, httpResponse)
        }
    }
    /* ------------------------------------------------------------ */
    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
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
            if (maxPutSize > 0) {
                int length = request.getContentLength()
                if (length > maxPutSize) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)
                    return
                }
                IO.copy(request.getInputStream(), out, maxPutSize)
            } else {
                IO.copy(request.getInputStream(), out)
            }
        }

        response.setStatus(HttpServletResponse.SC_NO_CONTENT)
    }

    /* ------------------------------------------------------------ */
    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        File file = locateFile(request)

        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }

        boolean success = IO.delete(file) // actual delete operation

        if (success) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT)
        } else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        }
    }

    void destroy() {
        // nothing to destroy
    }
}