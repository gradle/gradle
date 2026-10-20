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
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEventListener
import spock.lang.Subject

@Subject(BuildLogLevelFilterRenderer)
public class BuildLogLevelFilterRendererTest extends OutputSpecification {
    def listener = Mock(OutputEventListener)
    def renderer = new BuildLogLevelFilterRenderer(listener)

    def "consume correctly LogLevelChangeEvent"() {
        when:
        renderer.onOutput(new LogLevelChangeEvent(LogLevel.WARN))
        renderer.onOutput(event("lifecycle", LogLevel.LIFECYCLE))
        renderer.onOutput(event("warn", LogLevel.WARN))

        then:
        1 * listener.onOutput({it instanceof LogLevelChangeEvent})
        1 * listener.onOutput({it instanceof LogEvent && it.message == "warn"})
        0 * _
    }

    def "default to LogLevel.LIFECYCLE log level"() {
        when:
        renderer.onOutput(event("lifecycle", LogLevel.LIFECYCLE))
        renderer.onOutput(event("debug", LogLevel.DEBUG))

        then:
        1 * listener.onOutput({it.message == "lifecycle"})
        0 * _
    }

    def "forward the event unmodified to the listener"() {
        given:
        def event = event("event", LogLevel.LIFECYCLE)

        when:
        renderer.onOutput(event)

        then:
        1 * listener.onOutput(event)
        0 * _
    }
}
