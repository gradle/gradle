/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.BatchOutputEventListener
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.progress.BuildOperationType
import org.gradle.util.MockExecutor
import org.gradle.util.MockTimeProvider
import spock.lang.Subject

class LogGroupingOutputEventListenerTest extends OutputSpecification {
    def downstreamListener = Mock(BatchOutputEventListener)
    def timeProvider = new MockTimeProvider()
    def executor = new MockExecutor()

    @Subject listener = new LogGroupingOutputEventListener(downstreamListener, executor, timeProvider)

    def "forwards uncategorized events"() {
        def logLevelChangeEvent = new LogLevelChangeEvent(LogLevel.LIFECYCLE)

        when:
        listener.onOutput(logLevelChangeEvent)

        then:
        1 * downstreamListener.onOutput(logLevelChangeEvent)
        0 * _
    }

    def "forwards logs with no group"() {
        given:
        def event = event('message')

        when:
        listener.onOutput(event)

        then:
        1 * downstreamListener.onOutput(event)
        0 * _
    }

    def "forwards a group of logs for a task"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationType.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)

        when:
        listener.onOutput([taskStartEvent, warningMessage])

        then:
        0 * _

        when:
        listener.onOutput(new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, CATEGORY, 'Complete: :foo', null))

        then:
        1 * downstreamListener.onOutput(_ as List<OutputEvent>)
        1 * downstreamListener.onOutput({ it.getMessage() == "" })
        0 * _
    }

    def "groups logs for child operations of tasks"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationType.TASK)
        def subtaskStartEvent = new ProgressStartEvent(new OperationIdentifier(-4L), new OperationIdentifier(-5L), tenAm, CATEGORY, ":foo subtask", "subtask", null, null, new OperationIdentifier(3L), taskStartEvent.buildOperationId, BuildOperationType.UNCATEGORIZED)
        def warningMessage = event('Child task log message', LogLevel.WARN, subtaskStartEvent.buildOperationId)
        def subTaskCompleteEvent = new ProgressCompleteEvent(subtaskStartEvent.progressOperationId, tenAm, CATEGORY, 'Complete: subtask', 'subtask complete')
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, CATEGORY, 'Complete: :foo', 'UP-TO-DATE')

        when:
        listener.onOutput([taskStartEvent, subtaskStartEvent, warningMessage, subTaskCompleteEvent])

        then:
        0 * _

        when:
        listener.onOutput(taskCompleteEvent)

        then:
        1 * downstreamListener.onOutput(_ as List<OutputEvent>)
        1 * downstreamListener.onOutput({ it.getMessage() == "" })
        0 * _
    }

    def "flushes all remaining groups on end of build"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationType.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)
        def endBuildEvent = new EndOutputEvent()

        when:
        listener.onOutput([taskStartEvent, warningMessage, endBuildEvent])

        then:
        1 * downstreamListener.onOutput(_ as List<OutputEvent>)
        1 * downstreamListener.onOutput(endBuildEvent)
        1 * downstreamListener.onOutput(_ as OutputEvent)
        0 * _
    }

    def "does not forward group with no logs"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationType.TASK)
        def completeEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, CATEGORY, 'Complete: :foo', null)

        when:
        listener.onOutput([taskStartEvent, completeEvent])

        then:
        0 * _
    }

    void flush() {
        executor.runNow()
    }
}
