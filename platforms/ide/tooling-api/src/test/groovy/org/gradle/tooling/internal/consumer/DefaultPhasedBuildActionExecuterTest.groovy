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

package org.gradle.tooling.internal.consumer

import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1

class DefaultPhasedBuildActionExecuterTest extends ConcurrentSpec {
    // Tests based on DefaultBuildActionExecuterTest

    def asyncConnection = Mock(AsyncConsumerActionExecutor) {
        getDisplayName() >> 'testConnection'
    }
    def connection = Mock(ConsumerConnection)
    def parameters = Stub(ConnectionParameters)
    def phasedAction = Stub(PhasedBuildAction)
    def executer = new DefaultPhasedBuildActionExecuter(phasedAction, asyncConnection, parameters)

    def "delegates to connection to run phased action"() {
        def handler = Mock(ResultHandler)

        when:
        executer.run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> { ConsumerAction<Void> consumerAction, ResultHandlerVersion1 adaptedHandler ->
            consumerAction.run(connection)
            adaptedHandler.onComplete(null)
        }

        and:
        1 * connection.run(phasedAction, _)
        1 * handler.onComplete(null)
        0 * handler.onFailure(_)
    }

    def "notifies handler of failure"() {
        def handler = Mock(ResultHandler)
        def failure = new RuntimeException()

        when:
        executer.run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> { def consumerAction, ResultHandlerVersion1 adaptedHandler ->
            adaptedHandler.onFailure(failure)
        }

        and:
        1 * handler.onFailure({
            it instanceof GradleConnectionException &&
                it.message == 'Could not run phased build action using testConnection.' &&
                it.cause == failure
        })
        0 * handler.onComplete(_)
    }

    def "running phased action does not block"() {
        def handler = Mock(ResultHandler)

        given:
        asyncConnection.run(!null, !null) >> { def consumerAction, ResultHandlerVersion1 adaptedHandler ->
            start {
                thread.blockUntil.dispatched
                instant.resultAvailable
                adaptedHandler.onComplete(null)
            }
        }
        handler.onComplete(null) >> {
            instant.resultReceived
        }

        when:
        async {
            executer.run(handler)
            instant.dispatched
            thread.blockUntil.resultReceived
        }

        then:
        instant.dispatched < instant.resultAvailable
        instant.resultAvailable < instant.resultReceived
    }

    def "run() blocks until result is available"() {
        given:
        asyncConnection.run(!null, !null) >> { def consumerAction, ResultHandlerVersion1 adaptedHandler ->
            start {
                thread.block()
                instant.resultAvailable
                adaptedHandler.onComplete(null)
            }
        }

        when:
        def model
        operation.fetchResult {
            model = executer.run()
        }

        then:
        model == null

        and:
        operation.fetchResult.end > instant.resultAvailable
    }

    def "run() blocks until request fails"() {
        def failure = new RuntimeException()

        given:
        asyncConnection.run(!null, !null) >> { args ->
            def handler = args[1]
            start {
                thread.block()
                instant.failureAvailable
                handler.onFailure(failure)
            }
        }

        when:
        operation.fetchResult {
            executer.run()
        }

        then:
        GradleConnectionException e = thrown()
        e.message == 'Could not run phased build action using testConnection.'
        e.cause == failure

        and:
        operation.fetchResult.end > instant.failureAvailable
    }

    def "can define tasks to be run"() {
        def handler = Stub(ResultHandler)

        when:
        executer.forTasks('a', 'b').run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> { ConsumerAction<Void> consumerAction, ResultHandlerVersion1 adaptedHandler ->
            consumerAction.run(connection)
        }
        1 * connection.run(phasedAction, _) >> { args ->
            ConsumerOperationParameters params = args[1]
            assert params.tasks == ['a', 'b']
            return null
        }

        when:
        executer.forTasks(Collections.singleton("a")).run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> { ConsumerAction<Void> consumerAction, ResultHandlerVersion1 adaptedHandler ->
            consumerAction.run(connection)
        }
        1 * connection.run(phasedAction, _) >> { args ->
            ConsumerOperationParameters params = args[1]
            assert params.tasks == ['a']
            return null
        }
    }

    def "forTasks sets empty list correctly"() {
        when:
        executer.forTasks([])

        then:
        executer.operationParamsBuilder.tasks == []

        when:
        executer.forTasks(Collections.emptySet())

        then:
        executer.operationParamsBuilder.tasks == []
    }
}
