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

package org.gradle.tooling.internal.provider.runner

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.execution.ProjectConfigurer
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.resources.ProjectLeaseRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.tooling.internal.protocol.InternalBuildAction
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2
import org.gradle.tooling.internal.provider.ClientProvidedBuildAction
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload
import spock.lang.Specification

class ClientProvidedBuildActionRunnerTest extends Specification {

    def startParameter = Mock(StartParameterInternal)
    def action = Mock(SerializedPayload)
    def clientSubscriptions = Mock(BuildEventSubscriptions)
    def buildEventConsumer = Mock(BuildEventConsumer)
    def payloadSerializer = Mock(PayloadSerializer)
    def projectConfigurer = Mock(ProjectConfigurer)
    BuildListener listener
    def gradle = Stub(GradleInternal) {
        addBuildListener(_) >> { BuildListener listener ->
            this.listener = listener
        }
        getServices() >> Stub(ServiceRegistry) {
            get(PayloadSerializer) >> payloadSerializer
            get(ProjectConfigurer) >> projectConfigurer
            get(BuildEventConsumer) >> buildEventConsumer
            get(BuildCancellationToken) >> Stub(BuildCancellationToken)
            get(BuildOperationExecutor) >> Stub(BuildOperationExecutor)
            get(ProjectStateRegistry) >> Stub(ProjectStateRegistry)
            get(ProjectLeaseRegistry) >> Stub(ProjectLeaseRegistry)
        }
    }
    def buildController = Mock(BuildTreeLifecycleController) {
        configure() >> {
            listener.buildFinished(Stub(BuildResult) {
                getFailure() >> null
            })
        }
        getGradle() >> gradle
    }
    def clientProvidedBuildAction = new ClientProvidedBuildAction(startParameter, action, false /* isRunTasks */, clientSubscriptions)
    def runner = new ClientProvidedBuildActionRunner()

    def "can run action and returns result when completed"() {
        given:
        def model = new Object()
        def internalAction = Mock(InternalBuildAction)

        when:
        def result = runner.run(clientProvidedBuildAction, buildController)

        then:
        result.clientResult == model
        result.buildFailure == null
        result.clientFailure == null

        and:
        1 * internalAction.execute(_) >> model
        1 * payloadSerializer.deserialize(action) >> internalAction
    }

    def "can run action and reports failure"() {
        given:
        def failure = new RuntimeException()
        def internalAction = Mock(InternalBuildAction)

        when:
        def result = runner.run(clientProvidedBuildAction, buildController)

        then:
        result.clientResult == null
        result.buildFailure == failure
        result.clientFailure instanceof InternalBuildActionFailureException
        result.clientFailure.cause == failure

        and:
        1 * payloadSerializer.deserialize(action) >> internalAction
        1 * internalAction.execute(_) >> { throw failure }
    }

    def "can run action and propagate build exception"() {
        given:
        def failure = new RuntimeException()
        def buildController = Mock(BuildTreeLifecycleController)
        def internalAction = Mock(InternalBuildAction)

        when:
        def result = runner.run(clientProvidedBuildAction, buildController)

        then:
        result.clientResult == null
        result.buildFailure == failure
        result.clientFailure == failure

        and:
        1 * payloadSerializer.deserialize(action) >> internalAction
        _ * buildController.gradle >> gradle
        1 * buildController.configure() >> { throw failure }
    }

    def "can run tasks before run action"() {
        given:
        def clientProvidedBuildActionRunTasks = new ClientProvidedBuildAction(startParameter, action, true /* isRunTasks */, clientSubscriptions)

        when:
        runner.run(clientProvidedBuildActionRunTasks, buildController)

        then:
        1 * buildController.run()
    }

    def "can run action InternalActionVersion2"() {
        given:
        def model = new Object()
        def internalAction = Mock(InternalBuildActionVersion2)

        when:
        def result = runner.run(clientProvidedBuildAction, buildController)

        then:
        result.clientResult == model
        result.buildFailure == null
        result.clientFailure == null

        and:
        1 * internalAction.execute(_) >> model
        1 * payloadSerializer.deserialize(action) >> internalAction
    }
}
