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
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.progress.BuildOperationType
import org.gradle.util.MockTimeProvider
import spock.lang.Subject

class GroupingProgressLogEventGeneratorTest extends OutputSpecification {
    private final OutputEventListener downstreamListener = Mock(OutputEventListener)
    def timeProvider = new MockTimeProvider()
    @Subject listener = new GroupingProgressLogEventGenerator(downstreamListener)

    def "forwards logs with no group"() {
        given:
        def event = event('message')

        when: listener.onOutput(event)

        then: 1 * downstreamListener.onOutput(event)
        and: 0 * _
    }

    def "forwards a group of logs for a task"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationType.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)

        when: listener.onOutput([taskStartEvent, warningMessage])

        then: 0 * _

        when: listener.onOutput(new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, null))

        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Header>> Execute :foo</Header><Normal>${GroupingProgressLogEventGenerator.EOL}</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Warning: some deprecation or something" })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] " })
        then: 0 * _
    }

    def "groups logs for child operations of tasks"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationType.TASK)
        def subtaskStartEvent = new ProgressStartEvent(new OperationIdentifier(-4L), new OperationIdentifier(-5L), tenAm, CATEGORY, ":foo subtask", "subtask", null, null, new OperationIdentifier(3L), taskStartEvent.buildOperationId, BuildOperationType.UNCATEGORIZED)
        def warningMessage = event('Child task log message', LogLevel.WARN, subtaskStartEvent.buildOperationId)
        def subTaskCompleteEvent = new ProgressCompleteEvent(subtaskStartEvent.progressOperationId, tenAm, 'subtask complete')
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, 'UP-TO-DATE')

        when: listener.onOutput([taskStartEvent, subtaskStartEvent, warningMessage, subTaskCompleteEvent])

        then: 0 * _

        when: listener.onOutput(taskCompleteEvent)

        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Header>> Execute :foo</Header><Normal>${GroupingProgressLogEventGenerator.EOL}</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Child task log message" })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] " })
        then: 0 * _
    }

    def "flushes all remaining groups on end of build"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationType.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)
        def endBuildEvent = new EndOutputEvent()

        when: listener.onOutput([taskStartEvent, warningMessage, endBuildEvent])

        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Header>> Execute :foo</Header><Normal>${GroupingProgressLogEventGenerator.EOL}</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Warning: some deprecation or something" })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] " })
        then: 1 * downstreamListener.onOutput(endBuildEvent)
        then: 0 * _
    }

    def "handles multiple simultaneous operations"() {
        given:
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :a", ":a", null, null, new OperationIdentifier(2L), null, BuildOperationType.TASK)
        def taskBStartEvent = new ProgressStartEvent(new OperationIdentifier(-5L), new OperationIdentifier(-6L), tenAm, CATEGORY, "Execute :b", ":b", null, null, new OperationIdentifier(3L), null, BuildOperationType.TASK)
        def taskAOutput = event('message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskBOutput = event('message for task b', LogLevel.WARN, taskBStartEvent.buildOperationId)
        def taskBCompleteEvent = new ProgressCompleteEvent(taskBStartEvent.progressOperationId, tenAm, null)
        def taskACompleteEvent = new ProgressCompleteEvent(taskAStartEvent.progressOperationId, tenAm, 'UP-TO-DATE')

        when: listener.onOutput([taskAStartEvent, taskBStartEvent, taskAOutput, taskBOutput, taskBCompleteEvent, taskACompleteEvent])

        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Header>> Execute :b</Header><Normal>${GroupingProgressLogEventGenerator.EOL}</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task b" })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] " })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Header>> Execute :a</Header><Normal>${GroupingProgressLogEventGenerator.EOL}</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task a" })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] " })
        then: 0 * _
    }
}
