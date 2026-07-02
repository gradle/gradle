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

package org.gradle.internal.operations.trace

import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingFile
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec

/**
 * Tests that build operations are emitted into an active (user-managed) JFR recording.
 */
class BuildOperationJfrEmitterIntegrationTest extends DaemonIntegrationSpec {

    def "emits build operation events into an active JFR recording"() {
        given:
        def jfr = startJfrRecording()
        enableBuildOperationEmission()

        when:
        succeeds("help")
        stopDaemonAndWait()

        then:
        def buildOps = buildOperationEvents(jfr)
        buildOps.any { it.getString("displayName") == "Run build" }
        buildOps.every { it.getLong("operationId") != 0L }
        buildOps.every { it.getInstant("gradleStartTime").toEpochMilli() > 0 }
        buildOps.every { it.getInstant("gradleEndTime").toEpochMilli() > 0 }
    }

    def "JFR build operation events agree with the BuildOperationTrace"() {
        given:
        def operations = newBuildOperationsFixture()
        def jfr = startJfrRecording()
        enableBuildOperationEmission()

        when:
        succeeds("help")
        stopDaemonAndWait()

        then:
        def jfrEventsById = buildOperationEvents(jfr).collectEntries { [(it.getLong("operationId")): it] }
        def recordsById = operations.records.collectEntries { [(it.id): it] }
        !jfrEventsById.isEmpty()

        and: "JFR never records an operation that the BuildOperationTrace did not"
        // JFR only captures operations that ran while a recording was active, so it is a subset of the trace.
        (jfrEventsById.keySet() - recordsById.keySet()).isEmpty()

        and: "JFR and the BuildOperationTrace agree on every shared field of every operation they both recorded"
        jfrEventsById.each { id, event ->
            def record = recordsById[id]
            assert event.getString("displayName") == record.displayName
            assert event.getLong("parentId") == (record.parentId == null ? 0L : record.parentId)
            assert event.getInstant("gradleStartTime").toEpochMilli() == record.startTime
            assert event.getInstant("gradleEndTime").toEpochMilli() == record.endTime
        }
    }

    def "emits nothing when the feature is not enabled"() {
        given:
        // An active recording, but the emission flag is left off.
        def jfr = startJfrRecording()

        when:
        succeeds("help")
        stopDaemonAndWait()

        then:
        buildOperationEvents(jfr).isEmpty()
    }

    private void enableBuildOperationEmission() {
        executer.withArgument("-D${BuildOperationJfrEmitter.SYSPROP}=true")
    }

    private File startJfrRecording() {
        def jfrFile = file("build.jfr")
        // Ask the daemon JVM to dump the recording to a known path on exit.
        // withBuildJvmOpts appends to the args already set by GRADLE_OPTS so the
        // flag actually reaches the daemon JVM (not just the client).
        executer.withBuildJvmOpts("-XX:StartFlightRecording=filename=${jfrFile.absolutePath},dumponexit=true")
        jfrFile
    }

    private static List<RecordedEvent> buildOperationEvents(File jfrFile) {
        RecordingFile.readAllEvents(jfrFile.toPath()).findAll { RecordedEvent event ->
            event.eventType.name == "org.gradle.BuildOperation"
        }
    }
}
