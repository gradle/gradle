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
package org.gradle.tooling.internal.consumer

import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.internal.Exceptions

class DefaultModelBuilderTest extends ConcurrentSpec {
    final AsyncConsumerActionExecutor asyncConnection = Mock()
    final ConsumerConnection connection = Mock()
    final ConnectionParameters parameters = Stub() {
        getProjectDir() >> new File('foo')
    }
    final DefaultModelBuilder<GradleProject> builder = new DefaultModelBuilder<GradleProject>(GradleProject, asyncConnection, parameters)

    def "requests model from consumer connection"() {
        ResultHandlerVersion1<GradleProject> adaptedHandler
        ResultHandler<GradleProject> handler = Mock()
        GradleProject result = Mock()

        when:
        builder.get(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> {args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
        }
        1 * connection.run(GradleProject, _) >> {args ->
            ConsumerOperationParameters params = args[1]
            assert params.standardOutput == null
            assert params.standardError == null
            assert params.standardInput == null
            assert params.javaHome == null
            assert params.jvmArguments == null
            assert params.arguments == null
            assert params.progressListener != null
            assert params.cancellationToken != null
            assert params.tasks == null
            return result
        }

        when:
        adaptedHandler.onComplete(result)

        then:
        1 * handler.onComplete(result)
        0 * _._
    }

    def "can configure the operation"() {
        ResultHandler<GradleProject> handler = Mock()
        ResultHandlerVersion1<GradleProject> adaptedHandler
        GradleProject result = Mock()

        when:
        builder.forTasks('a', 'b').get(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> {args ->
            ConsumerAction<GradleProject> action = args[0]
            action.run(connection)
            adaptedHandler = args[1]
        }
        1 * connection.run(GradleProject, _) >> {args ->
            ConsumerOperationParameters params = args[1]
            assert params.standardOutput == null
            assert params.standardError == null
            assert params.progressListener != null
            assert params.tasks == ['a', 'b']
            return result
        }

        when:
        adaptedHandler.onComplete(result)

        then:
        1 * handler.onComplete(result)
        0 * _._
    }

    def "wraps failure to fetch model"() {
        ResultHandler<GradleProject> handler = Mock()
        ResultHandlerVersion1<GradleProject> adaptedHandler
        RuntimeException failure = new RuntimeException()
        GradleConnectionException wrappedFailure

        when:
        builder.get(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> {args ->
            adaptedHandler = args[1]
        }

        when:
        adaptedHandler.onFailure(failure)

        then:
        1 * handler.onFailure(!null) >> {args -> wrappedFailure = args[0] }
        _ * asyncConnection.displayName >> '[connection]'
        wrappedFailure.message == 'Could not fetch model of type \'GradleProject\' using [connection].'
        wrappedFailure.cause.is(failure)
        0 * _._
    }

    def "provides compatibility hint on failure"() {
        ResultHandler<GradleProject> handler = Mock()
        ResultHandlerVersion1<GradleProject> adaptedHandler
        RuntimeException failure = new UnsupportedOperationException()
        GradleConnectionException wrappedFailure

        when:
        builder.get(handler)

        then:
        1 * asyncConnection.run(!null, !null) >> {args ->
            adaptedHandler = args[1]
        }

        when:
        adaptedHandler.onFailure(failure)

        then:
        1 * handler.onFailure(!null) >> {args -> wrappedFailure = args[0] }
        wrappedFailure.message.contains(Exceptions.INCOMPATIBLE_VERSION_HINT)
        wrappedFailure.cause.is(failure)
    }

    def "fetching model does not block"() {
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
            builder.get(handler)
            instant.dispatched
            thread.blockUntil.resultReceived
        }

        then:
        instant.dispatched < instant.resultAvailable
        instant.resultAvailable < instant.resultReceived
    }

    def "get() blocks until model is available"() {
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
            model = builder.get()
        }

        then:
        model == result

        and:
        operation.fetchResult.end > instant.resultAvailable
    }

    def "get() blocks until request fails"() {
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
            builder.get()
        }

        then:
        GradleConnectionException e = thrown()
        e.cause.is(failure)

        and:
        operation.fetchResult.end > instant.failureAvailable
    }
}


