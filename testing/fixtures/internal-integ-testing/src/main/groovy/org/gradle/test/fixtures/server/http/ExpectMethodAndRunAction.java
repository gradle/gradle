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

package org.gradle.test.fixtures.server.http;

import com.sun.net.httpserver.HttpExchange;
import org.gradle.api.Action;

class ExpectMethodAndRunAction implements ResourceHandler, BlockingHttpServer.ExpectedRequest, ResourceExpectation {
    private final String method;
    private final String path;
    private final Action<? super HttpExchange> action;

    ExpectMethodAndRunAction(String method, String path, Action<? super HttpExchange> action) {
        this.method = method;
        this.path = path;
        this.action = action;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public ResourceHandler create(WaitPrecondition precondition) {
        return this;
    }

    @Override
    public void writeTo(int requestId, HttpExchange exchange) {
        action.execute(exchange);
    }
}
