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
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.progress.BuildOperationCategory
import org.gradle.internal.progress.BuildOperationListener
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class LoggingBuildOperationNotificationBridgeTest extends Specification {

    LoggingManagerInternal loggingManagerInternal = Mock()
    BuildOperationListener buildOperationListener = Mock()
    @Shared
    OperationIdentifier operationId = Mock()

    LoggingBuildOperationNotificationBridge bridge = new LoggingBuildOperationNotificationBridge(loggingManagerInternal, buildOperationListener)

    @Unroll
    def "forwards #eventType with operationId"() {
        when:
        bridge.onOutput(eventWithBuildOperationId)

        then:
        1 * buildOperationListener.progress(_, _) >> {
            assert it[0] == operationId
            assert it[1].details == eventWithBuildOperationId
        }


        when:
        bridge.onOutput(eventWithNoBuildOperationId)

        then:
        0 * buildOperationListener.progress(_, _)

        where:
        eventType             | eventWithBuildOperationId                                          | eventWithNoBuildOperationId
        LogEvent              | new LogEvent(0, 'c', LogLevel.INFO, 'm', null, operationId)        | new LogEvent(0, 'c', LogLevel.INFO, 'm', null, null)
        StyledTextOutputEvent | new StyledTextOutputEvent(0, 'c', LogLevel.INFO, operationId, 'm') | new StyledTextOutputEvent(0, 'c', LogLevel.INFO, null, 'm')
        ProgressStartEvent    | progressStartEvent(operationId)                                    | progressStartEvent(null)
    }

    def "filters non supported output events"() {
        when:
        bridge.onOutput(progressStartEvent(operationId, "Upload stuff"))

        then:
        1 * buildOperationListener.progress(_, _)


        when:
        bridge.onOutput(progressStartEvent(operationId, "Download stuff"))

        then:
        1 * buildOperationListener.progress(_, _)


        when:
        bridge.onOutput(progressStartEvent(operationId, null))
        then:
        0 * buildOperationListener.progress(_, _)
    }


    def "registers / unregisters itself as output listener"() {
        when:
        def loggingBuildOperationNotificationBridge = new LoggingBuildOperationNotificationBridge(loggingManagerInternal, buildOperationListener)

        then:
        loggingManagerInternal.addOutputEventListener(loggingBuildOperationNotificationBridge)


        when:
        loggingBuildOperationNotificationBridge.stop()

        then:
        loggingManagerInternal.removeOutputEventListener(loggingBuildOperationNotificationBridge)
    }

    private ProgressStartEvent progressStartEvent(OperationIdentifier operationId, String header = 'header') {
        new ProgressStartEvent(null, null, 0, 'c', 'd', 'sd', header, 's', 0, operationId, null, BuildOperationCategory.UNCATEGORIZED)
    }
}
