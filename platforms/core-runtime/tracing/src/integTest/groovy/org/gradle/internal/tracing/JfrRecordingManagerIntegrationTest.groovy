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

package org.gradle.internal.tracing

import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingFile
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec

/**
 * Tests the Gradle-managed JFR recording: starting/writing the recording and where the file lands.
 * Build-operation events come from the separate operations.jfr emitter, enabled alongside it.
 */
class JfrRecordingManagerIntegrationTest extends DaemonIntegrationSpec {

    private static final String EMIT_BUILD_OPS = "org.gradle.internal.operations.jfr"

    def "Gradle-managed recording captures build operation events and JVM events"() {
        given:
        enableManaged()
        emitBuildOps()

        when:
        succeeds("help")

        then:
        def events = singleRecording(testDirectory)
        def buildOps = buildOperationEvents(events)
        buildOps.any { it.getString("displayName") == "Run build" }
        buildOps.every { it.getLong("operationId") != 0L }

        and: "the default profile also captures JVM events"
        events.any { it.eventType.name.startsWith("jdk.GarbageCollection") }
    }

    def "records a separate JFR file per invocation"() {
        given:
        // Enable via gradle.properties: it is re-read every build, whereas a -D flag is applied to the
        // daemon JVM on the first build and not re-sent on reuse, so it would only arm the first build.
        file("gradle.properties") << """
            ${JfrRecordingManager.MANAGED_SYSPROP}=true
            ${EMIT_BUILD_OPS}=true
        """

        when:
        succeeds("help")
        succeeds("help")

        then:
        def recordings = jfrRecordingsIn(testDirectory)
        recordings.size() == 2

        and:
        recordings.every { buildOperationEvents(readEvents(it)).any { e -> e.getString("displayName") == "Run build" } }
    }

    def "can specify the managed recording output directory"() {
        given:
        def jfrDir = file("jfr-out")
        enableManaged()
        executer.withArgument("-D${JfrRecordingManager.DIR_SYSPROP}=${jfrDir.absolutePath}")

        when:
        succeeds("help")

        then:
        !jfrRecordingsIn(jfrDir).isEmpty()
        jfrRecordingsIn(testDirectory).isEmpty()
    }

    def "no recording is written when the managed feature is disabled"() {
        given:
        // Emission is on and the output dir is pointed somewhere, but the managed recording stays off.
        // Neither the dir option nor event emission alone may start a recording.
        emitBuildOps()
        executer.withArgument("-D${JfrRecordingManager.DIR_SYSPROP}=${file('jfr-out').absolutePath}")

        when:
        succeeds("help")

        then:
        jfrRecordingsIn(file("jfr-out")).isEmpty()
        jfrRecordingsIn(testDirectory).isEmpty()
    }

    def "managed recording agrees with the BuildOperationTrace"() {
        given:
        def operations = newBuildOperationsFixture()
        enableManaged()
        emitBuildOps()

        when:
        succeeds("help")

        then:
        def jfrEventsById = buildOperationEvents(singleRecording(testDirectory)).collectEntries { [(it.getLong("operationId")): it] }
        def recordsById = operations.records.collectEntries { [(it.id): it] }
        !jfrEventsById.isEmpty()

        and: "JFR never records an operation that the BuildOperationTrace did not"
        (jfrEventsById.keySet() - recordsById.keySet()).isEmpty()

        and: "JFR and the BuildOperationTrace agree on every shared field"
        jfrEventsById.each { id, event ->
            def record = recordsById[id]
            assert event.getString("displayName") == record.displayName
            assert event.getLong("parentId") == (record.parentId == null ? 0L : record.parentId)
            assert event.getInstant("gradleStartTime").toEpochMilli() == record.startTime
            assert event.getInstant("gradleEndTime").toEpochMilli() == record.endTime
        }
    }

    def "writes the recording to the root project directory when invoked from a subproject"() {
        given:
        settingsFile << "include 'sub'"
        def sub = file("sub").createDir()
        enableManaged()

        when:
        executer.inDirectory(sub)
        succeeds("help")

        then:
        !jfrRecordingsIn(testDirectory).isEmpty()

        and:
        jfrRecordingsIn(sub).isEmpty()
    }

    def "writes the recording to the invoked build's own root, not the outer project, for an included build"() {
        given:
        // A build-logic-style included build has its own settings, so invoking from within it makes IT
        // the build root; the recording follows the invoked build's root, not the outer project.
        settingsFile << "includeBuild 'included-build'"
        def included = file("included-build").createDir()
        included.file("settings.gradle") << ""
        enableManaged()

        when:
        executer.inDirectory(included)
        succeeds("help")

        then:
        !jfrRecordingsIn(included).isEmpty()

        and:
        jfrRecordingsIn(testDirectory).isEmpty()
    }

    def "Gradle-managed and user-managed recordings can operate simultaneously"() {
        given:
        // External recording: dumped by the JVM on exit.
        def externalJfr = file("external.jfr")
        executer.withBuildJvmOpts("-XX:StartFlightRecording=filename=${externalJfr.absolutePath},dumponexit=true")
        // Gradle-managed recording: flushed when the build session ends, written to its own dir
        // so it cannot be mistaken for the external file.
        def managedDir = file("jfr-out")
        enableManaged()
        emitBuildOps()
        executer.withArgument("-D${JfrRecordingManager.DIR_SYSPROP}=${managedDir.absolutePath}")

        when:
        succeeds("help")
        stopDaemonAndWait()

        then: "the Gradle-managed recording captured build-operation events"
        buildOperationEvents(singleRecording(managedDir)).any { it.getString("displayName") == "Run build" }

        and: "the external recording captured the same build-operation events in parallel"
        externalJfr.exists()
        buildOperationEvents(readEvents(externalJfr)).any { it.getString("displayName") == "Run build" }
    }

    private void enableManaged() {
        executer.withArgument("-D${JfrRecordingManager.MANAGED_SYSPROP}=true")
    }

    private void emitBuildOps() {
        executer.withArgument("-D${EMIT_BUILD_OPS}=true")
    }

    private static List<File> jfrRecordingsIn(File dir) {
        def files = dir.listFiles()
        files == null ? [] : files.findAll { it.name.endsWith(".jfr") }
    }

    private static List<RecordedEvent> readEvents(File jfrFile) {
        RecordingFile.readAllEvents(jfrFile.toPath())
    }

    private static List<RecordedEvent> singleRecording(File dir) {
        def recordings = jfrRecordingsIn(dir)
        assert recordings.size() == 1
        readEvents(recordings.first())
    }

    private static List<RecordedEvent> buildOperationEvents(List<RecordedEvent> events) {
        events.findAll { RecordedEvent event -> event.eventType.name == "org.gradle.BuildOperation" }
    }
}
