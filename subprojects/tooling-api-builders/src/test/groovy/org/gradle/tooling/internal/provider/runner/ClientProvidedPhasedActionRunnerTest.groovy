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

package org.gradle.tooling.internal.provider.runner

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.execution.ProjectConfigurer
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.resources.ProjectLeaseRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2
import org.gradle.tooling.internal.protocol.InternalPhasedAction
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.provider.action.ClientProvidedPhasedAction
import org.gradle.tooling.internal.provider.PhasedBuildActionResult
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload
import spock.lang.Specification

class ClientProvidedPhasedActionRunnerTest extends Specification {

    def startParameter = Stub(StartParameterInternal)
    def serializedAction = Stub(SerializedPayload)
    def clientSubscriptions = Stub(BuildEventSubscriptions)
    def clientProvidedPhasedAction = new ClientProvidedPhasedAction(startParameter, serializedAction, true, clientSubscriptions)

    def projectsLoadedAction = Mock(InternalBuildActionVersion2)
    def buildFinishedAction = Mock(InternalBuildActionVersion2)
    def phasedAction = Mock(InternalPhasedAction) {
        getProjectsLoadedAction() >> projectsLoadedAction
        getBuildFinishedAction() >> buildFinishedAction
    }

    def buildEventConsumer = Mock(BuildEventConsumer)
    def payloadSerializer = Mock(PayloadSerializer) {
        deserialize(serializedAction) >> phasedAction
    }
    BuildListener listener
    def gradle = Stub(GradleInternal) {
        addBuildListener(_) >> { BuildListener listener ->
            this.listener = listener
        }
        getServices() >> Stub(ServiceRegistry) {
            get(PayloadSerializer) >> payloadSerializer
            get(BuildEventConsumer) >> buildEventConsumer
            get(BuildCancellationToken) >> Stub(BuildCancellationToken)
            get(BuildOperationExecutor) >> Stub(BuildOperationExecutor)
            get(ProjectConfigurer) >> Stub(ProjectConfigurer)
            get(ProjectLeaseRegistry) >> Stub(ProjectLeaseRegistry)
        }
    }
    def buildResult = Mock(BuildResult)
    def buildController = Mock(BuildTreeLifecycleController) {
        run() >> {
            listener.projectsLoaded(gradle)
            listener.projectsEvaluated(gradle)
            listener.buildFinished(buildResult)
        }
        setResult(_) >> {
            buildController.hasResult() >> true
        }
        hasResult() >> false
        getGradle() >> gradle
    }
    def runner = new ClientProvidedPhasedActionRunner()

    def "can run actions and results are sent to event consumer"() {
        def result1 = 'result1'
        def serializedResult1 = Mock(SerializedPayload)
        def result2 = 'result2'
        def serializedResult2 = Mock(SerializedPayload)

        given:
        payloadSerializer.serialize(result1) >> serializedResult1
        payloadSerializer.serialize(result2) >> serializedResult2

        when:
        def result = runner.run(clientProvidedPhasedAction, buildController)

        then:
        result.hasResult()
        result.clientResult == null
        result.buildFailure == null
        result.clientFailure == null

        and:
        1 * projectsLoadedAction.execute(_) >> result1
        1 * buildFinishedAction.execute(_) >> result2
        1 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.phase == PhasedActionResult.Phase.PROJECTS_LOADED &&
                it.result == serializedResult1
        })
        1 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.phase == PhasedActionResult.Phase.BUILD_FINISHED &&
                it.result == serializedResult2
        })
    }

    def "do not run later build action when fails"() {
        def failure = new RuntimeException()

        given:

        when:
        def result = runner.run(clientProvidedPhasedAction, buildController)

        then:
        result.clientResult == null
        result.buildFailure == failure
        result.clientFailure instanceof InternalBuildActionFailureException
        result.clientFailure.cause == failure

        and:
        1 * projectsLoadedAction.execute(_) >> {
            throw failure
        }
        0 * buildFinishedAction.execute(_)
        0 * buildEventConsumer.dispatch(_)
    }

    def "build failures are propagated"() {
        def failure = new RuntimeException()
        def buildController = Mock(BuildTreeLifecycleController)

        when:
        def result = runner.run(clientProvidedPhasedAction, buildController)

        then:
        result.buildFailure == failure
        result.clientFailure == failure
        _ * buildController.gradle >> gradle
        1 * buildController.run() >> { throw failure }
    }

    def "action not run if null"() {
        when:
        def result = runner.run(clientProvidedPhasedAction, buildController)

        then:
        result.clientResult == null
        result.buildFailure == null
        result.clientFailure == null

        and:
        1 * phasedAction.getProjectsLoadedAction() >> null
        1 * phasedAction.getBuildFinishedAction() >> null
        0 * buildEventConsumer.dispatch(_)
    }

    def "run tasks if defined"() {
        when:
        runner.run(new ClientProvidedPhasedAction(startParameter, serializedAction, true, clientSubscriptions), buildController)

        then:
        0 * buildController.configure()
        1 * buildController.run()
    }

    def "configure instead of run if no tasks are defined"() {
        when:
        runner.run(new ClientProvidedPhasedAction(startParameter, serializedAction, false, clientSubscriptions), buildController)

        then:
        1 * buildController.configure()
        0 * buildController.run()
    }
}
