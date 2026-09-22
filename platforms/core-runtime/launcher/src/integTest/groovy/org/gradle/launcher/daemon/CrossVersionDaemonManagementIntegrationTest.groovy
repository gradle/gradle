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
import org.gradle.launcher.daemon.client.ReportDaemonStatusClient
import org.gradle.launcher.daemon.management.DaemonIndexEntry
import org.gradle.launcher.daemon.management.internal.DaemonIndexStore
import org.gradle.util.GradleVersion

/**
 * Covers managing daemons across Gradle versions from the command line.
 */
class CrossVersionDaemonManagementIntegrationTest extends DaemonIntegrationSpec {

    def "a running daemon publishes an index entry and withdraws it when it stops"() {
        given:
        executer.run()

        when:
        def entries = indexStore().all

        then:
        entries.size() == 1
        entries[0].gradleVersion == GradleVersion.current().version
        entries[0].pid == daemons.daemon.context.pid
        entries[0].schemaVersion == DaemonIndexEntry.CURRENT_SCHEMA_VERSION
        entries[0].token.length == 16
        entries[0].port > 0
        !entries[0].addresses.empty

        when:
        executer.withArgument("--stop").run()
        daemons.daemon.stops()

        then:
        indexStore().all.empty
    }

    def "--status --all-versions lists the daemon with its version"() {
        given:
        executer.run()
        def pid = daemons.daemon.context.pid

        when:
        def out = executer.withArguments("--status", "--all-versions").run().normalizedOutput

        then:
        out.contains("PID")
        out.contains("VERSION")
        out.contains(pid.toString())
        out.contains(GradleVersion.current().version)
    }

    def "--status on its own is unchanged"() {
        given:
        executer.run()

        when:
        def out = executer.withArgument("--status").run().normalizedOutput

        then:
        out.contains(ReportDaemonStatusClient.STATUS_FOOTER)
        !out.contains("VERSION")
    }

    def "--stop --all-versions stops a running daemon"() {
        given:
        executer.run()

        when:
        def out = executer.withArguments("--stop", "--all-versions").run().normalizedOutput

        then:
        out.contains("1 daemon stopped.")
        daemons.daemon.stops()
        indexStore().all.empty
    }

    def "--stop-when-idle asks an idle daemon to stop"() {
        given:
        executer.run()

        when:
        def out = executer.withArgument("--stop-when-idle").run().normalizedOutput

        then:
        out.contains("will stop once it finishes what it is doing")
        daemons.daemon.stops()
    }

    def "--cancel reports when the daemon is not running a build"() {
        given:
        executer.run()
        def pid = daemons.daemon.context.pid

        when:
        def out = executer.withArguments("--cancel", pid.toString()).run().normalizedOutput

        then:
        out.contains("is not running a build")
    }

    def "--cancel reports an unknown process id"() {
        given:
        executer.run()

        when:
        def out = executer.withArguments("--cancel", "999999").run().normalizedOutput

        then:
        out.contains("No Gradle daemon is running with pid 999999.")
    }

    def "--cancel rejects a process id that is not a number"() {
        when:
        def out = executer.withArguments("--cancel", "not-a-pid").run().normalizedOutput

        then:
        out.contains("--cancel needs the process id of a daemon")
    }

    def "--start-daemon leaves an idle daemon behind"() {
        when:
        def out = executer.withArgument("--start-daemon").run().normalizedOutput

        then:
        out.contains("Started a Gradle ${GradleVersion.current().version} daemon with pid")
        daemons.daemons.size() == 1
        daemons.daemon.assertIdle()
        indexStore().all.size() == 1
    }

    def "a daemon started this way runs the next build"() {
        given:
        executer.withArgument("--start-daemon").run()
        def startedPid = daemons.daemon.context.pid

        when:
        executer.withTasks("help").run()

        then:
        daemons.daemons.size() == 1
        daemons.daemon.context.pid == startedPid
    }

    def "a stale index entry is reported as not responding and then cleaned up by --stop"() {
        given:
        executer.run()
        def entry = indexStore().all[0]
        executer.withArgument("--stop").run()
        daemons.daemon.stops()
        // Put the entry back, standing in for a daemon that was killed without cleaning up after itself.
        indexStore().store(entry)

        when:
        def status = executer.withArguments("--status", "--all-versions").run().normalizedOutput

        then:
        status.contains("not responding")

        when:
        executer.withArguments("--stop", "--all-versions").run()

        then:
        indexStore().all.empty
    }

    private DaemonIndexStore indexStore() {
        new DaemonIndexStore(daemons.daemonBaseDir)
    }
}
