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
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.logging.format.LogHeaderFormatter
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.progress.BuildOperationCategory
import org.gradle.util.MockTimeProvider
import spock.lang.Subject

class GroupingProgressLogEventGeneratorTest extends OutputSpecification {
    private final OutputEventListener downstreamListener = Mock(OutputEventListener)
    def logHeaderFormatter = Mock(LogHeaderFormatter)
    def timeProvider = new MockTimeProvider()
    @Subject listener = new GroupingProgressLogEventGenerator(downstreamListener, timeProvider, logHeaderFormatter, false)

    def setup() {
        logHeaderFormatter.format(_, _, _, _) >> { h, d, s, st -> [new StyledTextOutputEvent.Span("Header $d")] }
    }

    def "forwards logs with no group"() {
        given:
        def event = event('message')

        when: listener.onOutput(event)

        then: 1 * downstreamListener.onOutput(event)
        and: 0 * downstreamListener._
    }

    def "renders ungrouped logging headers"() {
        given:
        def header = "Download http://repo.somewhere.com/foo.jar"
        def downloadEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Download description", null, header, null, 0, null, null, BuildOperationCategory.UNCATEGORIZED)

        when: listener.onOutput(downloadEvent)

        then: 1 * downstreamListener.onOutput({ it.getMessage() == header })
        and: 0 * _
    }

    def "forwards a group of logs for a task"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, 0, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, "STATUS")

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(warningMessage)

        then: 0 * downstreamListener._

        when: listener.onOutput(taskCompleteEvent)

        then: 1 * logHeaderFormatter.format(taskStartEvent.loggingHeader, taskStartEvent.description, taskStartEvent.shortDescription, taskCompleteEvent.status) >> { [new StyledTextOutputEvent.Span("Header $taskStartEvent.description")] }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Warning: some deprecation or something" })
        then: 0 * downstreamListener._
    }

    def "allows render of task headers when configured"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, 0, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, "STATUS")
        def listener = new GroupingProgressLogEventGenerator(downstreamListener, timeProvider, logHeaderFormatter, true)

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(taskCompleteEvent)

        then: 1 * logHeaderFormatter.format(taskStartEvent.loggingHeader, taskStartEvent.description, taskStartEvent.shortDescription, taskCompleteEvent.status) >> {
            [new StyledTextOutputEvent.Span(taskStartEvent.description + ' '), new StyledTextOutputEvent.Span(StyledTextOutput.Style.ProgressStatus, taskCompleteEvent.status)]
        }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>$taskStartEvent.description </Normal><ProgressStatus>$taskCompleteEvent.status</ProgressStatus>".toString() })
        then: 0 * downstreamListener._
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

        then: 0 * downstreamListener._

        when: listener.onOutput(taskCompleteEvent)

        then: 1 * logHeaderFormatter.format(taskStartEvent.loggingHeader, taskStartEvent.description, taskStartEvent.shortDescription, taskCompleteEvent.status) >> { [new StyledTextOutputEvent.Span("Header $taskStartEvent.description")] }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Child task log message" })
        then: 0 * downstreamListener._
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

        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Warning: some deprecation or something" })
        then: 1 * downstreamListener.onOutput(endBuildEvent)
        then: 0 * downstreamListener._
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
        listener.onOutput(taskBCompleteEvent)
        listener.onOutput(taskACompleteEvent)

        then: 1 * logHeaderFormatter.format(taskBStartEvent.loggingHeader, taskBStartEvent.description, taskBStartEvent.shortDescription, taskBCompleteEvent.status) >> { [new StyledTextOutputEvent.Span("Header $taskBStartEvent.description")] }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskBStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task b" })
        then: 1 * logHeaderFormatter.format(taskAStartEvent.loggingHeader, taskAStartEvent.description, taskAStartEvent.shortDescription, taskACompleteEvent.status) >> { [new StyledTextOutputEvent.Span("Header $taskAStartEvent.description")] }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskAStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task a" })
        then: 0 * downstreamListener._
    }

    def "does not forward a batched group of events after receiving update now event before flush period"() {
        given:
        def olderTimestamp = timeProvider.currentTime
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), olderTimestamp, CATEGORY, "Execute :a", ":a", null, null, 0, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def taskAOutput = event(olderTimestamp, 'message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def updateNowEvent = new UpdateNowEvent(timeProvider.currentTime)

        when:
        listener.onOutput(taskAStartEvent)
        listener.onOutput(taskAOutput)

        then:
        0 * downstreamListener.onOutput(_)

        when:
        listener.onOutput(updateNowEvent)

        then:
        0 * downstreamListener.onOutput(_)
    }

    def "forwards a batched group of events after receiving update now event after flush period"() {
        given:
        def olderTimestamp = timeProvider.currentTime - GroupingProgressLogEventGenerator.LONG_RUNNING_TASK_OUTPUT_FLUSH_TIMEOUT
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), olderTimestamp, CATEGORY, "Execute :a", ":a", null, null, 0, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def taskAOutput = event(olderTimestamp, 'message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def updateNowEvent = new UpdateNowEvent(timeProvider.currentTime)

        when:
        listener.onOutput(taskAStartEvent)
        listener.onOutput(taskAOutput)

        then:
        0 * downstreamListener.onOutput(_)

        when:
        listener.onOutput(updateNowEvent)

        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskAStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task a" })
    }

    def "forwards multiple batched groups of events after receiving update now event after flush period"() {
        given:
        def olderTimestamp = timeProvider.currentTime - GroupingProgressLogEventGenerator.LONG_RUNNING_TASK_OUTPUT_FLUSH_TIMEOUT
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), olderTimestamp, CATEGORY, "Execute :a", ":a", null, null, 0, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def taskAOutput = event(olderTimestamp, 'message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskBStartEvent = new ProgressStartEvent(new OperationIdentifier(-13L), new OperationIdentifier(-14L), olderTimestamp, CATEGORY, "Execute :b", ":b", null, null, 0, new OperationIdentifier(12L), null, BuildOperationCategory.TASK)
        def taskBOutput = event(olderTimestamp, 'message for task b', LogLevel.WARN, taskBStartEvent.buildOperationId)
        def updateNowEvent = new UpdateNowEvent(timeProvider.currentTime)

        when:
        listener.onOutput(taskAStartEvent)
        listener.onOutput(taskBStartEvent)
        listener.onOutput(taskAOutput)
        listener.onOutput(taskBOutput)

        then:
        0 * downstreamListener.onOutput(_)

        when:
        listener.onOutput(updateNowEvent)

        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskAStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task a" })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskBStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task b" })
    }
}
