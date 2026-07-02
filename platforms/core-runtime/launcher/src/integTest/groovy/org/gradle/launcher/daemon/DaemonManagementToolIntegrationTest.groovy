/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.ConcurrentTestUtil

/**
 * Exercises the standalone {@code daemon-management-tool} binary that ships in the distribution's {@code bin/}
 * directory, driving real daemons started by the same distribution. The tool is run as an external process
 * (no compile dependency, which would introduce a cycle) and pointed at the test's daemon registry via
 * {@code --registry-dir}.
 */
class DaemonManagementToolIntegrationTest extends DaemonIntegrationSpec {

    private String runTool(String... args) {
        def scriptName = OperatingSystem.current().windows ? "daemon-management-tool.bat" : "daemon-management-tool"
        def executable = buildContext.gradleHomeDir.file("bin/${scriptName}")
        assert executable.exists()
        def command = [
            executable.absolutePath,
            "--gradle-user-home", executer.gradleUserHomeDir.absolutePath,
            "--registry-dir", executer.daemonBaseDir.absolutePath
        ]
        command.addAll(args)
        def process = new ProcessBuilder(command).redirectErrorStream(true).start()
        def output = process.inputStream.getText("UTF-8")
        process.waitFor()
        return output
    }

    def "reports no daemons when none are running"() {
        expect:
        runTool("list").contains("No Gradle daemons are running")
    }

    def "lists a running daemon and stops it"() {
        given:
        executer.withTasks("help").run()
        daemons.daemon.assertIdle()

        when:
        def listed = runTool("list")

        then:
        listed.contains(daemons.daemon.context.pid.toString())
        listed.contains("IDLE")

        when:
        def stopped = runTool("stop")

        then:
        stopped.contains("Daemon stopped")
        ConcurrentTestUtil.poll {
            assert runTool("list").contains("No Gradle daemons are running")
        }
    }

    def "stop-when-idle stops an idle daemon"() {
        given:
        executer.withTasks("help").run()
        daemons.daemon.assertIdle()

        when:
        def output = runTool("stop-when-idle")

        then:
        output.contains("Gradle daemon stopped")
        ConcurrentTestUtil.poll {
            assert runTool("list").contains("No Gradle daemons are running")
        }
    }
}
