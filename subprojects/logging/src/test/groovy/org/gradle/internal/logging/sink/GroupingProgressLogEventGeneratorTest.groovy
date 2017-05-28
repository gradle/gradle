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
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.format.LogHeaderFormatter
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.progress.BuildOperationCategory
import org.gradle.util.MockTimeProvider
import spock.lang.Subject

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

class GroupingProgressLogEventGeneratorTest extends OutputSpecification {
    private final OutputEventListener downstreamListener = Mock(OutputEventListener)
    def logHeaderFormatter = Mock(LogHeaderFormatter)
    def timeProvider = new MockTimeProvider()
    def executor = Mock(ScheduledExecutorService)
    def future = Mock(ScheduledFuture)
    @Subject listener = new GroupingProgressLogEventGenerator(downstreamListener, timeProvider, executor, logHeaderFormatter, false)
    def latestScheduledRunnable

    def setup() {
        executor.scheduleAtFixedRate(_, _, _, _) >> { runnable, initialDelay, delay, timeUnit ->
            latestScheduledRunnable = runnable
            future
        }
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
        def downloadEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Download description", null, header, null, null, null, BuildOperationCategory.UNCATEGORIZED)

        when: listener.onOutput(downloadEvent)

        then: 1 * downstreamListener.onOutput({ it.getMessage() == header })
        and: 0 * _
    }

