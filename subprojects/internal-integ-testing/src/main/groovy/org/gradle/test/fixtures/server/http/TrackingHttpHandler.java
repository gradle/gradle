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

interface TrackingHttpHandler {
    /**
     * Selects a resource handler to handle the given request. Returns null when this handler does not want to handle the request.
     *
     * This method is called under the state lock. The handler is _not_ called under the state lock. That is, the lock is held only while selecting how to handle the request.
     *
     * The method may block until the request is ready to be handled.
     */
    ResourceHandler handle(int id, HttpExchange exchange) throws Exception;

    /**
     * Returns a precondition that asserts that this handler is not expecting any further requests to be released by the test in order to complete.
     */
    WaitPrecondition getWaitPrecondition();

    /**
     * Asserts that this handler has been completed successfully.
     */
    void assertComplete() throws AssertionError;

}
