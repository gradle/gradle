/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.process.internal.worker.child

import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import spock.lang.Specification

class WorkerLogEventListenerTest extends Specification {
    WorkerLogEventListener listener = new WorkerLogEventListener()

    def "listener forwards requests to the configured logging protocol object"() {
        given:
        def protocol = Mock(WorkerLoggingProtocol)
        def logEvent = Mock(LogEvent)
        def styledTextOutputEvent = Mock(StyledTextOutputEvent)
        listener.setWorkerLoggingProtocol(protocol)

        when:
        listener.onOutput(logEvent)

        then:
        1 * protocol.sendOutputEvent(logEvent)

        when:
        listener.onOutput(styledTextOutputEvent)

        then:
        1 * protocol.sendOutputEvent(styledTextOutputEvent)
    }

    def "can forward events to a different logging protocol object temporarily"() {
        given:
        def protocol1 = Mock(WorkerLoggingProtocol)
        def protocol2 = Mock(WorkerLoggingProtocol)
        listener.setWorkerLoggingProtocol(protocol1)
        def logEvent1 = Mock(LogEvent)
        def logEvent2 = Mock(LogEvent)
        def logEvent3 = Mock(StyledTextOutputEvent)
        def logEvent4 = Mock(LogEvent)

        when:
        listener.onOutput(logEvent1)
        listener.withWorkerLoggingProtocol(protocol2) {
            listener.onOutput(logEvent2)
            listener.onOutput(logEvent3)
        }
        listener.onOutput(logEvent4)

        then:
        1 * protocol1.sendOutputEvent(logEvent1)
        1 * protocol2.sendOutputEvent(logEvent2)
        1 * protocol2.sendOutputEvent(logEvent3)
        1 * protocol1.sendOutputEvent(logEvent4)
    }

    def "cannot send events to listener before logging protocol is set"() {
        given:
        def logEvent = Mock(LogEvent)
        def protocol = Mock(WorkerLoggingProtocol)

        when:
        listener.onOutput(logEvent)

        then:
        thrown(IllegalStateException)

        when:
        listener.setWorkerLoggingProtocol(protocol)
        listener.onOutput(logEvent)

        then:
        1 * protocol.sendOutputEvent(logEvent)

        and:
        noExceptionThrown()
    }
}
