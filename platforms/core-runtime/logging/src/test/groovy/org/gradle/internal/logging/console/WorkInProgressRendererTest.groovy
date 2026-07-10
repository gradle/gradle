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

import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
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


    private ConsoleStub.TestableBuildProgressTextArea getProgressArea() {
        console.buildProgressArea as ConsoleStub.TestableBuildProgressTextArea
    }


    private String getCursorParkText() {
        (console.buildProgressArea.cursorParkLine as ConsoleStub.TestableRedrawableLabel).getDisplay()
    }
}
