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

package org.gradle.tooling.internal.consumer

import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.tooling.BuildAction
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1
import org.gradle.tooling.model.GradleProject

class DefaultBuildActionExecuterTest extends ConcurrentSpec {
    def asyncConnection = Mock(AsyncConsumerActionExecutor)
    def connection = Mock(ConsumerConnection)
    def parameters = Mock(ConnectionParameters)
    def action = Mock(BuildAction)
    def executer = new DefaultBuildActionExecuter(action, asyncConnection, parameters)

    def "delegates to connection to run action"() {
        ResultHandlerVersion1<GradleProject> adaptedHandler
        ResultHandler<GradleProject> handler = Mock()
        GradleProject result = Mock()

        when:
        executer.run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> {args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
        }
        1 * connection.run(action, _) >> { args ->
            return result
        }

        when:
        adaptedHandler.onComplete(result)

        then:
        1 * handler.onComplete(result)
        0 * _._
    }

    def "notifies handler of failure"() {
        ResultHandlerVersion1<GradleProject> adaptedHandler
        ResultHandler<GradleProject> handler = Mock()
        RuntimeException failure = new RuntimeException()
        GradleConnectionException wrappedFailure

        when:
        executer.run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> {args ->
            adaptedHandler = args[1]
            adaptedHandler.onFailure(failure)
        }

        and:
        1 * handler.onFailure(!null) >> {args -> wrappedFailure = args[0] }
        _ * asyncConnection.displayName >> '[connection]'
        wrappedFailure.message == 'Could not run build action using [connection].'
        wrappedFailure.cause.is(failure)
        0 * _._
    }

    def "running action does not block"() {
        GradleProject result = Mock()
        ResultHandler<GradleProject> handler = Mock()

        given:
        asyncConnection.run(!null, !null) >> { args ->
            def wrappedHandler = args[1]
            start {
                thread.blockUntil.dispatched
                instant.resultAvailable
                wrappedHandler.onComplete(result)
            }
        }
        handler.onComplete(result) >> {
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
        GradleProject result = Mock()

        given:
        asyncConnection.run(!null, !null) >> { args ->
            def handler = args[1]
            start {
                thread.block()
                instant.resultAvailable
                handler.onComplete(result)
            }
        }

        when:
        def model
        operation.fetchResult {
            model = executer.run()
        }

        then:
        model == result

        and:
        operation.fetchResult.end > instant.resultAvailable
    }

    def "run() blocks until request fails"() {
        RuntimeException failure = new RuntimeException()

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
        e.cause.is(failure)

        and:
        operation.fetchResult.end > instant.failureAvailable
    }

    def "can define tasks to be run"() {
        ResultHandlerVersion1<GradleProject> adaptedHandler
        ResultHandler<GradleProject> handler = Mock()
        GradleProject result = Mock()

        when:
        executer.forTasks('a', 'b').run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> {args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
        }
        1 * connection.run(action, _) >> { args ->
            ConsumerOperationParameters params = args[1]
            assert params.tasks == ['a', 'b']
            return result
        }

        when:
        executer.forTasks(Collections.singleton("a")).run(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> {args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
        }
        1 * connection.run(action, _) >> { args ->
            ConsumerOperationParameters params = args[1]
            assert params.tasks == ['a']
            return result
        }
    }

}
