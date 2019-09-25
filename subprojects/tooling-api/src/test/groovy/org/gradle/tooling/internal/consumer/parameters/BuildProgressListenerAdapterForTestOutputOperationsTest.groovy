/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.tooling.internal.consumer.parameters

import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.test.Destination
import org.gradle.tooling.events.test.TestOutputEvent
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.events.InternalTestOutputDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTestOutputEvent
import org.gradle.tooling.internal.protocol.events.InternalTestOutputResult
import spock.lang.Specification

class BuildProgressListenerAdapterForTestOutputOperationsTest extends Specification {

    def "adapter is only subscribing to transform progress events if at least one test output progress listener is attached"() {
        when:
        def adapter = createAdapter()

        then:
        adapter.subscribedOperations == []

        when:
        def listener = Mock(ProgressListener)
        adapter = createAdapter(listener)

        then:
        adapter.subscribedOperations == [InternalBuildProgressListener.TEST_OUTPUT]
    }

    def "convert to TestOutputEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def outputDescriptor = Mock(InternalTestOutputDescriptor)
        _ * outputDescriptor.getId() >> 2
        _ * outputDescriptor.getName() >> 'output'
        _ * outputDescriptor.getDisplayName() >> 'output'

        def outputResult = Mock(InternalTestOutputResult)
        _ * outputResult.getDestination() >> 1 //StdErr
        _ * outputResult.getMessage() >> 'output message'

        def outputEvent = Mock(InternalTestOutputEvent)
        _ * outputEvent.getEventTime() >> 999
        _ * outputEvent.getDisplayName() >> 'output'
        _ * outputEvent.getDescriptor() >> outputDescriptor
        _ * outputEvent.getResult() >> outputResult

        adapter.onEvent(outputEvent)

        then:
        1 * listener.statusChanged(_ as TestOutputEvent) >> { TestOutputEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "StdErr: output message"
            assert event.destination == Destination.StdErr
            assert event.message == 'output message'
        }
    }

    private static BuildProgressListenerAdapter createAdapter() {
        new BuildProgressListenerAdapter([:])
    }

    private static BuildProgressListenerAdapter createAdapter(ProgressListener listener) {
        new BuildProgressListenerAdapter([(OperationType.TEST_OUTPUT): [listener]])
    }
}
