/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests.fixtures.daemon

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

import java.util.concurrent.TimeUnit

@Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "explicitly requests a daemon")
abstract class DaemonIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        executer.requireDaemon()
        executer.requireIsolatedDaemons()
    }

    void stopDaemonsNow() {
        result = executer.withArguments("--stop", "--info").run()
    }

    /**
     * Stops the daemon and waits for its JVM to actually exit, so that shutdown hooks have run.
     * Useful when a test relies on side effects that only happen on a graceful JVM shutdown, such
     * as a JFR recording dumped via {@code -XX:StartFlightRecording=...,dumponexit=true}.
     */
    void stopDaemonAndWait() {
        def daemonPid = daemons.daemon.context.pid
        stopDaemonsNow()
        waitForProcessExit(daemonPid)
    }

    private static void waitForProcessExit(long pid) {
        def handle = ProcessHandle.of(pid)
        if (!handle.isPresent()) {
            // process is already dead, no need to wait
            return
        }
        // waiting with a 10 second timeout
        handle.get().onExit().get(10, TimeUnit.SECONDS)
    }

    void buildSucceeds() {
        result = executer.withArguments("--info").run()
    }

    DaemonsFixture getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }

    DaemonsFixture daemons(String gradleVersion) {
        new DaemonLogsAnalyzer(executer.daemonBaseDir, gradleVersion)
    }

    GradleHandle startAForegroundDaemon() {
        int currentSize = daemons.getRegistry().getAll().size()
        def daemon = executer.withArgument("--foreground").start()
        // Wait for foreground daemon to be ready
        ConcurrentTestUtil.poll() { assert daemons.getRegistry().getAll().size() == (currentSize + 1) }
        return daemon
    }
}
