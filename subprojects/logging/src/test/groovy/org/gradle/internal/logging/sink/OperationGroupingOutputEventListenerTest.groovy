/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.sink

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.logging.events.OutputEventGroupListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.progress.BuildOperationCategory
import org.gradle.util.MockExecutor
import org.gradle.util.MockTimeProvider
import spock.lang.Subject

class OperationGroupingOutputEventListenerTest extends OutputSpecification {
    private final OutputEventGroupListener downstreamListener = Mock(OutputEventGroupListener)
    def timeProvider = new MockTimeProvider()
    def executor = new MockExecutor()
    @Subject listener = new OperationGroupingOutputEventListener(downstreamListener, timeProvider, executor, 5000)

    def "forwards logs with no group"() {
        given:
        def event = event('message')

        when:
        listener.onOutput(event)

        then:
        1 * downstreamListener.onOutput(event)
        0 * downstreamListener._
    }

    def "renders ungrouped logging headers"() {
        given:
        def header = "Download http://repo.somewhere.com/foo.jar"
        def downloadEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Download description", null, header, null, 0, null, null, BuildOperationCategory.UNCATEGORIZED)

        when:
        listener.onOutput(downloadEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] Download http://repo.somewhere.com/foo.jar" })
        1 * downstreamListener.onOutput(downloadEvent)
        0 * _
    }

    def "forwards a group of logs for a task"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, 0, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, "STATUS")

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(warningMessage)

        then:
        1 * downstreamListener.onOutput(taskStartEvent)
        0 * downstreamListener._

        when:
        listener.onOutput(taskCompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == group(taskStartEvent, [warningMessage], taskCompleteEvent.status).toString() })
        1 * downstreamListener.onOutput(taskCompleteEvent)
        0 * downstreamListener._
    }

    def "groups logs for child operations of tasks"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, 0, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def subtaskStartEvent = new ProgressStartEvent(new OperationIdentifier(-4L), new OperationIdentifier(-5L), tenAm, CATEGORY, ":foo subtask", "subtask", null, null, 0, new OperationIdentifier(3L), taskStartEvent.buildOperationId, BuildOperationCategory.UNCATEGORIZED)
        def warningMessage = event('Child task log message', LogLevel.WARN, subtaskStartEvent.buildOperationId)
        def subTaskCompleteEvent = new ProgressCompleteEvent(subtaskStartEvent.progressOperationId, tenAm, 'subtask complete')
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, 'UP-TO-DATE')

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(subtaskStartEvent)
        listener.onOutput(warningMessage)
        listener.onOutput(subTaskCompleteEvent)

        then:
        1 * downstreamListener.onOutput(taskStartEvent)
        1 * downstreamListener.onOutput(subtaskStartEvent)
        1 * downstreamListener.onOutput(subTaskCompleteEvent)
        0 * downstreamListener._

        when:
        listener.onOutput(taskCompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == group(taskStartEvent, [warningMessage], taskCompleteEvent.status).toString() })
        1 * downstreamListener.onOutput(taskCompleteEvent)
        0 * downstreamListener._
    }

    def "flushes all remaining groups on end of build"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, 0, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)
        def endBuildEvent = new EndOutputEvent()

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(warningMessage)
        listener.onOutput(endBuildEvent)

        then:
        1 * downstreamListener.onOutput(taskStartEvent)
        1 * downstreamListener.onOutput({ it.toString() == group(taskStartEvent, [warningMessage], "").toString() })
        1 * downstreamListener.onOutput(endBuildEvent)
        0 * downstreamListener._
    }

    def "handles multiple simultaneous operations"() {
        given:
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :a", ":a", null, null, 0, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def taskBStartEvent = new ProgressStartEvent(new OperationIdentifier(-5L), new OperationIdentifier(-6L), tenAm, CATEGORY, "Execute :b", ":b", null, null, 0, new OperationIdentifier(3L), null, BuildOperationCategory.TASK)
        def taskAOutput = event('message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskBOutput = event('message for task b', LogLevel.WARN, taskBStartEvent.buildOperationId)
        def taskBCompleteEvent = new ProgressCompleteEvent(taskBStartEvent.progressOperationId, tenAm, null)
        def taskACompleteEvent = new ProgressCompleteEvent(taskAStartEvent.progressOperationId, tenAm, 'UP-TO-DATE')

        when:
        listener.onOutput(taskAStartEvent)
        listener.onOutput(taskBStartEvent)
        listener.onOutput(taskAOutput)
        listener.onOutput(taskBOutput)

        then:
        1 * downstreamListener.onOutput(taskAStartEvent)
        1 * downstreamListener.onOutput(taskBStartEvent)
        0 * downstreamListener._

        when:
        listener.onOutput(taskBCompleteEvent)
        listener.onOutput(taskACompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == group(taskBStartEvent, [taskBOutput], taskBCompleteEvent.status).toString() })
        1 * downstreamListener.onOutput(taskBCompleteEvent)
        1 * downstreamListener.onOutput({ it.toString() == group(taskAStartEvent, [taskAOutput], taskACompleteEvent.status).toString() })
        1 * downstreamListener.onOutput(taskACompleteEvent)
        0 * downstreamListener._
    }
}
