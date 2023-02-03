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
import spock.lang.Specification
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.protocol.PhasedActionResultListener
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload

class PhasedActionEventConsumerTest extends Specification {
    def phasedActionResultListener = Mock(PhasedActionResultListener)
    def delegateEventConsumer = Mock(BuildEventConsumer)
    def payloadSerializer = Stub(PayloadSerializer)

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
        def serializedResult1 = Stub(SerializedPayload)
        def result2 = 'result2'
        def serializedResult2 = Stub(SerializedPayload)

        given:
        payloadSerializer.deserialize(serializedResult1) >> result1
        payloadSerializer.deserialize(serializedResult2) >> result2

        when:
        eventConsumer.dispatch(new PhasedBuildActionResult(serializedResult1, PhasedActionResult.Phase.PROJECTS_LOADED))
        eventConsumer.dispatch(new PhasedBuildActionResult(serializedResult2, PhasedActionResult.Phase.BUILD_FINISHED))

        then:
        1 * phasedActionResultListener.onResult({
            it.getPhase() == PhasedActionResult.Phase.PROJECTS_LOADED &&
                it.getResult() == result1
        })
        1 * phasedActionResultListener.onResult({
            it.getPhase() == PhasedActionResult.Phase.BUILD_FINISHED &&
                it.getResult() == result2
        })
        0 * delegateEventConsumer.dispatch(_)
    }
}
