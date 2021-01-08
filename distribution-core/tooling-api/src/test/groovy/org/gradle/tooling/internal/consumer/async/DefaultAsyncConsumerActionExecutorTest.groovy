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
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerActionExecutor
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1

class DefaultAsyncConsumerActionExecutorTest extends ConcurrentSpec {
    def actionExecuter = Mock(ConsumerActionExecutor) {
        getDisplayName() >> "[executer]"
    }
    def action = Mock(ConsumerAction)
    def handler = Mock(ResultHandlerVersion1)
    def connection = new DefaultAsyncConsumerActionExecutor(actionExecuter, executorFactory)

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
        1 * actionExecuter.run(action) >> {
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
        1 * actionExecuter.run(action) >> {
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
        e.message == 'Cannot use [executer] as it has been stopped.'
    }
}