    def "forwards a group of logs for a task"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, "STATUS")

        when: listener.onOutput([taskStartEvent, warningMessage])

        then: 0 * downstreamListener._

        when: listener.onOutput(taskCompleteEvent)

        then: 1 * logHeaderFormatter.format(taskStartEvent.loggingHeader, taskStartEvent.description, taskStartEvent.shortDescription, taskCompleteEvent.status) >> { [new StyledTextOutputEvent.Span("Header $taskStartEvent.description")] }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Warning: some deprecation or something" })
        then: 0 * downstreamListener._
    }

    def "allows render of task headers when configured"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, "STATUS")

        when: new GroupingProgressLogEventGenerator(downstreamListener, timeProvider, executor, logHeaderFormatter, true).onOutput([taskStartEvent, taskCompleteEvent])

        then: 1 * logHeaderFormatter.format(taskStartEvent.loggingHeader, taskStartEvent.description, taskStartEvent.shortDescription, taskCompleteEvent.status) >> {
            [new StyledTextOutputEvent.Span(taskStartEvent.description + ' '), new StyledTextOutputEvent.Span(StyledTextOutput.Style.ProgressStatus, taskCompleteEvent.status)]
        }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>$taskStartEvent.description </Normal><ProgressStatus>$taskCompleteEvent.status</ProgressStatus>".toString() })
        then: 0 * downstreamListener._
    }

    def "groups logs for child operations of tasks"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def subtaskStartEvent = new ProgressStartEvent(new OperationIdentifier(-4L), new OperationIdentifier(-5L), tenAm, CATEGORY, ":foo subtask", "subtask", null, null, new OperationIdentifier(3L), taskStartEvent.buildOperationId, BuildOperationCategory.UNCATEGORIZED)
        def warningMessage = event('Child task log message', LogLevel.WARN, subtaskStartEvent.buildOperationId)
        def subTaskCompleteEvent = new ProgressCompleteEvent(subtaskStartEvent.progressOperationId, tenAm, 'subtask complete')
        def taskCompleteEvent = new ProgressCompleteEvent(taskStartEvent.progressOperationId, tenAm, 'UP-TO-DATE')

        when: listener.onOutput([taskStartEvent, subtaskStartEvent, warningMessage, subTaskCompleteEvent])

        then: 0 * downstreamListener._

        when: listener.onOutput(taskCompleteEvent)

        then: 1 * logHeaderFormatter.format(taskStartEvent.loggingHeader, taskStartEvent.description, taskStartEvent.shortDescription, taskCompleteEvent.status) >> { [new StyledTextOutputEvent.Span("Header $taskStartEvent.description")] }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Child task log message" })
        then: 0 * downstreamListener._
    }

    def "flushes all remaining groups on end of build"() {
        given:
        def taskStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :foo", ":foo", null, null, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, taskStartEvent.buildOperationId)
        def endBuildEvent = new EndOutputEvent()

        when: listener.onOutput([taskStartEvent, warningMessage, endBuildEvent])

        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] Warning: some deprecation or something" })
        then: 1 * downstreamListener.onOutput(endBuildEvent)
        then: 0 * downstreamListener._
    }

    def "handles multiple simultaneous operations"() {
        given:
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), tenAm, CATEGORY, "Execute :a", ":a", null, null, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def taskBStartEvent = new ProgressStartEvent(new OperationIdentifier(-5L), new OperationIdentifier(-6L), tenAm, CATEGORY, "Execute :b", ":b", null, null, new OperationIdentifier(3L), null, BuildOperationCategory.TASK)
        def taskAOutput = event('message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskBOutput = event('message for task b', LogLevel.WARN, taskBStartEvent.buildOperationId)
        def taskBCompleteEvent = new ProgressCompleteEvent(taskBStartEvent.progressOperationId, tenAm, null)
        def taskACompleteEvent = new ProgressCompleteEvent(taskAStartEvent.progressOperationId, tenAm, 'UP-TO-DATE')

        when: listener.onOutput([taskAStartEvent, taskBStartEvent, taskAOutput, taskBOutput, taskBCompleteEvent, taskACompleteEvent])

        then: 1 * logHeaderFormatter.format(taskBStartEvent.loggingHeader, taskBStartEvent.description, taskBStartEvent.shortDescription, taskBCompleteEvent.status) >> { [new StyledTextOutputEvent.Span("Header $taskBStartEvent.description")] }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskBStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task b" })
        then: 1 * logHeaderFormatter.format(taskAStartEvent.loggingHeader, taskAStartEvent.description, taskAStartEvent.shortDescription, taskACompleteEvent.status) >> { [new StyledTextOutputEvent.Span("Header $taskAStartEvent.description")] }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskAStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task a" })
        then: 0 * downstreamListener._
    }

    def "schedules render at fixed rate once an root progress event is started"() {
        def event = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), timeProvider.currentTime, CATEGORY, "Execute :a", ":a", null, null, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)

        when:
        listener.onOutput([event] as ArrayList<OutputEvent>)

        then:
        1 * executor.scheduleAtFixedRate(_, _, _, _)
    }

    def "forward a group of logs after a initial delay"() {
        given:
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), timeProvider.currentTime, CATEGORY, "Execute :a", ":a", null, null, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def taskAOutput = event(timeProvider.currentTime, 'message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskACompleteEvent = new ProgressCompleteEvent(taskAStartEvent.progressOperationId, timeProvider.currentTime, 'UP-TO-DATE')
        def endEvent = new EndOutputEvent()

        when:
        listener.onOutput([taskAStartEvent, taskAOutput])
        timeProvider.increment(GroupingProgressLogEventGenerator.LONG_RUNNING_TASK_OUTPUT_FLUSH_TIMEOUT)
        latestScheduledRunnable.run()

        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskAStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task a" })

        when:
        listener.onOutput([taskACompleteEvent, endEvent])

        then:
        1 * downstreamListener.onOutput(endEvent)
        0 * downstreamListener._
    }

    def "keep forwarding the task output while still in focus"() {
        given:
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), timeProvider.currentTime, CATEGORY, "Execute :a", ":a", null, null, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def taskAOutput1 = event(timeProvider.currentTime, 'first message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskAOutput2 = event(timeProvider.currentTime, 'second message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskACompleteEvent = new ProgressCompleteEvent(taskAStartEvent.progressOperationId, timeProvider.currentTime, 'UP-TO-DATE')
        def endEvent = new EndOutputEvent()

        and:
        listener.onOutput([taskAStartEvent, taskAOutput1])
        timeProvider.increment(GroupingProgressLogEventGenerator.LONG_RUNNING_TASK_OUTPUT_FLUSH_TIMEOUT)
        latestScheduledRunnable.run()

        when: listener.onOutput([taskAOutput2])

        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] second message for task a" })

        when: listener.onOutput([taskACompleteEvent, endEvent])

        then:
        1 * downstreamListener.onOutput(endEvent)
        0 * downstreamListener._
    }

    def "correctly interlace grouped of logs of long running task with a short running task"() {
        given:
        def taskAStartEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), timeProvider.currentTime, CATEGORY, "Execute :a", ":a", null, null, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def taskAOutput1 = event(timeProvider.currentTime, 'first message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskBStartEvent = new ProgressStartEvent(new OperationIdentifier(-5L), new OperationIdentifier(-6L), timeProvider.currentTime, CATEGORY, "Execute :b", ":b", null, null, new OperationIdentifier(3L), null, BuildOperationCategory.TASK)
        def taskBOutput = event(timeProvider.currentTime, 'message for task b', LogLevel.WARN, taskBStartEvent.buildOperationId)
        def taskBCompleteEvent = new ProgressCompleteEvent(taskBStartEvent.progressOperationId, timeProvider.currentTime, 'UP-TO-DATE')
        def taskAOutput2 = event(timeProvider.currentTime, 'second message for task a', LogLevel.WARN, taskAStartEvent.buildOperationId)
        def taskACompleteEvent = new ProgressCompleteEvent(taskAStartEvent.progressOperationId, timeProvider.currentTime, 'UP-TO-DATE')
        def endEvent = new EndOutputEvent()

        and:
        listener.onOutput([taskAStartEvent, taskAOutput1])
        timeProvider.increment(GroupingProgressLogEventGenerator.LONG_RUNNING_TASK_OUTPUT_FLUSH_TIMEOUT)
        latestScheduledRunnable.run()

        // then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] first message for task a" })

        when: listener.onOutput([taskBStartEvent, taskBOutput, taskBCompleteEvent])

        then: 1 * logHeaderFormatter.format(taskBStartEvent.loggingHeader, taskBStartEvent.description, taskBStartEvent.shortDescription, taskBCompleteEvent.status) >> { [new StyledTextOutputEvent.Span("Header $taskBStartEvent.description")] }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskBStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] message for task b" })

        when: listener.onOutput([taskAOutput2, taskACompleteEvent, endEvent])

        then: 1 * logHeaderFormatter.format(taskAStartEvent.loggingHeader, taskAStartEvent.description, taskAStartEvent.shortDescription, taskACompleteEvent.status) >> { [new StyledTextOutputEvent.Span("Header $taskAStartEvent.description")] }
        then: 1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $taskAStartEvent.description</Normal>".toString() })
        then: 1 * downstreamListener.onOutput({ it.toString() == "[WARN] [category] second message for task a" })
    }

    def "correctly cancel the future and shutdown executor once the end event is received"() {
        def startEvent = new ProgressStartEvent(new OperationIdentifier(-3L), new OperationIdentifier(-4L), timeProvider.currentTime, CATEGORY, "Execute :a", ":a", null, null, new OperationIdentifier(2L), null, BuildOperationCategory.TASK)
        def end = new EndOutputEvent()

        given:
        listener.onOutput([startEvent] as ArrayList<OutputEvent>)

        when:
        listener.onOutput([end] as ArrayList<OutputEvent>)

        then: 1 * future.cancel(false)
        then: 1 * executor.shutdownNow()
    }
}
