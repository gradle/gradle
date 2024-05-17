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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeModelAction
import org.gradle.internal.buildtree.BuildTreeModelController
import org.gradle.tooling.internal.protocol.InternalBuildAction
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2
import org.gradle.tooling.internal.provider.action.ClientProvidedBuildAction
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload
import spock.lang.Specification

import java.util.function.Function

class ClientProvidedBuildActionRunnerTest extends Specification {

    def startParameter = Mock(StartParameterInternal)
    def action = Mock(SerializedPayload)
    def clientSubscriptions = Mock(BuildEventSubscriptions)
    def payloadSerializer = Mock(PayloadSerializer)
    def gradle = Stub(GradleInternal)
    def buildController = Mock(BuildTreeLifecycleController) {
        getGradle() >> this.gradle
    }
    def modelController = Stub(BuildTreeModelController)
    def clientProvidedBuildAction = new ClientProvidedBuildAction(startParameter, action, false /* isRunTasks */, clientSubscriptions)
    def runner = new ClientProvidedBuildActionRunner(Stub(BuildControllerFactory), payloadSerializer)

    def "can run action and returns result when completed"() {
        given:
        def model = new Object()
        def serialized = Stub(SerializedPayload)
        def internalAction = Mock(InternalBuildAction)

        when:
        def result = runner.run(clientProvidedBuildAction, buildController)

        then:
        result.clientResult == serialized
        result.buildFailure == null
        result.clientFailure == null

        and:
        1 * payloadSerializer.deserialize(action) >> internalAction
        1 * buildController.fromBuildModel(false, _) >> { Boolean b, BuildTreeModelAction modelAction -> modelAction.fromBuildModel(modelController) }
        1 * internalAction.execute(_) >> model
        1 * payloadSerializer.serialize(model) >> serialized
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
        1 * buildController.fromBuildModel(false, _) >> { Boolean b, BuildTreeModelAction modelAction -> modelAction.fromBuildModel(modelController) }
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
        1 * buildController.fromBuildModel(false, _) >> { throw failure }
    }

    def "can run tasks before run action"() {
        given:
        def clientProvidedBuildActionRunTasks = new ClientProvidedBuildAction(startParameter, action, true /* isRunTasks */, clientSubscriptions)

        when:
        runner.run(clientProvidedBuildActionRunTasks, buildController)

        then:
        1 * buildController.fromBuildModel(true, _) >> { Boolean b, Function function -> function.apply(gradle) }
    }

    def "can run action InternalActionVersion2"() {
        given:
        def model = new Object()
        def internalAction = Mock(InternalBuildActionVersion2)
        def serializedResult = Stub(SerializedPayload)

        when:
        def result = runner.run(clientProvidedBuildAction, buildController)

        then:
        result.clientResult == serializedResult
        result.buildFailure == null
        result.clientFailure == null

        and:
        1 * payloadSerializer.deserialize(action) >> internalAction
        1 * buildController.fromBuildModel(false, _) >> { Boolean b, BuildTreeModelAction modelAction -> modelAction.fromBuildModel(modelController) }
        1 * internalAction.execute(_) >> model
        1 * payloadSerializer.serialize(model) >> serializedResult
    }
}
