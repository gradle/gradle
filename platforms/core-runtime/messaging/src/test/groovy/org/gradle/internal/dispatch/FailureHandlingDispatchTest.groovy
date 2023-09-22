/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.dispatch

import spock.lang.Specification

class FailureHandlingDispatchTest extends Specification {
    final Dispatch<String> target = Mock()
    final DispatchFailureHandler<String> handler = Mock()
    final FailureHandlingDispatch<String> dispatch = new FailureHandlingDispatch<String>(target, handler)

    def "dispatches message to target"() {
        when:
        dispatch.dispatch("message")

        then:
        1 * target.dispatch("message")
    }

    def "notifies handler on failure"() {
        def failure = new RuntimeException()

        when:
        dispatch.dispatch("message")

        then:
        1 * target.dispatch("message") >> { throw failure }
        1 * handler.dispatchFailed("message", failure)
    }

    def "propagates exception thrown by handler"() {
        def failure = new RuntimeException()
        def adaptedFailure = new RuntimeException()

        when:
        dispatch.dispatch("message")

        then:
        RuntimeException e = thrown()
        e == adaptedFailure
        1 * target.dispatch("message") >> { throw failure }
        1 * handler.dispatchFailed("message", failure) >> { throw adaptedFailure }
    }
}
