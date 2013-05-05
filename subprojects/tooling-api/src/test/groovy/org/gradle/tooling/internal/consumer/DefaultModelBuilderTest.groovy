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
import org.gradle.tooling.internal.consumer.async.AsyncConnection
import org.gradle.tooling.internal.protocol.ProjectVersion3
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.internal.Exceptions

class DefaultModelBuilderTest extends ConcurrentSpec {
    final AsyncConnection protocolConnection = Mock()
    final ConnectionParameters parameters = Mock()
    final DefaultModelBuilder<GradleProject> builder = new DefaultModelBuilder<GradleProject>(GradleProject, protocolConnection, parameters)

    def getModelDelegatesToProtocolConnectionToFetchModel() {
        ResultHandlerVersion1<ProjectVersion3> adaptedHandler
        ResultHandler<GradleProject> handler = Mock()
        GradleProject result = Mock()

        when:
        builder.get(handler)

        then:
        1 * protocolConnection.run(GradleProject, !null, !null) >> {args ->
            def params = args[1]
            assert params.standardOutput == null
            assert params.standardError == null
            assert params.progressListener != null
            assert params.tasks == null
            adaptedHandler = args[2]
        }

        when:
        adaptedHandler.onComplete(result)

        then:
        1 * handler.onComplete(result)
        0 * _._
    }

    def canConfigureTheOperation() {
        ResultHandler<GradleProject> handler = Mock()
        ResultHandlerVersion1<ProjectVersion3> adaptedHandler
        GradleProject result = Mock()

        when:
        builder.forTasks('a', 'b').get(handler)

        then:
        1 * protocolConnection.run(GradleProject, !null, !null) >> {args ->
            def params = args[1]
            assert params.standardOutput == null
            assert params.standardError == null
            assert params.progressListener != null
            assert params.tasks == ['a', 'b']
            adaptedHandler = args[2]
        }

        when:
        adaptedHandler.onComplete(result)

        then:
        1 * handler.onComplete(result)
        0 * _._
    }

    def getModelWrapsFailureToFetchModel() {
        ResultHandler<GradleProject> handler = Mock()
        ResultHandlerVersion1<ProjectVersion3> adaptedHandler
        RuntimeException failure = new RuntimeException()
        GradleConnectionException wrappedFailure

        when:
        builder.get(handler)

        then:
        1 * protocolConnection.run(!null, !null, !null) >> {args -> adaptedHandler = args[2]}

        when:
        adaptedHandler.onFailure(failure)

        then:
        1 * handler.onFailure(!null) >> {args -> wrappedFailure = args[0] }
        _ * protocolConnection.displayName >> '[connection]'
        wrappedFailure.message == 'Could not fetch model of type \'GradleProject\' using [connection].'
        wrappedFailure.cause.is(failure)
        0 * _._
    }

    def "provides compatibility hint on failure"() {
        ResultHandler<GradleProject> handler = Mock()
        ResultHandlerVersion1<ProjectVersion3> adaptedHandler
        RuntimeException failure = new UnsupportedOperationException()
        GradleConnectionException wrappedFailure

        when:
        builder.get(handler)

        then:
        1 * protocolConnection.run(!null, !null, !null) >> {args -> adaptedHandler = args[2]}

        when:
        adaptedHandler.onFailure(failure)

        then:
        1 * handler.onFailure(!null) >> {args -> wrappedFailure = args[0] }
        wrappedFailure.message.contains(Exceptions.INCOMPATIBLE_VERSION_HINT)
        wrappedFailure.cause.is(failure)
    }

    def getModelBlocksUntilResultReceivedFromProtocolConnection() {
        GradleProject result = Mock()

        given:
        protocolConnection.run(!null, !null, !null) >> { args ->
            def handler = args[2]
            start {
                thread.block()
                instant.handleResult
                handler.onComplete(result)
            }
        }

        when:
        def model
        operation.buildResult {
            model = builder.get()
        }

        then:
        model == result

        and:
        operation.buildResult.end > instant.handleResult
    }

    def getModelBlocksUntilFailureReceivedFromProtocolConnectionAndRethrowsFailure() {
        RuntimeException failure = new RuntimeException()

        given:
        protocolConnection.run(!null, !null, !null) >> { args ->
            def handler = args[2]
            start {
                thread.block()
                instant.handlingResult
                handler.onFailure(failure)
            }
        }

        when:
        operation.buildResult {
            builder.get()
        }

        then:
        GradleConnectionException e = thrown()
        e.cause.is(failure)

        and:
        operation.buildResult.end > instant.handlingResult
    }
}


