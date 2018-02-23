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
package org.gradle.internal.logging.services

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.StyledTextOutputEvent
import spock.lang.Specification

class TextStreamOutputEventListenerTest extends Specification {
    private final OutputEventListener target = Mock()
    private final TextStreamOutputEventListener listener = new TextStreamOutputEventListener(target)

    def attachesLogLevelToTextOutputEvent() {
        StyledTextOutputEvent event = Mock()
        StyledTextOutputEvent transformed = Mock()

        when:
        listener.onOutput(event)

        then:
        1 * event.withLogLevel(LogLevel.LIFECYCLE) >> transformed
        1 * target.onOutput(transformed)
        0 * target._
    }

    def doesNotChangeLogLevelWhenEventAlreadyHasALogLevel() {
        StyledTextOutputEvent event = Mock()

        when:
        listener.onOutput(event)

        then:
        1 * event.logLevel >> LogLevel.INFO
        1 * target.onOutput(event)
        0 * target._
    }

    def doesNotForwardLogLevelChangeEvents() {
        StyledTextOutputEvent event = Mock()
        StyledTextOutputEvent transformed = Mock()

        when:
        listener.onOutput(new LogLevelChangeEvent(LogLevel.ERROR))
        listener.onOutput(event)

        then:
        1 * event.withLogLevel(LogLevel.ERROR) >> transformed
        1 * target.onOutput(transformed)
        0 * target._
    }
}
