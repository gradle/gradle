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
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.process.ExecResult
import spock.lang.Specification

class DaemonGreeterTest extends Specification {

    DocumentationRegistry registry = Mock()

    def "parses the process output"() {
        given:
        def output = """hey joe!
another line of output...
${new DaemonStartupCommunication().daemonStartedMessage(12, new File("12.log"))}"""

        when:
        def diagnostics = new DaemonGreeter(registry).parseDaemonOutput(output, Mock(ExecResult))

        then:
        diagnostics.pid == 12
        diagnostics.daemonLog == new File("12.log")
    }

    def "shouts if daemon did not start"() {
        given:
        def output = """hey joe!
another line of output..."""

        ExecResult result = Mock()

        when:
        new DaemonGreeter(registry).parseDaemonOutput(output, result);

        then:
        def ex = thrown(GradleException)
        ex.message.contains(DaemonMessages.UNABLE_TO_START_DAEMON)
        ex.message.contains("hey joe!")
    }

    def "shouts if daemon broke completely..."() {
        when:
        new DaemonGreeter(registry).parseDaemonOutput("", Mock(ExecResult))

        then:
        def ex = thrown(GradleException)
        ex.message.contains(DaemonMessages.UNABLE_TO_START_DAEMON)
    }
}
