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

import static org.gradle.test.fixtures.server.http.AbstractFailure.contextSuffix;
import static org.gradle.test.fixtures.server.http.AbstractFailure.withLeadingSlash;

class ExpectationState {

    private enum FailureType {
        None, UnexpectedRequest, Timeout
    }

    private FailureType failure = FailureType.None;
    private String unexpectedMethod;
    private String unexpectedPath;

    public boolean isFailed() {
        return failure != FailureType.None;
    }

    /**
     * Signals that an unexpected request was received.
     *
     * @return A response to return to the client
     */
    public ResponseProducer unexpectedRequest(String requestMethod, String path, String context) {
        if (failure == FailureType.None) {
            failure = FailureType.UnexpectedRequest;
            unexpectedMethod = requestMethod;
            unexpectedPath = path;
        }
        return new UnexpectedRequestFailure(requestMethod, path, context);
    }

    /**
     * Signals that a timeout occurred waiting to handle the given request.
     *
     * @return A response to return to the client
     */
    public ResponseProducer timeout(String requestMethod, String path, String waitingFor, String context) {
        if (failure == FailureType.None) {
            failure = FailureType.Timeout;
        }
        return new RequestConditionFailure(requestMethod, path, String.format("Failed to handle %s %s due to a timeout %s%s", requestMethod, withLeadingSlash(path), waitingFor, contextSuffix(context)));
    }

    /**
     * Signals that a timeout occurred waiting for test condition to become true.
     */
    public void timeout(String waitingFor, String context) {
        if (failure == FailureType.None) {
            failure = FailureType.Timeout;
        }
    }

    /**
     * Creates a response to return to the client for an expected request received after a failure.
     */
    public ResponseProducer alreadyFailed(String requestMethod, String path, String context) {
        switch (failure) {
            case UnexpectedRequest:
                return new RequestConditionFailure(requestMethod, path, String.format("Failed to handle %s %s due to unexpected request %s %s%s", requestMethod, withLeadingSlash(path), unexpectedMethod, withLeadingSlash(unexpectedPath), contextSuffix(context)));
            case Timeout:
                return new RequestConditionFailure(requestMethod, path, String.format("Failed to handle %s %s due to a previous timeout%s", requestMethod, withLeadingSlash(path), contextSuffix(context)));
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Creates a response to return to the client for a request that was waiting when a failure occurred.
     */
    public ResponseProducer failureWhileWaiting(String requestMethod, String path, String waitingFor, String context) {
        switch (failure) {
            case UnexpectedRequest:
                return new RequestConditionFailure(requestMethod, path, String.format("Failed to handle %s %s due to unexpected request %s %s%s", requestMethod, withLeadingSlash(path), unexpectedMethod, withLeadingSlash(unexpectedPath), contextSuffix(context)));
            case Timeout:
                return new RequestConditionFailure(requestMethod, path, String.format("Failed to handle %s %s due to a timeout %s%s", requestMethod, withLeadingSlash(path), waitingFor, contextSuffix(context)));
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Creates an exception to throw to the test thread that is waiting for some condition
     */
    public RuntimeException getWaitFailure(String context) {
        switch (failure) {
            case UnexpectedRequest:
                return new RuntimeException(String.format("Unexpected request %s %s received%s", unexpectedMethod, withLeadingSlash(unexpectedPath), contextSuffix(context)));
            case Timeout:
                return new RuntimeException(String.format("Timeout waiting for expected requests%s", contextSuffix(context)));
            default:
                throw new IllegalStateException();
        }
    }
}
