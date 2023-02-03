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

import org.gradle.api.GradleException
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.launcher.daemon.client.DaemonGreeter
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress
import org.gradle.process.ExecResult
import spock.lang.Specification

class DaemonGreeterTest extends Specification {

    def registry = Mock(DocumentationRegistry)
    def args = ["foo", "bar"]

    def "parses the process output"() {
        given:
        def address = new MultiChoiceAddress(UUID.randomUUID(), 123, [])

        def outputStream = new ByteArrayOutputStream()
        def printStream = new PrintStream(outputStream)
        printStream.print("""hey joe!
another line of output...
""")

        new DaemonStartupCommunication().printDaemonStarted(printStream, 12, "uid", address, new File("12.log"))
        def output = new String(outputStream.toByteArray())

        when:
        def daemonStartupInfo = new DaemonGreeter(registry).parseDaemonOutput(output, args)

        then:
        daemonStartupInfo.address == address
        daemonStartupInfo.uid == "uid"
        daemonStartupInfo.pid == 12
        daemonStartupInfo.diagnostics.pid == 12
        daemonStartupInfo.diagnostics.daemonLog == new File("12.log")
    }

    def "shouts if daemon did not start"() {
        given:
        def output = """hey joe!
another line of output..."""

        ExecResult result = Mock()

        when:
        new DaemonGreeter(registry).parseDaemonOutput(output, args);

        then:
        def ex = thrown(GradleException)
        ex.message.contains(DaemonMessages.UNABLE_TO_START_DAEMON)
        ex.message.concat("Process command line: [foo, bar]")
        ex.message.contains("hey joe!")
    }

    def "shouts if daemon broke completely..."() {
        when:
        new DaemonGreeter(registry).parseDaemonOutput("", args)

        then:
        def ex = thrown(GradleException)
        ex.message.contains(DaemonMessages.UNABLE_TO_START_DAEMON)
    }
}
