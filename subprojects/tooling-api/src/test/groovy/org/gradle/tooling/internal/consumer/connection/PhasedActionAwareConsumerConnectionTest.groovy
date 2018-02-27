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
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.AfterBuildResult
import org.gradle.tooling.internal.protocol.AfterConfigurationResult
import org.gradle.tooling.internal.protocol.AfterLoadingResult
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
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import spock.lang.Specification

class PhasedActionAwareConsumerConnectionTest extends Specification {

    final target = Mock(TestConnection) {
        getMetaData() >> Stub(ConnectionMetaDataVersion1) {
            getVersion() >> "4.7"
        }
    }
    final adapter = Mock(ProtocolToModelAdapter)
    final modelMapping = Mock(ModelMapping)
    final connection = new PhasedActionAwareConsumerConnection(target, modelMapping, adapter)

    def "describes capabilities of provider"() {
        given:
        def details = connection.versionDetails

        expect:
        details.supportsCancellation()
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
        details.maySupportModel(ProjectOutcomes)
        details.maySupportModel(Void)
        details.maySupportModel(GradleBuild)
        details.maySupportModel(BuildInvocations)
        details.maySupportModel(CustomModel)
    }

    def "delegates to connection to run phased action"() {
        def afterLoadingAction = Mock(BuildAction)
        def afterLoadingHandler = Mock(ResultHandler)
        def afterConfigurationAction = Mock(BuildAction)
        def afterConfigurationHandler = Mock(ResultHandler)
        def afterBuildAction = Mock(BuildAction)
        def afterBuildHandler = Mock(ResultHandler)
        def phasedAction = Stub(PhasedBuildAction) {
            getAfterLoadingAction() >> Stub(PhasedBuildAction.BuildActionWrapper) {
                getAction() >> afterLoadingAction
                getHandler() >> afterLoadingHandler
            }
            getAfterConfigurationAction() >> Stub(PhasedBuildAction.BuildActionWrapper) {
                getAction() >> afterConfigurationAction
                getHandler() >> afterConfigurationHandler
            }
            getAfterBuildAction() >> Stub(PhasedBuildAction.BuildActionWrapper) {
                getAction() >> afterBuildAction
                getHandler() >> afterBuildHandler
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

            def actionResult1 = protocolAction.getAfterLoadingAction().execute(buildController)
            def actionResult2 = protocolAction.getAfterConfigurationAction().execute(buildController)
            def actionResult3 = protocolAction.getAfterBuildAction().execute(buildController)
            listener.onResult(new AfterLoadingResult<Object>() {
                @Override
                Object getResult() {
                    return actionResult1
                }

                @Override
                Throwable getFailure() {
                    return null
                }
            })
            listener.onResult(new AfterConfigurationResult<Object>() {
                @Override
                Object getResult() {
                    return actionResult2
                }

                @Override
                Throwable getFailure() {
                    return null
                }
            })
            listener.onResult(new AfterBuildResult<Object>() {
                @Override
                Object getResult() {
                    return actionResult3
                }

                @Override
                Throwable getFailure() {
                    return null
                }
            })
            return Stub(BuildResult) {
                getModel() >> null
            }
        }
        1 * afterLoadingAction.execute(_) >> 'result1'
        1 * afterConfigurationAction.execute(_) >> 'result2'
        1 * afterBuildAction.execute(_) >> 'result3'
        1 * afterLoadingHandler.onComplete('result1')
        1 * afterConfigurationHandler.onComplete('result2')
        1 * afterBuildHandler.onComplete('result3')
        0 * afterLoadingHandler.onFailure(_)
        0 * afterConfigurationHandler.onFailure(_)
        0 * afterBuildHandler.onFailure(_)
    }

    def "adapts phased action build action final failure"() {
        def phasedAction = Mock(PhasedBuildAction)
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

    def "adapts phased action partial failure"() {
        def afterConfigurationHandler = Mock(ResultHandler)
        def phasedAction = Stub(PhasedBuildAction) {
            getAfterLoadingAction() >> null
            getAfterConfigurationAction() >> Stub(PhasedBuildAction.BuildActionWrapper) {
                getAction() >> Mock(BuildAction)
                getHandler() >> afterConfigurationHandler
            }
            getAfterBuildAction() >> null
        }
        def parameters = Stub(ConsumerOperationParameters)
        def failure = new RuntimeException()

        when:
        connection.run(phasedAction, parameters)

        then:
        1 * target.run(_, _, _, parameters) >> { def protocolAction, PhasedActionResultListener listener, def cancel, def params ->
            listener.onResult(new AfterConfigurationResult<Object>() {
                @Override
                Object getResult() {
                    return null
                }

                @Override
                Throwable getFailure() {
                    return new InternalBuildActionFailureException(failure)
                }
            })
        }
        1 * afterConfigurationHandler.onFailure(_) >> { GradleConnectionException exception ->
            assert exception instanceof BuildActionFailureException
            assert exception.message == 'The supplied build action failed with an exception.'
            assert exception.cause == failure
        }
    }

    interface TestConnection extends ConnectionVersion4, ConfigurableConnection, InternalParameterAcceptingConnection, InternalTestExecutionConnection, StoppableConnection,
        InternalCancellableConnection, InternalPhasedActionConnection {
    }

    interface CustomModel {
    }
}
