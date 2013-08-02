/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.consumer.async

import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.tooling.internal.consumer.connection.ConnectionAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1

class DefaultAsyncConnectionTest extends ConcurrentSpec {
    def consumerConnection = Mock(ConsumerConnection) {
        getDisplayName() >> "[connection]"
    }
    def action = Mock(ConnectionAction)
    def handler = Mock(ResultHandlerVersion1)
    def connection = new DefaultAsyncConnection(consumerConnection, executorFactory)

    def cleanup() {
        connection.stop()
    }

    def "runs action asynchronously"() {
        when:
        async {
            connection.run(action, handler)
            instant.dispatched
        }

        then:
        1 * action.run(consumerConnection) >> {
            thread.blockUntil.dispatched
            instant.actionStarted
            return "result"
        }
        1 * handler.onComplete("result") >> {
            instant.resultReceived
        }

        and:
        instant.actionStarted < instant.resultReceived
    }

    def "notifies handler on failure"() {
        def failure = new RuntimeException()

        when:
        async {
            connection.run(action, handler)
        }

        then:
        1 * action.run(consumerConnection) >> {
            throw failure
        }
        1 * handler.onFailure(failure)
    }

    def "cannot use connection after it has stopped"() {
        when:
        connection.stop()
        connection.run(action, handler)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use [connection] as it has been stopped.'
    }
}
