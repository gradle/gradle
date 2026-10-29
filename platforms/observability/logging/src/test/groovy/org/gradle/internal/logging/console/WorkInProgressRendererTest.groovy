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

import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.operations.BuildOperationCategory
import spock.lang.Issue
import spock.lang.Subject

@Subject(WorkInProgressRenderer)
class WorkInProgressRendererTest extends OutputSpecification {
    def listener = Mock(OutputEventListener)
    def console = new ConsoleStub();
    def metaData = Mock(ConsoleMetaData);
    def renderer = new WorkInProgressRenderer(listener, console.getBuildProgressArea(), new DefaultWorkInProgressFormatter(metaData), new ConsoleLayoutCalculator(metaData))

    def "start and complete events in the same batch are ignored"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(1, ":foo"))
        renderer.onOutput(start(2, ":bar"))
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :bar"]
        cursorParkText == ""
    }

    def "shows the owner of a start event before its status"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: "Building task graph", owner: ":lib"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :lib > Building task graph"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "shows how long the work of a line has run, at each update"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(1, ":foo"))
        renderer.onOutput(new UpdateNowEvent(tenAm))
        renderer.onOutput(new UpdateNowEvent(tenAm + 9_999))
        console.flush()

        then:
        progressArea.display == ["> :foo"]

        when:
        renderer.onOutput(new UpdateNowEvent(tenAm + 10_000))
        console.flush()

        then:
        progressArea.display == ["> :foo  10s"]

        when:
        renderer.onOutput(new UpdateNowEvent(tenAm + 12_000))
        console.flush()

        then:
        progressArea.display == ["> :foo  12s"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "shows how long each work action has run on its line, and how long the task has run on the line of the count"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(new UpdateNowEvent(tenAm))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(new UpdateNowEvent(tenAm + 2_000))
        renderer.onOutput(start(id: 3, parentId: 1, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(new UpdateNowEvent(tenAm + 5_000))
        renderer.onOutput(new UpdateNowEvent(tenAm + 16_000))
        console.flush()

        then:
        progressArea.display == ["> :work > 0 work actions completed  16s", "> :work > Executing work action A  14s", "> :work > Executing work action B  11s"]

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(new UpdateNowEvent(tenAm + 20_000))
        console.flush()

        then:
        progressArea.display == ["> :work > 1 work action completed  20s", "> IDLE", "> :work > Executing work action B  15s"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "times the work of a line on the clock of the console, which can differ from the clock of the daemon"() {
        given:
        metaData.getRows() >> 6

        when:
        // The clock of the console is one hour ahead of the timestamp of the start event
        renderer.onOutput(start(1, ":foo"))
        renderer.onOutput(new UpdateNowEvent(tenAm + 3_600_000))
        console.flush()

        then:
        progressArea.display == ["> :foo"]

        when:
        renderer.onOutput(new UpdateNowEvent(tenAm + 3_610_000))
        console.flush()

        then:
        progressArea.display == ["> :foo  10s"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "times the work of a line from when the console gets its start, also just before a prompt"() {
        given:
        metaData.getRows() >> 6
        def prompting = new UserInputConsoleRenderer(renderer, console, Mock(GlobalUserInputReceiver), Mock(TemporaryFileProvider))

        when:
        prompting.onOutput(start(1, ":foo"))
        prompting.onOutput(new UserInputRequestEvent())
        prompting.onOutput(new UpdateNowEvent(tenAm))
        prompting.onOutput(new UpdateNowEvent(tenAm + 72_000))
        prompting.onOutput(new UserInputResumeEvent(tenAm + 72_000))
        prompting.onOutput(new UpdateNowEvent(tenAm + 72_100))
        console.flush()

        then:
        progressArea.display == ["> :foo  1m 12s"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "times the work of a line from when the console gets its start, also during a prompt"() {
        given:
        metaData.getRows() >> 6
        def prompting = new UserInputConsoleRenderer(renderer, console, Mock(GlobalUserInputReceiver), Mock(TemporaryFileProvider))

        when:
        prompting.onOutput(new UserInputRequestEvent())
        prompting.onOutput(start(1, ":foo"))
        prompting.onOutput(new UpdateNowEvent(tenAm))
        prompting.onOutput(new UpdateNowEvent(tenAm + 72_000))
        prompting.onOutput(new UserInputResumeEvent(tenAm + 72_000))
        prompting.onOutput(new UpdateNowEvent(tenAm + 72_100))
        console.flush()

        then:
        progressArea.display == ["> :foo  1m 12s"]
    }

    def "events are forwarded to the listener even if are not rendered"() {
        given:
        metaData.getRows() >> 6

        def startEvent = start(1, ":foo")
        def completeEvent = complete(1)

        when:
        renderer.onOutput(startEvent)
        renderer.onOutput(completeEvent)

        then:
        1 * listener.onOutput(startEvent)
        1 * listener.onOutput(completeEvent)
    }

    def "progress operation without message have no effect on progress area"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(1))
        console.flush()

        then:
        progressArea.display == []
        cursorParkText == ""
    }

    def "parent progress operation without message is ignored when renderable child completes"() {
        given:
        metaData.getRows() >> 2

        when:
        renderer.onOutput(start(1))
        renderer.onOutput(start(id: 2, parentId: 1, status: ":foo"))
        renderer.onOutput(start(id: 3, parentId: null, status: ":bar"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :foo"]

        and:
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :bar"]
    }

    def "forward the event unmodified to the listener"() {
        given:
        metaData.getRows() >> 6

        def event1 = event("event 1")
        def event2 = event("event 2")

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)

        then:
        1 * listener.onOutput(event1)
        1 * listener.onOutput(event2)
        0 * _
    }

    def "completing children of offscreen parents"() {
        given:
        metaData.getRows() >> 2 // only a single line will be displayable

        // This test confirms that when a child task is completed for a
        // parent task that is currently offscreen, that we don't accumulate
        // additional copies of that pending parent task to show later
        when:
        // start 1 and 2
        renderer.onOutput(start(id: 1, status: ":one"))
        renderer.onOutput(start(id: 2, status: ":two"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        // task 2 should not be shown because there should not be enough space for it
        progressArea.display == ["> :one"]
        cursorParkText == "  (1 line not showing)"

        and:
        // start a child for 2 while it's offscreen, and complete that child
        renderer.onOutput(start(id: 3, parentId: 2, status: ":two:one"))
        renderer.onOutput(updateNow())
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        // start a second child for 2 while it's offscreen, and complete that child too
        renderer.onOutput(start(id: 4, parentId: 2, status: ":two:two"))
        renderer.onOutput(updateNow())
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())
        console.flush()

        and:
        // note that task 2 is still not shown
        progressArea.display == ["> :one"]
        cursorParkText == "  (1 line not showing)"

        then:
        // task 1 completes
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())
        console.flush()

        and:
        // task 2 should appear because there should be enough space for it now
        progressArea.display == ["> :two"]
        cursorParkText == ""

        then:
        // task 2 completes
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        // there should be no more copies of task 2 claiming to be running
        progressArea.display == ["> IDLE"]
        cursorParkText == ""
    }

    def "multiple offscreen operations"() {
        given:
        metaData.getRows() >> 4 // only two active operations can be displayable

        when:
        renderer.onOutput(start(id: 1, status: ":one"))
        renderer.onOutput(start(id: 2, status: ":two"))
        renderer.onOutput(start(id: 3, status: ":three"))
        renderer.onOutput(start(id: 4, status: ":four"))
        renderer.onOutput(start(id: 5, status: ":five"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :one", "> :two"]
        cursorParkText == "  (3 lines not showing)"

        and:
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :one", "> :three"]
        cursorParkText == "  (2 lines not showing)"

        and:
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :four", "> :three"]
        cursorParkText == "  (1 line not showing)"

        and:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :four", "> :five"]
        cursorParkText == ""

        and:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> IDLE", "> :five"]
        cursorParkText == ""

        and:
        renderer.onOutput(complete(5))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> IDLE", "> IDLE"]
        cursorParkText == ""
    }


    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "does not show the line of the single work action of a task"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":pmdMain"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :pmdMain"]

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :pmdMain"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "gives the line of a task to the work of its single work action, and takes it back when this work ends"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":pmdMain"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action PmdInvoker", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 3, parentId: 2, status: "Resolve dependencies"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :pmdMain > Resolve dependencies"]

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :pmdMain"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "keeps the line of a task for the work of its single work action when the own work of the task ends"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 3, parentId: 2, status: "Resolve dependencies"))
        renderer.onOutput(start(id: 4, parentId: 1, status: "Resolve dependencies of :foo"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Resolve dependencies", "> :work > Resolve dependencies of :foo"]

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Resolve dependencies", "> IDLE"]

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work", "> IDLE"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "gives the line of a task to the work of a work action submitted by its single work action"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 3, parentId: 2, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 4, parentId: 3, status: "Resolve dependencies"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Resolve dependencies"]

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "does not show an operation again when the work of its single work action ends after this operation"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "B"))
        renderer.onOutput(start(id: 3, parentId: 2, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > B"]

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(start(id: 4, parentId: 3, status: "Resolve dependencies"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work", "> :work > B > Resolve dependencies"]

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work", "> IDLE"]

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work", "> IDLE"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "shows the work actions submitted by the single work action of a task next to the line of the task, and their count on this line"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 3, parentId: 2, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 4, parentId: 2, status: "Executing work action C", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 0 work actions completed", "> :work > Executing work action B", "> :work > Executing work action C"]

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 1 work action completed", "> IDLE", "> :work > Executing work action C"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "shows the line of a task again when its single work action submits a second work action while the work of the first one has the line of the task"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 3, parentId: 2, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 4, parentId: 3, status: "Resolve dependencies"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Resolve dependencies"]

        when:
        renderer.onOutput(start(id: 5, parentId: 2, status: "Executing work action C", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Executing work action B > Resolve dependencies", "> :work > 0 work actions completed", "> :work > Executing work action C"]

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Executing work action B", "> :work > 0 work actions completed", "> :work > Executing work action C"]

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(5))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> IDLE", "> :work > 2 work actions completed", "> IDLE"]

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> IDLE", "> :work", "> IDLE"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "shows the line of a task again when a second work action starts while the work of the first one has the line of the task"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 3, parentId: 2, status: "Resolve dependencies"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Resolve dependencies"]

        when:
        renderer.onOutput(start(id: 4, parentId: 1, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Executing work action A > Resolve dependencies", "> :work > 0 work actions completed", "> :work > Executing work action B"]

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Executing work action A", "> :work > 0 work actions completed", "> :work > Executing work action B"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "shows the lines of the work actions of a task next to its line, from the second one"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work"]

        when:
        renderer.onOutput(start(id: 3, parentId: 1, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 0 work actions completed", "> :work > Executing work action A", "> :work > Executing work action B"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "shows the line of a second work action that starts after the first one ended"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        renderer.onOutput(complete(2))
        renderer.onOutput(start(id: 3, parentId: 1, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 1 work action completed", "> :work > Executing work action B"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "shows the line of the first work action when a second one starts and ends between two updates"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        renderer.onOutput(start(id: 3, parentId: 1, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 1 work action completed", "> :work > Executing work action A"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "shows the line of a task again when its own work ends while its work actions run"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 3, parentId: 1, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 4, parentId: 1, status: "Resolve dependencies"))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Resolve dependencies", "> :work > Executing work action A", "> :work > Executing work action B"]

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 0 work actions completed", "> :work > Executing work action A", "> :work > Executing work action B"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "keeps a single line for a task when one of its work actions ends"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 3, parentId: 1, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 1 work action completed", "> IDLE", "> :work > Executing work action B"]

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 2 work actions completed", "> IDLE", "> IDLE"]
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "keeps the start order of waiting work actions when one of them ends"() {
        given:
        metaData.getRows() >> 4

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Resolve dependencies"))
        renderer.onOutput(start(id: 3, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 4, parentId: 1, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 5, parentId: 1, status: "Executing work action C", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 6, parentId: 1, status: "Executing work action D", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Resolve dependencies", "> :work > Executing work action A"]
        cursorParkText == "  (3 lines not showing)"

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Resolve dependencies", "> :work > Executing work action A"]
        cursorParkText == "  (2 lines not showing)"

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Resolve dependencies", "> :work > Executing work action C"]
        cursorParkText == "  (1 line not showing)"

        when:
        renderer.onOutput(complete(5))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > Resolve dependencies", "> :work > Executing work action D"]
        cursorParkText == ""
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "gives a freed line to the next waiting work action when there are more work actions than lines"() {
        given:
        metaData.getRows() >> 4

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 3, parentId: 1, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(start(id: 4, parentId: 1, status: "Executing work action C", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 0 work actions completed", "> :work > Executing work action A"]
        cursorParkText == "  (2 lines not showing)"

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 1 work action completed", "> :work > Executing work action B"]
        cursorParkText == "  (1 line not showing)"

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 2 work actions completed", "> :work > Executing work action C"]
        cursorParkText == ""

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 3 work actions completed", "> IDLE"]
        cursorParkText == ""
    }

    @Issue("https://github.com/gradle/gradle/issues/39544")
    def "counts the work actions that start and end between two updates"() {
        given:
        metaData.getRows() >> 6

        when:
        renderer.onOutput(start(id: 1, status: ":work"))
        renderer.onOutput(start(id: 2, parentId: 1, status: "Executing work action A", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(complete(2))
        renderer.onOutput(start(id: 3, parentId: 1, status: "Executing work action B", buildOperationCategory: BuildOperationCategory.WORK_ACTION))
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())
        console.flush()

        then:
        progressArea.display == ["> :work > 2 work actions completed"]
    }


    private ConsoleStub.TestableBuildProgressTextArea getProgressArea() {
        console.buildProgressArea as ConsoleStub.TestableBuildProgressTextArea
    }


    private String getCursorParkText() {
        (console.buildProgressArea.cursorParkLine as ConsoleStub.TestableRedrawableLabel).getDisplay()
    }
}
