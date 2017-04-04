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
import org.gradle.internal.logging.events.BatchOutputEventListener
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.time.TimeProvider

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

class BuildStatusRendererTest extends OutputSpecification {
    def listener = Mock(BatchOutputEventListener)
    def label = new TestStyledLabel()
    def console = Mock(Console)
    def consoleMetaData = Mock(ConsoleMetaData)
    def timeProvider = Mock(TimeProvider)
    def future = Mock(ScheduledFuture)
    def executor = Mock(ScheduledExecutorService)
    long currentTimeMs
    def renderer = new BuildStatusRenderer(listener, label, console, consoleMetaData, timeProvider, executor)

    def setup() {
        executor.scheduleAtFixedRate(_, _, _, _) >> future
        timeProvider.getCurrentTime() >> { currentTimeMs }
    }

    def "schedules render at fixed rate once an root progress event is started"() {
        def event = startRoot("message")

        when:
        renderer.onOutput([event] as ArrayList<OutputEvent>)

        then:
        1 * executor.scheduleAtFixedRate(_, _, _, _)
    }

    def "forwards event list to listener"() {
        def event = event("message")

        when:
        renderer.onOutput([event] as ArrayList<OutputEvent>)

        then:
        1 * listener.onOutput([event] as ArrayList)
    }

    def "correctly format and set the text's label from the event"() {
        def event1 = startRoot('message')
        def event2 = event('2')

        when:
        renderer.onOutput([event1] as ArrayList<OutputEvent>)

        then:
        label.display == "message [0s]"

        when:
        currentTimeMs += 1000
        renderer.onOutput([event2] as ArrayList<OutputEvent>)

        then:
        label.display == "message [1s]"
    }

    def "correctly cancel the future once the end event is received"() {
        def startEvent = startRoot('message')
        def end = new EndOutputEvent()

        given:
        renderer.onOutput([startEvent] as ArrayList<OutputEvent>)

        when:
        renderer.onOutput([end] as ArrayList<OutputEvent>)

        then:
        1 * future.cancel(false)
    }

    def startRoot(String description) {
        start(parentId: null, category: BuildStatusRenderer.BUILD_PROGRESS_CATEGORY, shortDescription: description)
    }
}
