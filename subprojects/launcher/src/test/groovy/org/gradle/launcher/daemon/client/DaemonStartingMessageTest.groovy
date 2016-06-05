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

package org.gradle.launcher.daemon.client

import com.google.common.collect.Lists
import org.gradle.launcher.daemon.registry.DaemonStopEvent
import spock.lang.Specification
import spock.lang.Unroll

class DaemonStartingMessageTest extends Specification {
    @Unroll
    def "starting message contains number of busy and incompatible daemons (#numBusy busy, #numIncompatible incompatible)"() {
        given:
        def message = DaemonStartingMessage.generate(numBusy, numIncompatible, Lists.newArrayList())

        expect:
        message.contains(DaemonStartingMessage.STARTING_DAEMON_MESSAGE)
        messages.each { assert message.contains(it) }

        where:
        numBusy | numIncompatible | messages
        0       | 1               | [DaemonStartingMessage.ONE_INCOMPATIBLE_DAEMON_MESSAGE]
        1       | 0               | [DaemonStartingMessage.ONE_BUSY_DAEMON_MESSAGE]
        1       | 1               | [DaemonStartingMessage.ONE_BUSY_DAEMON_MESSAGE, DaemonStartingMessage.ONE_INCOMPATIBLE_DAEMON_MESSAGE]
        1       | 2               | [DaemonStartingMessage.ONE_BUSY_DAEMON_MESSAGE, DaemonStartingMessage.MULTIPLE_INCOMPATIBLE_DAEMONS_MESSAGE]
        2       | 1               | [DaemonStartingMessage.MULTIPLE_BUSY_DAEMONS_MESSAGE, DaemonStartingMessage.ONE_INCOMPATIBLE_DAEMON_MESSAGE]
        2       | 2               | [DaemonStartingMessage.MULTIPLE_BUSY_DAEMONS_MESSAGE, DaemonStartingMessage.MULTIPLE_INCOMPATIBLE_DAEMONS_MESSAGE]
    }

    def "starting message contains stoppage reasons"() {
        given:
        def stopEvent = new DaemonStopEvent(new Date(System.currentTimeMillis()), "REASON")
        def stopEvent2 = new DaemonStopEvent(new Date(System.currentTimeMillis()), "OTHER_REASON")
        def message = DaemonStartingMessage.generate(0, 0, Lists.newArrayList(stopEvent, stopEvent2))

        expect:
        message.contains(DaemonStartingMessage.STARTING_DAEMON_MESSAGE)
        message.contains(DaemonStartingMessage.ONE_DAEMON_STOPPED_PREFIX + "REASON")
        message.contains(DaemonStartingMessage.ONE_DAEMON_STOPPED_PREFIX + "OTHER_REASON")
    }
}
