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

package org.gradle.internal.observability

import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingFile
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Requires(UnitTestPreconditions.Jdk11OrLater)
class BuildOperationJfrIntegrationTest extends DaemonIntegrationSpec {

    def "emits JFR build operation events during a build"() {
        given:
        def jfrFile = file("build.jfr")
        // Ask the daemon JVM to dump the recording to a known path on exit.
        // withBuildJvmOpts appends to the args already set by GRADLE_OPTS so the
        // flag actually reaches the daemon JVM (not just the client).
        executer.withBuildJvmOpts("-XX:StartFlightRecording=filename=${jfrFile.absolutePath},dumponexit=true")

        when:
        succeeds("help")
        // --stop triggers a graceful JVM shutdown (runs shutdown hooks), which causes
        // the JVM to honour dumponexit and write the recording before exiting.
        def daemonPid = daemons.daemon.context.pid
        stopDaemonsNow()
        waitForProcessExit(daemonPid)

        then:
        jfrFile.exists()

        and:
        def events = buildOperationEvents(jfrFile.toPath())
        !events.isEmpty()

        and:
        events.any { it.getString("displayName") == "Run build" }

        and:
        events.every { it.getLong("operationId") != 0L }
    }

    def "JFR events record correct parent-child relationships"() {
        given:
        def jfrFile = file("build.jfr")
        executer.withBuildJvmOpts("-XX:StartFlightRecording=filename=${jfrFile.absolutePath},dumponexit=true")

        when:
        succeeds("help")
        def daemonPid = daemons.daemon.context.pid
        stopDaemonsNow()
        waitForProcessExit(daemonPid)

        then:
        def events = buildOperationEvents(jfrFile.toPath())

        // There must be at least one root operation (parentId == 0)
        events.any { it.getLong("parentId") == 0L }

        and:
        // Every child's parentId must point to an existing operation in the same recording
        def operationIds = events.collect { it.getLong("operationId") } as Set
        events.findAll { it.getLong("parentId") != 0L }.every { child ->
            operationIds.contains(child.getLong("parentId"))
        }
    }

    /** Waits up to 30 seconds for the OS process with the given PID to exit. */
    private static void waitForProcessExit(long pid) {
        def handle = ProcessHandle.of(pid)
        if (!handle.isPresent()) {
            return // already gone
        }
        handle.get().onExit().get(30, TimeUnit.SECONDS)
    }

    private static List<RecordedEvent> buildOperationEvents(Path jfrPath) {
        RecordingFile.readAllEvents(jfrPath).findAll { RecordedEvent event ->
            event.eventType.name == "org.gradle.BuildOperation"
        }
    }
}
