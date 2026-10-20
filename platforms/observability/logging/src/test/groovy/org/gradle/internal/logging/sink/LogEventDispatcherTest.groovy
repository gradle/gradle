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
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OutputEventListener
import spock.lang.Specification

class LogEventDispatcherTest extends Specification {
    OutputEventListener stdoutChain = Mock(OutputEventListener)
    OutputEventListener stderrChain = Mock(OutputEventListener)
    LogEventDispatcher dispatcher = new LogEventDispatcher(stdoutChain, stderrChain)

    def "QUIET and below are dispatched to the stdout chain"() {
        when:
        dispatcher.onOutput(event(logLevel))

        then:
        1 * stdoutChain.onOutput(_)
        0 * stderrChain.onOutput(_)

        where:
        logLevel << LogLevel.values() - LogLevel.ERROR
    }

    def "ERROR is dispatched to the stderr chain"() {
        when:
        dispatcher.onOutput(event(LogLevel.ERROR))

        then:
        0 * stdoutChain.onOutput(_)
        1 * stderrChain.onOutput(_)
    }

    def "event without log level is dispatched to both chains"() {
        when:
        dispatcher.onOutput(event(null))

        then:
        1 * stdoutChain.onOutput(_)
        1 * stderrChain.onOutput(_)
    }

    def "dispatches only to stdout when stderr is null"() {
        when:
        dispatcher = new LogEventDispatcher(stdoutChain, null)
        LogLevel.values().each { logLevel ->
            dispatcher.onOutput(event(logLevel))
        }

        then:
        5 * stdoutChain.onOutput(_) >> { args -> assert args[0].logLevel != LogLevel.ERROR }
        0 * stderrChain.onOutput(_)
    }

    def "dispatches only to stderr when stdout is null"() {
        when:
        dispatcher = new LogEventDispatcher(null, stderrChain)
        LogLevel.values().each { logLevel ->
            dispatcher.onOutput(event(logLevel))
        }

        then:
        0 * stdoutChain.onOutput(_)
        1 * stderrChain.onOutput(_) >> { args -> assert args[0].logLevel == LogLevel.ERROR }
    }

    LogEvent event(level) {
        LogEvent event = Stub(LogEvent)
        event.logLevel >> level
        return event
    }
}
