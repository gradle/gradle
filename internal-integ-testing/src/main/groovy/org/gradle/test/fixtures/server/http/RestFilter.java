/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.test.fixtures.server.http;

import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.URIUtil;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * <p>
 * Support for HTTP PUT and DELETE methods.
 * </p>
 * <p><b>
 * THIS FILTER SHOULD ONLY BE USED WITH VERY GOOD SECURITY CONSTRAINTS!
 * </b></p>
 *
 * <p>
 * If the filter init parameter maxPutSize is set to a positive integer, then
 * only puts of known size less than maxPutSize will be accepted.
 * </p>
 *
 */
// Copied from org.mortbay.servlet.RestFilter
public class RestFilter implements Filter {
    private static final String HTTP_METHOD_PUT = "PUT";
    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_DELETE = "DELETE";

    private FilterConfig filterConfig;
    private long _maxPutSize;

    /* ------------------------------------------------------------ */
    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig filterConfig) throws UnavailableException {
        this.filterConfig = filterConfig;
        String tmp = filterConfig.getInitParameter("maxPutSize");
        if (tmp != null) {
            _maxPutSize = Long.parseLong(tmp);
        }
    }

    /* ------------------------------------------------------------ */

    /**
     *
     */
    private File locateFile(HttpServletRequest request) {
        return new File(filterConfig.getServletContext().getRealPath(URIUtil.addPaths(request.getServletPath(), request.getPathInfo())));
    }

    /* ------------------------------------------------------------ */
    /* (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest && response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (httpRequest.getMethod().equals(HTTP_METHOD_GET)) {
            chain.doFilter(httpRequest, httpResponse);
        } else if (httpRequest.getMethod().equals(HTTP_METHOD_PUT)) {
            doPut(httpRequest, httpResponse);
        } else if (httpRequest.getMethod().equals(HTTP_METHOD_DELETE)) {
            doDelete(httpRequest, httpResponse);
        } else {
            chain.doFilter(httpRequest, httpResponse);
        }
    }
    /* ------------------------------------------------------------ */

    /**
     *
     */
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        File file = locateFile(request);

        if (file.exists()) {
            boolean success = file.delete(); // replace file if it exists
            if (!success) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }

        FileOutputStream out = new FileOutputStream(file);
        try {
            if (_maxPutSize > 0) {
                int length = request.getContentLength();
                if (length > _maxPutSize) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                IO.copy(request.getInputStream(), out, _maxPutSize);
            } else {
                IO.copy(request.getInputStream(), out);
            }
        } finally {
            out.close();
        }

        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    /* ------------------------------------------------------------ */

    /**
     *
     */
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        File file = locateFile(request);

        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean success = IO.delete(file); // actual delete operation

        if (success) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public void destroy() {
        // nothing to destroy
    }
}
