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


@CompileStatic
class LoggingHandler {
    private final Set<String> authenticationAttempts
    private final Set<Map<String, String>> allHeaders
    private final boolean logRequests

    LoggingHandler(Set<String> authenticationAttempts, Set<Map<String, String>> allHeaders, boolean logRequests) {
        this.logRequests = logRequests
        this.authenticationAttempts = authenticationAttempts
        this.allHeaders = allHeaders
    }

    void log(HttpRequest request) {
        // HTTP header names are case-insensitive, and com.sun.net.httpserver canonicalises them
        // (e.g. "TestHttpHeaderName" -> "Testhttpheadername"), so record them case-insensitively.
        Map<String, String> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        request.getHeaderNames().each { String name -> entries.put(name, request.getHeader(name)) }
        synchronized (allHeaders) {
            allHeaders.add(entries)
        }
        String authorization = getAuthorizationHeader(request)
        synchronized (authenticationAttempts) {
            authenticationAttempts << (authorization != null ? authorization.split(" ")[0] : "None")
        }
        if (logRequests) {
            String query = request.queryString
            println("handling http request: ${request.method} ${request.pathInfo}${query ? "?" + query : ''}")
        }
    }

    protected static String getAuthorizationHeader(HttpRequest request) {
        return request.getHeader("Authorization")
    }
}
