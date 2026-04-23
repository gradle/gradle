/*
 * Copyright 2019 the original author or authors.
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

abstract class AbstractFailure implements ResponseProducer, Failure {
    private final RuntimeException failure;

    public AbstractFailure(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public boolean isFailure() {
        return true;
    }

    @Override
    public RuntimeException getFailure() {
        return failure;
    }

    @Override
    public void writeTo(int requestId, HttpExchange exchange) {
        throw new IllegalStateException();
    }

    protected static String withLeadingSlash(String path) {
        if (path.startsWith("/")) {
            return path;
        } else {
            return "/" + path;
        }
    }

    protected static String contextSuffix(String context) {
        if (context.isEmpty()) {
            return context;
        }
        return ". " + context;
    }
}
