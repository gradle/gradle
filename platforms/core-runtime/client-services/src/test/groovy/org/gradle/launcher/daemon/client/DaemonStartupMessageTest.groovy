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

import spock.lang.Specification

class DaemonStartupMessageTest extends Specification {
    def "starting message contains number of busy and incompatible daemons (#numBusy busy, #numIncompatible incompatible, #numStopped stopped)"() {
        given:
        def message = DaemonStartupMessage.generate(numBusy, numIncompatible, numStopped)

        expect:
        message.contains(DaemonStartupMessage.STARTING_DAEMON_MESSAGE)
        message.contains(DaemonStartupMessage.NOT_REUSED_MESSAGE)
        !message.contains(DaemonStartupMessage.SUBSEQUENT_BUILDS_WILL_BE_FASTER)
        messages.each { assert message.contains(it) }

        where:
        numBusy | numIncompatible | numStopped | messages
        0       | 1               | 0          | ["1 incompatible"]
        1       | 0               | 0          | ["1 busy"]
        0       | 0               | 1          | ["1 stopped"]
        1       | 2               | 4          | ["1 busy", "2 incompatible", "4 stopped"]
    }

    def "starting message contains subsequent builds message given no unavailable daemons"() {
        given:
        def message = DaemonStartupMessage.generate(0, 0, 0)

        expect:
        message.contains(DaemonStartupMessage.STARTING_DAEMON_MESSAGE)
        message.contains(DaemonStartupMessage.SUBSEQUENT_BUILDS_WILL_BE_FASTER)
    }
}
