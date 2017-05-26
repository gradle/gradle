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

interface ResourceHandler {
    /**
     * Returns the method for this handler.
     */
    String getMethod();

    /**
     * Returns the path for this handler.
     */
    String getPath();

    /**
     * Called to handle a request. Is *not* called under lock.
     */
    void writeTo(int requestId, HttpExchange exchange) throws IOException;
}
