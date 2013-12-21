/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.bootstrap

import spock.lang.Specification

class DaemonStartupCommunicationSpec extends Specification {

    def comm = new DaemonStartupCommunication()

    def "can simply communicate diagnostics"() {
        given:
        def dummyFile = new File("C:\\foo;;\\daemon-123.log")

        when:
        def message = comm.daemonStartedMessage(123, dummyFile)
        def diagnostics = comm.readDiagnostics(message)

        then:
        diagnostics.pid == 123
        diagnostics.daemonLog == dummyFile
    }

    def "null pid is supported"() {
        given:
        def dummyFile = new File("C:\\foo;;\\daemon-123.log")

        when:
        def message = comm.daemonStartedMessage(null, dummyFile)
        def diagnostics = comm.readDiagnostics(message)

        then:
        diagnostics.pid == null
        diagnostics.daemonLog == dummyFile
    }

    def "knows if a message contains a greeting"() {
        expect:
        !comm.containsGreeting("foo")
        comm.containsGreeting(comm.daemonStartedMessage(null, new File("foo")))

        when:
        comm.containsGreeting(null)
        then:
        thrown(IllegalArgumentException)
    }
}
