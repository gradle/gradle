/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.RenderableOutputEvent
import org.gradle.testing.internal.util.Specification

class GroupedLogEventDispatcherTest extends Specification {
    OutputEventListener stdoutChain = Mock(OutputEventListener)
    OutputEventListener stderrChain = Mock(OutputEventListener)
    GroupedLogEventDispatcher dispatcher = new GroupedLogEventDispatcher(stdoutChain, stderrChain)

    def "dispatches grouped log events to stdout"() {
        when:
        dispatcher.onOutput(event(LogLevel.INFO, true))

        then:
        1 * stdoutChain.onOutput(_)
        0 * stderrChain.onOutput(_)

        when:
        dispatcher.onOutput(event(LogLevel.ERROR, true))

        then:
        1 * stdoutChain.onOutput(_)
        0 * stderrChain.onOutput(_)

        when:
        dispatcher.onOutput(event(null, true))

        then:
        1 * stdoutChain.onOutput(_)
        0 * stderrChain.onOutput(_)
    }

    def "dispatches ungrouped non-error log events to stdout"() {
        when:
        dispatcher.onOutput(event(logLevel, false))

        then:
        1 * stdoutChain.onOutput(_)
        0 * stderrChain.onOutput(_)

        where:
        logLevel << LogLevel.values() - LogLevel.ERROR
    }

    def "dispatches ungrouped error log events to stderr"() {
        when:
        dispatcher.onOutput(event(LogLevel.ERROR, false))

        then:
        0 * stdoutChain.onOutput(_)
        1 * stderrChain.onOutput(_)
    }

    def "dispatches ungrouped log events with no log level to both stdout and stderr"() {
        when:
        dispatcher.onOutput(event(null, false))

        then:
        1 * stdoutChain.onOutput(_)
        1 * stderrChain.onOutput(_)
    }

    RenderableOutputEvent event(LogLevel logLevel, boolean isGrouped) {
        RenderableOutputEvent event = Mock(RenderableOutputEvent)
        event.logLevel >> logLevel
        event.grouped >> isGrouped
        return event
    }
}
