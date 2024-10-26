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

package org.gradle.internal.operations.logging

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.sink.OutputEventListenerManager
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Shared
import spock.lang.Specification

import static org.gradle.internal.time.TestTime.timestampOf

class LoggingBuildOperationProgressBroadcasterTest extends Specification {

    def outputEventListenerManager = Mock(OutputEventListenerManager)
    def buildOperationProgressEventEmitter = Mock(BuildOperationProgressEventEmitter)

    @Shared
    def testOperationId = Mock(OperationIdentifier)

    @Shared
    def fallbackOperationId = Mock(OperationIdentifier)

    LoggingBuildOperationProgressBroadcaster bridge = new LoggingBuildOperationProgressBroadcaster(outputEventListenerManager, buildOperationProgressEventEmitter)

    def setup() {
        bridge.rootBuildOperation = fallbackOperationId
    }

    def "forwards #eventType with operationId"() {
        when:
        bridge.onOutput(eventWithBuildOperationId)

        then:
        1 * buildOperationProgressEventEmitter.emit(_, _, _) >> { OperationIdentifier operationIdentifier, def timestamp, Object details ->
            assert operationIdentifier == testOperationId
            assert details == eventWithBuildOperationId
        }


        when:
        bridge.onOutput(eventWithFallbackBuildOperationId)

        then:
        1 * buildOperationProgressEventEmitter.emit(_, _, _) >> { OperationIdentifier operationIdentifier, def timestamp, Object details ->
            assert operationIdentifier == fallbackOperationId
            assert details == eventWithFallbackBuildOperationId
        }

        where:
        eventType             | eventWithBuildOperationId                                                           | eventWithFallbackBuildOperationId
        LogEvent              | new LogEvent(timestampOf(0), 'c', LogLevel.INFO, 'm', null, testOperationId)        | new LogEvent(timestampOf(0), 'c', LogLevel.INFO, 'm', null, null)
        StyledTextOutputEvent | new StyledTextOutputEvent(timestampOf(0), 'c', LogLevel.INFO, testOperationId, 'm') | new StyledTextOutputEvent(timestampOf(0), 'c', LogLevel.INFO, null, 'm')
        ProgressStartEvent    | progressStartEvent(testOperationId)                                                 | progressStartEvent(null)
    }

    def "does not forward progress start events with no logging header"() {
        when:
        bridge.onOutput(progressStartEvent(testOperationId, null))

        then:
        0 * buildOperationProgressEventEmitter.emit(_, _, _)
    }

    def "registers / unregisters itself as output listener"() {
        when:
        def loggingBuildOperationNotificationBridge = new LoggingBuildOperationProgressBroadcaster(outputEventListenerManager, buildOperationProgressEventEmitter)

        then:
        outputEventListenerManager.setListener(loggingBuildOperationNotificationBridge)


        when:
        loggingBuildOperationNotificationBridge.stop()

        then:
        outputEventListenerManager.removeListener(loggingBuildOperationNotificationBridge)
    }

    private ProgressStartEvent progressStartEvent(OperationIdentifier operationId, String header = 'header') {
        new ProgressStartEvent(null, null, timestampOf(0), 'c', 'd', header, 's', 0, false, operationId, BuildOperationCategory.UNCATEGORIZED)
    }
}
