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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.ConfigurableConnection
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2
import org.gradle.tooling.internal.protocol.InternalCancellableConnection
import org.gradle.tooling.internal.protocol.InternalParameterAcceptingConnection
import org.gradle.tooling.internal.protocol.InternalPhasedAction
import org.gradle.tooling.internal.protocol.InternalPhasedActionConnection
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.protocol.PhasedActionResultListener
import org.gradle.tooling.internal.protocol.StoppableConnection
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import spock.lang.Specification

class PhasedActionAwareConsumerConnectionTest extends Specification {

    final target = Mock(TestConnection) {
        getMetaData() >> Stub(ConnectionMetaDataVersion1) {
            getVersion() >> "4.8"
        }
    }
    final adapter = Stub(ProtocolToModelAdapter)
    final modelMapping = Stub(ModelMapping)
    final connection = new PhasedActionAwareConsumerConnection(target, modelMapping, adapter)

    def "describes capabilities of provider"() {
        given:
        def details = connection.versionDetails

        expect:
        details.supportsEnvironmentVariablesCustomization()
        details.supportsRunTasksBeforeExecutingAction()
        details.supportsParameterizedToolingModels()
        details.supportsRunPhasedActions()

        and:
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

    def "delegates to connection to run phased action"() {
        def projectsLoadedAction = Mock(BuildAction)
        def projectsLoadedHandler = Mock(IntermediateResultHandler)
        def buildFinishedAction = Mock(BuildAction)
        def buildFinishedHandler = Mock(IntermediateResultHandler)
        def phasedAction = Stub(PhasedBuildAction) {
            getProjectsLoadedAction() >> Stub(PhasedBuildAction.BuildActionWrapper) {
                getAction() >> projectsLoadedAction
                getHandler() >> projectsLoadedHandler
            }
            getBuildFinishedAction() >> Stub(PhasedBuildAction.BuildActionWrapper) {
                getAction() >> buildFinishedAction
                getHandler() >> buildFinishedHandler
            }
        }
        def parameters = Stub(ConsumerOperationParameters)
        def buildController = Mock(InternalBuildControllerVersion2)

        when:
        def result = connection.run(phasedAction, parameters)

        then:
        result == null

        and:
        1 * target.run(_, _, _, parameters) >> { InternalPhasedAction protocolAction, PhasedActionResultListener listener, def cancel, def params ->
            assert params == parameters

            def actionResult1 = protocolAction.getProjectsLoadedAction().execute(buildController)
            def actionResult3 = protocolAction.getBuildFinishedAction().execute(buildController)
            listener.onResult(new PhasedActionResult<Object>() {
                @Override
                Object getResult() {
                    return actionResult1
                }

                @Override
                PhasedActionResult.Phase getPhase() {
                    return PhasedActionResult.Phase.PROJECTS_LOADED
                }
            })
            listener.onResult(new PhasedActionResult<Object>() {
                @Override
                Object getResult() {
                    return actionResult3
                }

                @Override
                PhasedActionResult.Phase getPhase() {
                    return PhasedActionResult.Phase.BUILD_FINISHED
                }
            })
            return Stub(BuildResult) {
                getModel() >> null
            }
        }
        1 * projectsLoadedAction.execute(_) >> 'result1'
        1 * buildFinishedAction.execute(_) >> 'result2'
        1 * projectsLoadedHandler.onComplete('result1')
        1 * buildFinishedHandler.onComplete('result2')
    }

    def "adapts phased action build action failure"() {
        def phasedAction = Stub(PhasedBuildAction)
        def parameters = Stub(ConsumerOperationParameters)
        def failure = new RuntimeException()

        when:
        connection.run(phasedAction, parameters)

        then:
        BuildActionFailureException e = thrown()
        e.message == 'The supplied phased action failed with an exception.'
        e.cause == failure

        and:
        1 * target.run(_, _, _, parameters) >> { throw new InternalBuildActionFailureException(failure) }
    }

    interface TestConnection extends ConnectionVersion4, ConfigurableConnection, InternalParameterAcceptingConnection, InternalTestExecutionConnection, StoppableConnection,
        InternalCancellableConnection, InternalPhasedActionConnection {
    }

    interface CustomModel {
    }
}
