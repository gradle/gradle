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

import org.gradle.integtests.fixtures.OtherGradleVersionFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.DaemonGradleExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.util.GradleVersion

/**
 * Manages a daemon of a genuinely different, already released Gradle version.
 *
 * <p>The other daemon here predates everything this feature adds. It publishes no cross-version index,
 * it wrote its registry in whatever layout its own version used, and it has never heard of the commands
 * being sent to it. That is the case the design exists for, so it is worth one test against a real
 * distribution rather than a simulated one.
 */
class ReleasedVersionDaemonManagementIntegrationTest extends DaemonIntegrationSpec implements OtherGradleVersionFixture {

    GradleExecuter otherVersionExecuter

    def cleanup() {
        otherVersionExecuter?.cleanup()
    }

    def "lists and stops a daemon of another released Gradle version"() {
        given:
        def otherDaemon = startDaemonOfOtherVersion()

        when:
        def status = executer.withArguments("--status", "--all-versions").run().normalizedOutput

        then:
        status.contains(otherVersion.version.version)
        status.contains(otherDaemon.context.pid.toString())

        when:
        def stopped = executer.withArguments("--stop", "--all-versions").run().normalizedOutput

        then:
        stopped.contains("daemon stopped")
        otherDaemon.stops()
    }

    def "a daemon of another version is left alone without --all-versions"() {
        given:
        def otherDaemon = startDaemonOfOtherVersion()

        when:
        def status = executer.withArgument("--status").run().normalizedOutput

        then:
        !status.contains(otherDaemon.context.pid.toString())

        when:
        executer.withArgument("--stop").run()

        then:
        otherDaemon.assertIdle()
    }

    def "cancelling a build is refused for a version that cannot do it"() {
        given:
        def otherDaemon = startDaemonOfOtherVersion()

        when:
        def out = executer.withArguments("--cancel", otherDaemon.context.pid.toString()).run().normalizedOutput

        then:
        out.contains("Gradle ${otherVersion.version.version} cannot cancel a build from outside")

        cleanup:
        otherDaemon.kill()
    }

    /**
     * Runs a build with the other version, pointed at the same daemon directory this test's own client
     * uses, and returns a handle on the daemon it left behind.
     */
    private startDaemonOfOtherVersion() {
        otherVersionExecuter = new DaemonGradleExecuter(otherVersion, temporaryFolder, GradleVersion.version(otherVersion.version.version), buildContext)
            .withDaemonBaseDir(daemons.daemonBaseDir)
            .withDaemonIdleTimeoutSecs(300)
            .inDirectory(testDirectory)
            .withTasks("help")
        otherVersionExecuter.run()

        def otherDaemons = DaemonLogsAnalyzer.newAnalyzer(daemons.daemonBaseDir, otherVersion.version.version)
        assert otherDaemons.daemons.size() == 1
        otherDaemons.daemon
    }
}
