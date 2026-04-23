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

import javax.annotation.Nullable;
import java.util.Collection;

interface TrackingHttpHandler {
    /**
     * Selects a response producer to handle the given request.
     *
     * This method is called under the state lock. The handler is _not_ called under the state lock. That is, the lock is held only while selecting how to handle the request.
     *
     * This method may block until the request is ready to be handled, but must do so using a condition created from the state lock.
     *
     * @return null when this handler is not expecting any further requests.
     */
    @Nullable
    ResponseProducer selectResponseProducer(int id, HttpExchange exchange);

    boolean expecting(HttpExchange exchange);

    /**
     * Returns a precondition that asserts that this handler is not expecting any further requests to be released by the test in order to complete.
     */
    WaitPrecondition getWaitPrecondition();

    /**
     * Releases any blocked requests, in preparation for shutdown.
     */
    void cancelBlockedRequests();

    /**
     * Asserts that this handler has been completed successfully.
     */
    void assertComplete(Collection<Throwable> failures);
}
