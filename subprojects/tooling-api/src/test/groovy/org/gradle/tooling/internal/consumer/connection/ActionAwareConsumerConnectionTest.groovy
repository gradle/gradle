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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildController
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.ConfigurableConnection
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.InternalBuildAction
import org.gradle.tooling.internal.protocol.InternalBuildActionExecutor
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildController
import org.gradle.tooling.internal.protocol.ModelBuilder
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import spock.lang.Specification

class ActionAwareConsumerConnectionTest extends Specification {
    final metaData = Stub(ConnectionMetaDataVersion1)
    final target = Mock(TestModelBuilder) {
        getMetaData() >> metaData
    }
    final adapter = Mock(ProtocolToModelAdapter)
    final modelMapping = Stub(ModelMapping)

    def "describes capabilities of 1.8 provider"() {
        given:
        metaData.version >> "1.8"
        def connection = new ActionAwareConsumerConnection(target, modelMapping, adapter)
        def details = connection.versionDetails

        expect:
        !details.supportsCancellation()

        and:
        details.maySupportModel(HierarchicalEclipseProject)
        details.maySupportModel(EclipseProject)
        details.maySupportModel(IdeaProject)
        details.maySupportModel(BasicIdeaProject)
        details.maySupportModel(GradleProject)
        details.maySupportModel(BuildEnvironment)
        details.maySupportModel(ProjectOutcomes)
        details.maySupportModel(Void)
        details.maySupportModel(CustomModel)
        details.maySupportModel(GradleBuild)

        and:
        !details.maySupportModel(BuildInvocations)
    }

    def "describes capabilities of a post 1.12 provider"() {
        given:
        metaData.version >> "1.12"
        def connection = new ActionAwareConsumerConnection(target, modelMapping, adapter)
        def details = connection.versionDetails

        expect:
        !details.supportsCancellation()

        and:
        details.maySupportModel(HierarchicalEclipseProject)
        details.maySupportModel(EclipseProject)
        details.maySupportModel(IdeaProject)
        details.maySupportModel(BasicIdeaProject)
        details.maySupportModel(GradleProject)
        details.maySupportModel(BuildEnvironment)
        details.maySupportModel(ProjectOutcomes)
        details.maySupportModel(Void)
        details.maySupportModel(CustomModel)
        details.maySupportModel(GradleBuild)
        details.maySupportModel(BuildInvocations)
    }

    def "delegates to connection to run build action"() {
        def action = Mock(BuildAction)
        def parameters = Stub(ConsumerOperationParameters)
        def buildController = Mock(InternalBuildController)

        when:
        metaData.version >> "1.8"
        def connection = new ActionAwareConsumerConnection(target, modelMapping, adapter)
        def result = connection.run(action, parameters)

        then:
        result == 'result'

        and:
        1 * target.run(_, parameters) >> { InternalBuildAction protocolAction, def params ->
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
        metaData.version >> "1.8"
        def connection = new ActionAwareConsumerConnection(target, modelMapping, adapter)
        connection.run(action, parameters)

        then:
        def e = thrown BuildActionFailureException
        e.message == /The supplied build action failed with an exception./
        e.cause == failure

        and:
        1 * target.run(_, parameters) >> { throw new InternalBuildActionFailureException(failure) }
    }

    interface TestModelBuilder extends ModelBuilder, ConnectionVersion4, ConfigurableConnection, InternalBuildActionExecutor {
    }

    static class CustomModel {
    }
}
