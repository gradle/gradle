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
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.logging.format.LogHeaderFormatter
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Subject

import static org.gradle.internal.time.TestTime.timestampOf

class GroupingProgressLogEventGeneratorTest extends OutputSpecification {
    private final OutputEventListener downstreamListener = Mock(OutputEventListener)
    def logHeaderFormatter = Mock(LogHeaderFormatter)
    @Subject
    listener = new GroupingProgressLogEventGenerator(downstreamListener, logHeaderFormatter, false)

    def setup() {
        logHeaderFormatter.format(_, _, _) >> { d, st, f -> [new StyledTextOutputEvent.Span("Header $d")] }
    }

    def "forwards log event that does not belong to any operation"() {
        given:
        def event = event('message')

        when:
        listener.onOutput(event)

        then:
        1 * downstreamListener.onOutput(event)
        and:
        0 * downstreamListener._
    }

    def "forwards a series of log events that do not belong to any operations"() {
        given:
        def event1 = event('message 1')
        def event2 = event('message 2')
        def event3 = event('message 3')

        when:
        listener.onOutput(event1)
        listener.onOutput(event2)
        listener.onOutput(event3)

        then:
        1 * downstreamListener.onOutput(event1)
        1 * downstreamListener.onOutput(event2)
        1 * downstreamListener.onOutput(event3)

        and:
        0 * downstreamListener._
    }

    def "forwards log events for ungrouped operation along with header"() {
        given:
        def header = "Download http://repo.somewhere.com/foo.jar"
        def operationId = new OperationIdentifier(-3L)
        def buildOpId = new OperationIdentifier(-3L)
        def downloadEvent = new ProgressStartEvent(operationId, new OperationIdentifier(-4L), tenAm, CATEGORY, "Download description", header, null, 0, true, buildOpId, BuildOperationCategory.UNCATEGORIZED)
        def progressEvent = new LogEvent(tenAm, CATEGORY, LogLevel.INFO, "message", null, buildOpId)
        def endEvent = new ProgressCompleteEvent(operationId, tenAm, "all good", false)

        when:
        listener.onOutput(downloadEvent)
        listener.onOutput(progressEvent)
        listener.onOutput(endEvent)

        then:
        1 * downstreamListener.onOutput({ it.getMessage() == header })
        1 * downstreamListener.onOutput(progressEvent)

        and:
        0 * _
    }

