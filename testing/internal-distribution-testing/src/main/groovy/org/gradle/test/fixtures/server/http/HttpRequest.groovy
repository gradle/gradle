/*
 * Copyright 2026 the original author or authors.
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

import com.sun.net.httpserver.HttpExchange

import java.nio.charset.StandardCharsets

class HttpRequest {
    private final HttpExchange exchange

    boolean handled
    String remoteUser

    HttpRequest(HttpExchange exchange) {
        this.exchange = exchange
    }

    String getMethod() {
        return exchange.requestMethod
    }

    String getPathInfo() {
        return exchange.requestURI.path
    }

    String getRequestURI() {
        return exchange.requestURI.path
    }

    String getQueryString() {
        return exchange.requestURI.rawQuery
    }

    String getHeader(String name) {
        return exchange.requestHeaders.getFirst(name)
    }

    Set<String> getHeaderNames() {
        return exchange.requestHeaders.keySet()
    }

    int getContentLength() {
        String value = getHeader("Content-Length")
        return value == null ? -1 : Integer.parseInt(value)
    }

    InputStream getInputStream() {
        return exchange.requestBody
    }

    BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(exchange.requestBody, StandardCharsets.UTF_8))
    }

    Map<String, String[]> getParameterMap() {
        Map<String, List<String>> collected = [:]
        String query = getQueryString()
        if (query) {
            for (String pair : query.split("&")) {
                int idx = pair.indexOf("=")
                String key = URLDecoder.decode(idx < 0 ? pair : pair.substring(0, idx), "UTF-8")
                String value = idx < 0 ? "" : URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                collected.computeIfAbsent(key, { [] }).add(value)
            }
        }
        return collected.collectEntries { key, values -> [(key): values as String[]] } as Map<String, String[]>
    }

    String getRemoteUser() {
        return remoteUser
    }

    String getRemoteAddr() {
        return exchange.remoteAddress.address.hostAddress
    }

    int getRemotePort() {
        return exchange.remoteAddress.port
    }
}
