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

package org.gradle.test.fixtures.server.http

import groovy.transform.CompileStatic
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@CompileStatic
class LoggingHandler extends AbstractHandler {
    private final Set<String> authenticationAttempts
    private final Set<Map<String, String>> allHeaders
    private final boolean logRequests

    LoggingHandler(Set<String> authenticationAttempts, Set<Map<String, String>> allHeaders, boolean logRequests) {
        this.logRequests = logRequests
        this.authenticationAttempts = authenticationAttempts
        this.allHeaders = allHeaders
    }

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Map<String, String> entries = request.getHeaderNames().toList().collectEntries { headerName -> [headerName, request.getHeader(headerName as String)] }
        allHeaders.add(entries)
        String authorization = getAuthorizationHeader(request)
        if (authorization != null) {
            synchronized (authenticationAttempts) {
                authenticationAttempts << authorization.split(" ")[0]
            }
        } else {
            synchronized (authenticationAttempts) {
                authenticationAttempts << "None"
            }
        }
        if (logRequests) {
            println("handling http request: $request.method $target${request.queryString ? "?" + request.queryString : ''}")
        }
    }

    protected static String getAuthorizationHeader(HttpServletRequest request) {
        def header = request.getHeader(HttpHeader.AUTHORIZATION.asString())
        return header
    }
}
