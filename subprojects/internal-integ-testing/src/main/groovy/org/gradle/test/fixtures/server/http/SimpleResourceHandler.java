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

package org.gradle.test.fixtures.server.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

class SimpleResourceHandler implements ResourceHandler, BlockingHttpServer.Resource {
    private final String path;

    public SimpleResourceHandler(String path) {
        this.path = removeLeadingSlash(path);
    }

    static String removeLeadingSlash(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public void writeTo(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseHeaders().add("RESPONSE", "done");
    }
}
