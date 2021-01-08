/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.api.BuildCancelledException
import org.gradle.api.GradleException
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildController
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.adapter.ViewBuilder
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.*
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import spock.lang.Specification

class CancellableConsumerConnectionTest extends Specification {
    final target = Mock(TestModelBuilder) {
        getMetaData() >> Stub(ConnectionMetaDataVersion1) {
            getVersion() >> "2.1"
        }
    }
    final adapter = Mock(ProtocolToModelAdapter)
    final modelMapping = Mock(ModelMapping)
    final connection = new CancellableConsumerConnection(target, modelMapping, adapter)

    def "describes capabilities of provider"() {
        given:
        def details = connection.versionDetails

        expect:
        details.maySupportModel(HierarchicalEclipseProject)
        details.maySupportModel(EclipseProject)
        details.maySupportModel(IdeaProject)
        details.maySupportModel(BasicIdeaProject)
        details.maySupportModel(GradleProject)
        details.maySupportModel(BuildEnvironment)
        details.maySupportModel(Void)
        details.maySupportModel(GradleBuild)
        details.maySupportModel(BuildInvocations)
        details.maySupportModel(CustomModel)
    }

    def "delegates to connection to run build action"() {
        def action = Mock(BuildAction)
        def parameters = Stub(ConsumerOperationParameters)
        def buildController = Mock(InternalBuildController)

        when:
        def result = connection.run(action, parameters)

        then:
        result == 'result'

        and:
        1 * target.run(_, _, parameters) >> { InternalBuildAction protocolAction, InternalCancellationToken cancel, def params ->
            def actionResult = protocolAction.execute(buildController)
            return Stub(BuildResult) {
                getModel() >> actionResult
            }
        }
        1 * action.execute({ it instanceof BuildController }) >> 'result'
    }

    def "adapts build action failure"() {
        def action = Mock(BuildAction)
        def parameters = Stub(ConsumerOperationParameters)
        def failure = new RuntimeException()

        when:
        connection.run(action, parameters)

        then:
        BuildActionFailureException e = thrown()
        e.message == /The supplied build action failed with an exception./
        e.cause == failure

        and:
        1 * target.run(_, _, parameters) >> { throw new InternalBuildActionFailureException(failure) }
    }

    def "adapts implementation-specific cancellation failure when running build action"() {
        def action = Mock(BuildAction)
        def parameters = Stub(ConsumerOperationParameters)
        def failure = new GradleException("broken", new BuildCancelledException("cancelled."))

        when:
        connection.run(action, parameters)

        then:
        InternalBuildCancelledException e = thrown()
        e.cause == failure

        and:
        1 * target.run(_, _, parameters) >> { throw new InternalBuildActionFailureException(failure) }
    }

    def "runs build using connection's getModel() method"() {
        def parameters = Stub(ConsumerOperationParameters)
        def modelIdentifier = Stub(ModelIdentifier)
        def builder = Mock(ViewBuilder)

        when:
        def result = connection.run(Void.class, parameters)

        then:
        result == 'the result'

        and:
        1 * modelMapping.getModelIdentifierFromModelType(Void) >> modelIdentifier
        1 * target.getModel(modelIdentifier, _, parameters) >> { ModelIdentifier id, InternalCancellationToken cancel, def params ->
            return Stub(BuildResult) {
                getModel() >> 'result'
            }
        }
        1 * adapter.builder(Void) >> builder
        1 * builder.build('result') >> 'the result'
    }

    def "adapts implementation-specific cancellation failure when fetching model"() {
        def parameters = Stub(ConsumerOperationParameters)
        def failure = new GradleException("broken", new BuildCancelledException("cancelled."))

        when:
        connection.run(Void.class, parameters)

        then:
        InternalBuildCancelledException e = thrown()
        e.cause == failure

        and:
        1 * target.getModel(_, _, _) >> { throw new BuildExceptionVersion1(failure) }
    }

    interface TestModelBuilder extends ConnectionVersion4, ConfigurableConnection, InternalCancellableConnection {
    }

    interface CustomModel {
    }
}
