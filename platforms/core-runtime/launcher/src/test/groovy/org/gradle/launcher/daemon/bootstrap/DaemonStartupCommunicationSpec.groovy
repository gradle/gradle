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

import org.gradle.internal.remote.internal.inet.MultiChoiceAddress
import spock.lang.Specification

class DaemonStartupCommunicationSpec extends Specification {
    def comm = new DaemonStartupCommunication()
    def dummyFile = new File("C:\\foo;;\\daemon-123.log\n\r\n\u03b1")
    def uuid = UUID.randomUUID()
    def addresses = [InetAddress.getByName(null)]

    def "can simply communicate diagnostics"() {
        when:
        def message = message(123, "1234", uuid, 123, addresses, dummyFile)
        def startupInfo = comm.readDiagnostics(message)

        then:
        startupInfo.uid == "1234"
        startupInfo.address.canonicalAddress == uuid
        startupInfo.address.port == 123
        startupInfo.address.candidates == addresses
        startupInfo.pid == 123
        startupInfo.diagnostics.pid == 123
        startupInfo.diagnostics.daemonLog == dummyFile
    }

    def "null pid is supported"() {
        when:
        def message = message(null, "1234", uuid, 123, addresses, dummyFile)
        def startupInfo = comm.readDiagnostics(message)

        then:
        startupInfo.diagnostics.pid == null
    }

    def "knows if a message contains a greeting"() {
        expect:
        !comm.containsGreeting("foo")
        comm.containsGreeting(message(null, "id", uuid, 123, addresses, new File("foo")))

        when:
        comm.containsGreeting(null)

        then:
        thrown(IllegalArgumentException)
    }

    def message(Long pid, String daemonId, UUID addressId, int port, List<InetAddress> addresses, File logFile) {
        def outputStream = new ByteArrayOutputStream()
        def printStream = new PrintStream(outputStream)
        def address = new MultiChoiceAddress(addressId, port, addresses)
        comm.printDaemonStarted(printStream, pid, daemonId, address, logFile)
        return new String(outputStream.toByteArray())
    }
}
