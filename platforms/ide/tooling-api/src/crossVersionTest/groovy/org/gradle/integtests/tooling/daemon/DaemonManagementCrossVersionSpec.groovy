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

package org.gradle.integtests.tooling.daemon

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.daemon.DaemonStatus
import org.gradle.tooling.daemon.GradleDaemon
import org.gradle.tooling.daemon.StopResult
import org.gradle.util.GradleVersion

/**
 * End-to-end coverage for the public {@code DaemonManagement} API against every
 * supported Gradle daemon version (5.0+). Spawns a real daemon of the target
 * version, then uses the current Tooling API consumer code to read that daemon's
 * registry — exercising the version-specific {@code DaemonContextReader}.
 */
@TargetGradleVersion(">=5.0")
@ToolingApiVersion(">=9.6")
@Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "needs a real daemon JVM")
class DaemonManagementCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        // Force daemonBaseDir to live under gradleUserHomeDir/daemon, which is what
        // our DaemonManagement API expects. requireIsolatedDaemons() respects this
        // by writing daemon registries into <userHome>/daemon/<version>/registry.bin.
        toolingApi.requireIsolatedUserHome()
        toolingApi.requireIsolatedDaemons()
        settingsFile << "rootProject.name = 'daemon-management-test'"
    }

    def "lists the daemon spawned by a build of the target Gradle version"() {
        given:
        runBuild()

        when:
        def daemons = newDaemonManagement().listDaemons()

        then:
        daemons.size() >= 1
        def target = daemons.find { it.gradleVersion == targetGradleVersion }
        target != null
        target.pid > 0
        target.status in [DaemonStatus.IDLE, DaemonStatus.BUSY]
        target.javaHome != null
        target.idleTimeout != null
        !target.jvmArguments.empty
    }

    def "javaMajorVersion is populated for 8.8+ daemons and null otherwise"() {
        given:
        runBuild()

        when:
        def daemon = newDaemonManagement().listDaemons().find { it.gradleVersion == targetGradleVersion }

        then:
        daemon != null
        if (atLeast("8.8")) {
            assert daemon.javaMajorVersion != null
            assert daemon.javaMajorVersion >= 8
        } else {
            assert daemon.javaMajorVersion == null
        }
    }

    def "javaVendor is populated for 8.10+ daemons and null otherwise"() {
        given:
        runBuild()

        when:
        def daemon = newDaemonManagement().listDaemons().find { it.gradleVersion == targetGradleVersion }

        then:
        daemon != null
        if (atLeast("8.10")) {
            assert daemon.javaVendor != null
            assert !daemon.javaVendor.empty
        } else {
            assert daemon.javaVendor == null
        }
    }

    def "listDaemons(version) returns only that version's daemons"() {
        given:
        runBuild()

        expect:
        def filtered = newDaemonManagement().listDaemons(targetGradleVersion)
        !filtered.empty
        filtered.every { it.gradleVersion == targetGradleVersion }
    }

    def "stopByPid stops the daemon"() {
        given:
        runBuild()
        def management = newDaemonManagement()
        GradleDaemon daemon = management.listDaemons().find { it.gradleVersion == targetGradleVersion }
        assert daemon != null

        when:
        StopResult result = management.stopByPid(daemon.pid)

        then:
        result == StopResult.STOPPED

        and:
        // The daemon may not be removed from the registry immediately, but the process
        // must be dead. Poll the OS for liveness.
        waitForProcessExit(daemon.pid)
    }

    def "stopByPid returns NOT_FOUND for unknown pid"() {
        expect:
        newDaemonManagement().stopByPid(999_999_999L) == StopResult.NOT_FOUND
    }

    private void runBuild() {
        withConnection { connection ->
            connection.newBuild().forTasks("help").run()
        }
    }

    private org.gradle.tooling.daemon.DaemonManagement newDaemonManagement() {
        // Construct an independent connector pointed at the same Gradle user home /
        // daemon base dir the test fixture used, so we read the right registry file.
        GradleConnector connector = GradleConnector.newConnector()
        connector.useGradleUserHomeDir(toolingApi.gradleUserHomeDir.absoluteFile)
        return connector.newDaemonManagement()
    }

    private String getTargetGradleVersion() {
        return targetDist.version.version
    }

    private boolean atLeast(String version) {
        return GradleVersion.version(targetGradleVersion).baseVersion >= GradleVersion.version(version)
    }

    private static void waitForProcessExit(long pid) {
        long deadline = System.currentTimeMillis() + 30_000L
        while (System.currentTimeMillis() < deadline) {
            if (!isProcessAlive(pid)) {
                return
            }
            Thread.sleep(200)
        }
        throw new AssertionError("Process ${pid} still alive after 30s")
    }

    private static boolean isProcessAlive(long pid) {
        try {
            // ProcessHandle is JDK 9+. The cross-version test fixture itself runs on JDK 11+.
            def processHandleClass = Class.forName("java.lang.ProcessHandle")
            def optional = processHandleClass.getMethod("of", long.class).invoke(null, pid)
            if (!optional.isPresent()) {
                return false
            }
            return processHandleClass.getMethod("isAlive").invoke(optional.get()) as Boolean
        } catch (Exception ignored) {
            return true
        }
    }
}
