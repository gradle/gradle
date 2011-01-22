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

import org.gradle.util.ConcurrentSpecification
import org.gradle.tooling.internal.protocol.ConnectionVersion1
import org.gradle.tooling.model.Build
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.protocol.BuildVersion1
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.UnsupportedVersionException

class DefaultBuildConnectionTest extends ConcurrentSpecification {
    final ConnectionVersion1 protocolConnection = Mock()
    final ProtocolToModelAdapter adapter = Mock()
    final DefaultBuildConnection connection = new DefaultBuildConnection(protocolConnection, adapter)

    def getModelDelegatesToProtocolConnectionToFetchModel() {
        ResultHandler<Build> handler = Mock()
        ResultHandlerVersion1<BuildVersion1> adaptedHandler
        BuildVersion1 result = Mock()
        Build adaptedResult = Mock()

        when:
        connection.getModel(Build.class, handler)

        then:
        1 * protocolConnection.getModel(BuildVersion1.class, !null) >> {args -> adaptedHandler = args[1]}

        when:
        adaptedHandler.onComplete(result)

        then:
        1 * adapter.adapt(Build.class, result) >> adaptedResult
        1 * handler.onComplete(adaptedResult)
        0 * _._
    }

    def getModelWrapsFailureToFetchModel() {
        ResultHandler<Build> handler = Mock()
        ResultHandlerVersion1<BuildVersion1> adaptedHandler
        RuntimeException failure = new RuntimeException()
        GradleConnectionException wrappedFailure

        when:
        connection.getModel(Build.class, handler)

        then:
        1 * protocolConnection.getModel(BuildVersion1.class, !null) >> {args -> adaptedHandler = args[1]}

        when:
        adaptedHandler.onFailure(failure)

        then:
        1 * handler.onFailure(!null) >> {args -> wrappedFailure = args[0] }
        _ * protocolConnection.displayName >> '[connection]'
        wrappedFailure.message == 'Could not fetch model of type \'Build\' from [connection].'
        wrappedFailure.cause.is(failure)
        0 * _._
    }

    def getModelFailsForUnknownModelType() {
        when:
        connection.getModel(TestBuild.class)

        then:
        UnsupportedVersionException e = thrown()
        e.message == 'Model of type \'TestBuild\' is not supported.'
    }

    def getModelBlocksUntilResultReceivedFromProtocolConnection() {
        def supplyResult = later()
        BuildVersion1 result = Mock()
        Build adaptedResult = Mock()
        _ * adapter.adapt(Build.class, result) >> adaptedResult

        when:
        def model
        def action = start {
            model = connection.getModel(Build.class)
        }

        then:
        action.waitsFor(supplyResult)
        1 * protocolConnection.getModel(BuildVersion1.class, !null) >> { args ->
            def handler = args[1]
            supplyResult.activate {
                handler.onComplete(result)
            }
        }

        when:
        finished()

        then:
        model == adaptedResult
    }

    def getModelBlocksUntilFailureReceivedFromProtocolConnectionAndRethrowsFailure() {
        def supplyResult = later()
        RuntimeException failure = new RuntimeException()

        when:
        def model
        def action = start {
            model = connection.getModel(Build.class)
        }

        then:
        action.waitsFor(supplyResult)
        1 * protocolConnection.getModel(BuildVersion1.class, !null) >> { args ->
            def handler = args[1]
            supplyResult.activate {
                handler.onFailure(failure)
            }
        }

        when:
        finished()

        then:
        GradleConnectionException e = thrown()
        e.cause.is(failure)
    }
}

interface TestBuild extends Build {
    
}
