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

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.async.AsyncConnection
import org.gradle.tooling.internal.consumer.converters.ConsumerPropertyHandler
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.protocol.ProjectVersion3
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.internal.Exceptions
import org.gradle.util.ConcurrentSpecification

class DefaultModelBuilderTest extends ConcurrentSpecification {
    final AsyncConnection protocolConnection = Mock()
    final ProtocolToModelAdapter adapter = Mock()
    final ConnectionParameters parameters = Mock()
    final DefaultModelBuilder<GradleProject> builder = new DefaultModelBuilder<GradleProject>(GradleProject, protocolConnection, adapter, parameters)

    def getModelDelegatesToProtocolConnectionToFetchModel() {
        ResultHandlerVersion1<ProjectVersion3> adaptedHandler
        ResultHandler<GradleProject> handler = Mock()
        ProjectVersion3 result = Mock()
        GradleProject adaptedResult = Mock()

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
        1 * protocolConnection.versionDetails
        1 * adapter.adapt(GradleProject.class, result, _ as ConsumerPropertyHandler) >> adaptedResult
        1 * handler.onComplete(adaptedResult)
        0 * _._
    }

    def canConfigureTheOperation() {
        ResultHandler<GradleProject> handler = Mock()
        ResultHandlerVersion1<ProjectVersion3> adaptedHandler
        ProjectVersion3 result = Mock()
        GradleProject adaptedResult = Mock()

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
        1 * protocolConnection.versionDetails
        1 * adapter.adapt(GradleProject.class, result, _ as ConsumerPropertyHandler) >> adaptedResult
        1 * handler.onComplete(adaptedResult)
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
        def supplyResult = waitsForAsyncCallback()
        ProjectVersion3 result = Mock()
        GradleProject adaptedResult = Mock()
        _ * adapter.adapt(GradleProject.class, result, _) >> adaptedResult

        when:
        def model
        supplyResult.start {
            model = builder.get()
        }

        then:
        model == adaptedResult
        1 * protocolConnection.run(!null, !null, !null) >> { args ->
            def handler = args[2]
            supplyResult.callbackLater {
                handler.onComplete(result)
            }
        }
    }

    def getModelBlocksUntilFailureReceivedFromProtocolConnectionAndRethrowsFailure() {
        def supplyResult = waitsForAsyncCallback()
        RuntimeException failure = new RuntimeException()

        when:
        def model
        supplyResult.start {
            model = builder.get()
        }

        then:
        GradleConnectionException e = thrown()
        e.cause.is(failure)
        1 * protocolConnection.run(!null, !null, !null) >> { args ->
            def handler = args[2]
            supplyResult.callbackLater {
                handler.onFailure(failure)
            }
        }
    }
}


