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

package org.gradle.tooling.internal.provider

import org.gradle.initialization.BuildEventConsumer
import org.gradle.testing.internal.util.Specification
import org.gradle.tooling.internal.protocol.AfterBuildResult
import org.gradle.tooling.internal.protocol.AfterConfigurationResult
import org.gradle.tooling.internal.protocol.AfterLoadingResult
import org.gradle.tooling.internal.protocol.PhasedActionResultListener
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload

class PhasedActionEventConsumerTest extends Specification {
    def phasedActionResultListener = Mock(PhasedActionResultListener)
    def delegateEventConsumer = Mock(BuildEventConsumer)
    def payloadSerializer = Mock(PayloadSerializer)

    def eventConsumer = new PhasedActionEventConsumer(phasedActionResultListener, payloadSerializer, delegateEventConsumer)

    def "delegate when not a phase action result"() {
        def event = new Object()

        when:
        eventConsumer.dispatch(event)

        then:
        1 * delegateEventConsumer.dispatch(event)
        0 * phasedActionResultListener.onResult(_)
    }

    def "deserialize and correctly map results"() {
        def result1 = 'result1'
        def serializedResult1 = Mock(SerializedPayload)
        def result2 = 'result2'
        def serializedResult2 = Mock(SerializedPayload)
        def result3 = 'result3'
        def serializedResult3 = Mock(SerializedPayload)

        given:
        payloadSerializer.deserialize(serializedResult1) >> result1
        payloadSerializer.deserialize(serializedResult2) >> result2
        payloadSerializer.deserialize(serializedResult3) >> result3

        when:
        eventConsumer.dispatch(new PhasedBuildActionResult(serializedResult1, null, PhasedBuildActionResult.Type.AFTER_LOADING))
        eventConsumer.dispatch(new PhasedBuildActionResult(serializedResult2, null, PhasedBuildActionResult.Type.AFTER_CONFIGURATION))
        eventConsumer.dispatch(new PhasedBuildActionResult(serializedResult3, null, PhasedBuildActionResult.Type.AFTER_BUILD))

        then:
        1 * phasedActionResultListener.onResult({
            it instanceof AfterLoadingResult &&
                it.getResult() == result1
        })
        1 * phasedActionResultListener.onResult({
            it instanceof AfterConfigurationResult &&
                it.getResult() == result2
        })
        1 * phasedActionResultListener.onResult({
            it instanceof AfterBuildResult &&
                it.getResult() == result3
        })
        0 * phasedActionResultListener.onResult({
            it.getFailure() != null
        })
    }

    def "deserialize and correctly map failures"() {
        def failure1 = new RuntimeException()
        def serializedFailure1 = Mock(SerializedPayload)
        def failure2 = new RuntimeException()
        def serializedFailure2 = Mock(SerializedPayload)
        def failure3 = new RuntimeException()
        def serializedFailure3 = Mock(SerializedPayload)

        given:
        payloadSerializer.deserialize(serializedFailure1) >> failure1
        payloadSerializer.deserialize(serializedFailure2) >> failure2
        payloadSerializer.deserialize(serializedFailure3) >> failure3

        when:
        eventConsumer.dispatch(new PhasedBuildActionResult(null, serializedFailure1, PhasedBuildActionResult.Type.AFTER_LOADING))
        eventConsumer.dispatch(new PhasedBuildActionResult(null, serializedFailure2, PhasedBuildActionResult.Type.AFTER_CONFIGURATION))
        eventConsumer.dispatch(new PhasedBuildActionResult(null, serializedFailure3, PhasedBuildActionResult.Type.AFTER_BUILD))

        then:
        1 * phasedActionResultListener.onResult({
            it instanceof AfterLoadingResult &&
                it.getFailure() == failure1
        })
        1 * phasedActionResultListener.onResult({
            it instanceof AfterConfigurationResult &&
                it.getFailure() == failure2
        })
        1 * phasedActionResultListener.onResult({
            it instanceof AfterBuildResult &&
                it.getFailure() == failure3
        })
        0 * phasedActionResultListener.onResult({
            it.getResult() != null
        })
    }
}
