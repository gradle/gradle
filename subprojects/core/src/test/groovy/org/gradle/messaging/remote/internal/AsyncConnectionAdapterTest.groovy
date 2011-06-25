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
package org.gradle.messaging.remote.internal

import org.gradle.messaging.dispatch.Dispatch
import org.gradle.util.ConcurrentSpecification
import org.gradle.messaging.dispatch.DispatchFailureHandler
import spock.lang.Ignore

@Ignore
class AsyncConnectionAdapterTest extends ConcurrentSpecification {
    final Connection<String> connection = Mock()
    final Dispatch<String> incoming = Mock()
    final DispatchFailureHandler<String> failureHandler = Mock()
    AsyncConnectionAdapter asyncConnection

    def cleanup() {
        asyncConnection?.stop()
    }

    def "dispatches messages to the connection"() {
        def dispatched = startsAsyncAction()

        when:
        asyncConnection = new AsyncConnectionAdapter(connection, failureHandler, executorFactory)
        dispatched.started {
            asyncConnection.dispatch("message")
        }

        then:
        1 * connection.dispatch("message") >> { dispatched.done() }
    }

    def "starts receiving messages when dispatchTo() called"() {
        when:
        asyncConnection = new AsyncConnectionAdapter(connection, failureHandler, executorFactory)
        asyncConnection.dispatchTo(incoming)

        then:
        2 * connection.receive() >>> ["message", null]
        1 * incoming.dispatch("message")
        0 * incoming._
    }

    def "stops connection at end of receive stream"() {
        when:
        asyncConnection = new AsyncConnectionAdapter(connection, failureHandler, executorFactory)
        asyncConnection.dispatchTo(incoming)

        then:
        1 * connection.receive() >> null
        1 * connection.stop()
        0 * incoming._
    }

    def "stop blocks until all outgoing messages dispatched"() {
        def stopped = waitsForAsyncActionToComplete()

        when:
        stopped.start {
            asyncConnection.dispatch("message")
            asyncConnection.stop()
        }

        then:
        1 * connection.dispatch("message") >> { stopped.done() }
    }

    def "stop blocks until all incoming messages dispatched"() {
        def stopped = waitsForAsyncActionToComplete()

        when:
        stopped.start {
            asyncConnection.dispatchTo(incoming)
            asyncConnection.stop()
        }

        then:
        2 * connection.receive() >>> ["message", null]
        1 * incoming.dispatch("message") >> { stopped.done() }
    }

    def "stops connection on stop"() {
        when:
        asyncConnection.stop()

        then:
        1 * connection.stop()
    }
}