    def "buffers log events for a grouped build operation until the operation is complete"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, "STATUS", false)

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(warningMessage)

        then:
        0 * downstreamListener._

        when:
        listener.onOutput(taskCompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :foo</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Warning: some deprecation or something" })
        then:
        0 * downstreamListener._
    }

    def "forwards log events that are not grouped during execution of build operation that is grouped"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, "STATUS", false)
        def event = event('message')

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(warningMessage)
        listener.onOutput(event)

        then:
        1 * downstreamListener.onOutput(event)
        then:
        0 * downstreamListener._

        when:
        listener.onOutput(taskCompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :foo</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Warning: some deprecation or something" })
        then:
        0 * downstreamListener._
    }

    def "does not forward header for grouped build operation with no log events"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, "STATUS", false)

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(taskCompleteEvent)

        then:
        0 * downstreamListener._
    }

    def "forwards header for grouped build operation with no log events when configured"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, "STATUS", false)
        def listener = new GroupingProgressLogEventGenerator(downstreamListener, logHeaderFormatter, true)

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(taskCompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :foo</Normal>" })
        then:
        0 * downstreamListener._
    }

    def "adds spacer line between build operations with log events and not between build operations with no log events"() {
        given:
        def startEvent1 = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :a", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def logEvent1 = event('a message', LogLevel.WARN, startEvent1.buildOperationId)
        def completeEvent1 = new ProgressCompleteEvent(startEvent1.progressOperationId, tenAm, "STATUS", false)
        def startEvent2 = new ProgressStartEvent(new OperationIdentifier(-5L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :b", null, null, 0, true, new OperationIdentifier(-5L), BuildOperationCategory.TASK)
        def completeEvent2 = new ProgressCompleteEvent(startEvent2.progressOperationId, tenAm, "STATUS", false)
        def startEvent3 = new ProgressStartEvent(new OperationIdentifier(-6L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :c", null, null, 0, true, new OperationIdentifier(-6L), BuildOperationCategory.TASK)
        def completeEvent3 = new ProgressCompleteEvent(startEvent3.progressOperationId, tenAm, "STATUS", false)
        def startEvent4 = new ProgressStartEvent(new OperationIdentifier(-7L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :d", null, null, 0, true, new OperationIdentifier(-7L), BuildOperationCategory.TASK)
        def logEvent4 = event('d message', LogLevel.WARN, startEvent4.buildOperationId)
        def completeEvent4 = new ProgressCompleteEvent(startEvent4.progressOperationId, tenAm, "STATUS", false)
        def listener = new GroupingProgressLogEventGenerator(downstreamListener, logHeaderFormatter, true)

        when:
        listener.onOutput(startEvent1)
        listener.onOutput(logEvent1)
        listener.onOutput(completeEvent1)
        listener.onOutput(startEvent2)
        listener.onOutput(completeEvent2)
        listener.onOutput(startEvent3)
        listener.onOutput(completeEvent3)
        listener.onOutput(startEvent4)
        listener.onOutput(logEvent4)
        listener.onOutput(completeEvent4)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :a</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] a message" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :b</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :c</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :d</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] d message" })
        then:
        0 * downstreamListener._
    }

    def "groups logs for child operations of tasks"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-2L), tenAm, CATEGORY, "Execute :foo", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def subtaskStartEvent = new ProgressStartEvent(new OperationIdentifier(-4L), new OperationIdentifier(-3L), tenAm, CATEGORY, ":foo subtask", null, null, 0, true, new OperationIdentifier(-4L), BuildOperationCategory.UNCATEGORIZED)
        def warningMessage = event('Child task log message', LogLevel.WARN, subtaskStartEvent.buildOperationId)
        def subTaskCompleteEvent = new ProgressCompleteEvent(subtaskStartEvent.progressOperationId, tenAm, 'subtask complete', false)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, 'UP-TO-DATE', false)

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(subtaskStartEvent)
        listener.onOutput(warningMessage)
        listener.onOutput(subTaskCompleteEvent)

        then:
        0 * downstreamListener._

        when:
        listener.onOutput(taskCompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :foo</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Child task log message" })
        then:
        0 * downstreamListener._
    }

    def "groups logs for child operations of tasks when progress operations interleaved"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-2L), tenAm, CATEGORY, "Execute :foo", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def progStartEvent = new ProgressStartEvent(new OperationIdentifier(-4L), new OperationIdentifier(-3L), tenAm, CATEGORY, ":foo subtask", null, null, 0, false, new OperationIdentifier(-3L), BuildOperationCategory.UNCATEGORIZED)
        def subtaskStartEvent = new ProgressStartEvent(new OperationIdentifier(-5L), new OperationIdentifier(-4L), tenAm, CATEGORY, ":foo subtask", null, null, 0, true, new OperationIdentifier(-5L), BuildOperationCategory.UNCATEGORIZED)
        def warningMessage = event('Child task log message', LogLevel.WARN, subtaskStartEvent.buildOperationId)
        def subTaskCompleteEvent = new ProgressCompleteEvent(subtaskStartEvent.progressOperationId, tenAm, 'subtask complete', false)
        def progCompleteEvent = new ProgressCompleteEvent(progStartEvent.progressOperationId, tenAm, 'subtask complete', false)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, 'UP-TO-DATE', false)

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(progStartEvent)
        listener.onOutput(subtaskStartEvent)
        listener.onOutput(warningMessage)
        listener.onOutput(subTaskCompleteEvent)
        listener.onOutput(progCompleteEvent)

        then:
        0 * downstreamListener._

        when:
        listener.onOutput(taskCompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :foo</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Child task log message" })
        then:
        0 * downstreamListener._
    }

    def "flushes all remaining groups on end of build"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)
        def endBuildEvent = new EndOutputEvent()

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(warningMessage)
        listener.onOutput(endBuildEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :foo</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Warning: some deprecation or something" })
        then:
        1 * downstreamListener.onOutput(endBuildEvent)
        then:
        0 * downstreamListener._
    }

    def "handles multiple simultaneous operations"() {
        given:
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :a", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def taskBStartEvent = new ProgressStartEvent(new OperationIdentifier(-5L), new OperationIdentifier(-6L), tenAm, CATEGORY, "Execute :b", null, null, 0, true, new OperationIdentifier(-5L), BuildOperationCategory.TASK)
        def taskAOutput = event('message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskBOutput = event('message for task b', LogLevel.WARN, taskBStartEvent.buildOperationId)
        def taskBCompleteEvent = new ProgressCompleteEvent(taskBStartEvent.progressOperationId, tenAm, null, false)
        def taskACompleteEvent = new ProgressCompleteEvent(taskAStartEvent.progressOperationId, tenAm, 'UP-TO-DATE', false)

        when:
        listener.onOutput(taskAStartEvent)
        listener.onOutput(taskBStartEvent)
        listener.onOutput(taskAOutput)
        listener.onOutput(taskBOutput)
        listener.onOutput(taskBCompleteEvent)
        listener.onOutput(taskACompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :b</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task b" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :a</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task a" })
        then:
        0 * downstreamListener._
    }

    def "does not forward a batched group of events after receiving update now event before flush period"() {
        given:
        def olderTimestamp = timestampOf(0)
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), olderTimestamp, CATEGORY, "Execute :a", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def taskAOutput = event(olderTimestamp, 'message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def updateNowEvent = new UpdateNowEvent(olderTimestamp.plusMillis(100))

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
        def olderTimestamp = timestampOf(0)
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), olderTimestamp, CATEGORY, "Execute :a", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def taskAOutput = event(olderTimestamp, 'message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def updateNowEvent = new UpdateNowEvent(olderTimestamp.plusMillis(GroupingProgressLogEventGenerator.HIGH_WATERMARK_FLUSH_TIMEOUT + 10))

        when:
        listener.onOutput(taskAStartEvent)
        listener.onOutput(taskAOutput)

        then:
        0 * downstreamListener.onOutput(_)

        when:
        listener.onOutput(updateNowEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :a</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task a" })
        0 * downstreamListener._
    }

    def "forwards multiple batched groups of events after receiving update now event after flush period"() {
        given:
        def olderTimestamp = timestampOf(0)
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), olderTimestamp, CATEGORY, "Execute :a", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def taskAOutput = event(olderTimestamp, 'message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskBStartEvent = new ProgressStartEvent(new OperationIdentifier(-13L), new OperationIdentifier(-14L), olderTimestamp, CATEGORY, "Execute :b", null, null, 0, true, new OperationIdentifier(-13L), BuildOperationCategory.TASK)
        def taskBOutput = event(olderTimestamp, 'message for task b', LogLevel.WARN, taskBStartEvent.buildOperationId)
        def updateNowEvent = new UpdateNowEvent(olderTimestamp.plusMillis(GroupingProgressLogEventGenerator.HIGH_WATERMARK_FLUSH_TIMEOUT + 10))

        when:
        listener.onOutput(taskAStartEvent)
        listener.onOutput(taskBStartEvent)
        listener.onOutput(taskAOutput)
        listener.onOutput(taskBOutput)

        then:
        0 * downstreamListener.onOutput(_)

        when:
        listener.onOutput(updateNowEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :a</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task a" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :b</Normal>" })
        then:
        1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task b" })
        0 * downstreamListener._
    }

    def "forwards header again when status changes after output is flushed (verbose: #verbose)"() {
        def olderTimestamp = timestampOf(0)
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), olderTimestamp, CATEGORY, "Execute :a", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def event1 = event(olderTimestamp, 'message for task a', LogLevel.WARN, taskStartEvent.buildOperationId)
        def updateNowEvent = new UpdateNowEvent(olderTimestamp.plusMillis(GroupingProgressLogEventGenerator.HIGH_WATERMARK_FLUSH_TIMEOUT + 10))
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, olderTimestamp.plusMillis(GroupingProgressLogEventGenerator.HIGH_WATERMARK_FLUSH_TIMEOUT + 10), "STATUS", false)
        def listener = new GroupingProgressLogEventGenerator(downstreamListener, logHeaderFormatter, verbose)

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(event1)
        listener.onOutput(updateNowEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :a</Normal>" })
        1 * downstreamListener.onOutput(event1)
        0 * downstreamListener._

        when:
        listener.onOutput(taskCompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] " })
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :a</Normal>" })
        0 * downstreamListener._

        where:
        verbose << [true, false]
    }

    def "forwards header when not verbose and task status is failed"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", null, null, 0, true, new OperationIdentifier(-3L), BuildOperationCategory.TASK)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, "FAILED", true)
        def listener = new GroupingProgressLogEventGenerator(downstreamListener, logHeaderFormatter, false)

        when:
        listener.onOutput(taskStartEvent)
        listener.onOutput(taskCompleteEvent)

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header Execute :foo</Normal>" })
        then:
        0 * downstreamListener._
    }
}
