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
import org.gradle.api.BuildCancelledException
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.service.ServiceRegistry
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException
import org.gradle.tooling.internal.protocol.InternalPhasedAction
import org.gradle.tooling.internal.provider.BuildActionResult
import org.gradle.tooling.internal.provider.BuildClientSubscriptions
import org.gradle.tooling.internal.provider.ClientProvidedPhasedAction
import org.gradle.tooling.internal.provider.PhasedBuildActionResult
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload
import spock.lang.Specification

class ClientProvidedPhasedActionRunnerTest extends Specification {

    def startParameter = Mock(StartParameterInternal)
    def serializedAction = Mock(SerializedPayload)
    def clientSubscriptions = Mock(BuildClientSubscriptions)
    def clientProvidedPhasedAction = new ClientProvidedPhasedAction(startParameter, serializedAction, clientSubscriptions)

    def afterLoadingAction = Mock(InternalBuildActionVersion2)
    def afterConfigurationAction = Mock(InternalBuildActionVersion2)
    def afterBuildAction = Mock(InternalBuildActionVersion2)
    def phasedAction = Mock(InternalPhasedAction) {
        getAfterLoadingAction() >> afterLoadingAction
        getAfterConfigurationAction() >> afterConfigurationAction
        getAfterBuildAction() >> afterBuildAction
    }

    def nullSerialized = Mock(SerializedPayload)
    def buildEventConsumer = Mock(BuildEventConsumer)
    def payloadSerializer = Mock(PayloadSerializer) {
        deserialize(serializedAction) >> phasedAction
        serialize(null) >> nullSerialized
    }
    BuildListener listener
    def gradle = Stub(GradleInternal) {
        addBuildListener(_) >> { BuildListener listener ->
            this.listener = listener
        }
        getServices() >> Stub(ServiceRegistry) {
            get(PayloadSerializer) >> payloadSerializer
            get(BuildEventConsumer) >> buildEventConsumer
        }
    }
    def buildResult = Mock(BuildResult)
    def buildController = Mock(BuildController) {
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
        def result3 = 'result3'
        def serializedResult3 = Mock(SerializedPayload)

        given:
        payloadSerializer.serialize(result1) >> serializedResult1
        payloadSerializer.serialize(result2) >> serializedResult2
        payloadSerializer.serialize(result3) >> serializedResult3

        when:
        runner.run(clientProvidedPhasedAction, buildController)

        then:
        1 * afterLoadingAction.execute(_) >> result1
        1 * afterConfigurationAction.execute(_) >> result2
        1 * afterBuildAction.execute(_) >> result3
        1 * buildController.setResult({
            it instanceof BuildActionResult &&
                it.failure == null &&
                it.result == nullSerialized
        })
        1 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.type == PhasedBuildActionResult.Type.AFTER_LOADING &&
                it.failure == null &&
                it.result == serializedResult1
        })
        1 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.type == PhasedBuildActionResult.Type.AFTER_CONFIGURATION &&
                it.failure == null &&
                it.result == serializedResult2
        })
        1 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.type == PhasedBuildActionResult.Type.AFTER_BUILD &&
                it.failure == null &&
                it.result == serializedResult3
        })
    }

    def "do not run later build action when fails"() {
        def serializedFailure = Mock(SerializedPayload)

        given:
        payloadSerializer.serialize({ it instanceof RuntimeException }) >> serializedFailure

        when:
        runner.run(clientProvidedPhasedAction, buildController)

        then:
        1 * afterLoadingAction.execute(_) >> { throw new RuntimeException() }
        0 * afterConfigurationAction.execute(_)
        0 * afterBuildAction.execute(_)
        1 * buildController.setResult(_) >> { args ->
            def it = args[0]
            assert it instanceof BuildActionResult
            assert it.failure == serializedFailure
            assert it.result == null
            buildController.hasResult() >> true
        }
        1 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.type == PhasedBuildActionResult.Type.AFTER_LOADING &&
                it.failure == serializedFailure &&
                it.result == null
        })
        0 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.type == PhasedBuildActionResult.Type.AFTER_CONFIGURATION
        })
        0 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.type == PhasedBuildActionResult.Type.AFTER_BUILD
        })
    }

    def "exceptions are wrapped"() {
        when:
        runner.run(clientProvidedPhasedAction, buildController)

        then:
        1 * afterLoadingAction.execute(_) >> { throw new RuntimeException() }
        1 * payloadSerializer.serialize({ it instanceof InternalBuildActionFailureException })

        when:
        runner.run(clientProvidedPhasedAction, buildController)

        then:
        1 * afterLoadingAction.execute(_) >> { throw new BuildCancelledException() }
        1 * payloadSerializer.serialize({ it instanceof InternalBuildCancelledException })
    }

    def "action not run if null"() {
        when:
        runner.run(clientProvidedPhasedAction, buildController)

        then:
        noExceptionThrown()
        1 * phasedAction.getAfterLoadingAction() >> null
        1 * phasedAction.getAfterConfigurationAction() >> null
        1 * phasedAction.getAfterBuildAction() >> null
        1 * buildController.setResult({
            it instanceof BuildActionResult &&
                it.failure == null &&
                it.result == nullSerialized
        })
        0 * buildEventConsumer.dispatch(_)
    }
}
