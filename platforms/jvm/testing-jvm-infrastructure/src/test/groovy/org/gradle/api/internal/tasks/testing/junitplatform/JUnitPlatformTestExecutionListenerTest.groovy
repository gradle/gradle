/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform

import org.gradle.api.internal.tasks.testing.DefaultTestFileAttachmentDataEvent
import org.gradle.api.internal.tasks.testing.DefaultTestKeyValueDataEvent
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.time.Clock
import org.gradle.internal.time.FixedClock
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Unit tests for {@link JUnitPlatformTestExecutionListener}.
 */
class JUnitPlatformTestExecutionListenerTest extends Specification {
    def "published events use timestamp from junit ReportEntry, not from clock in execution listener"() {
        given:
        long startTime = 100L // Arbitrary time long, long ago
        Clock testClock = FixedClock.createAt(startTime)
        String id = "[test:1]"
        IdGenerator<String> idGenerator = { id } as IdGenerator
        TestDescriptor mockParentTestDescriptor = Mock(TestDescriptor) {
            getUniqueId() >> UniqueId.parse("[test:0]")
            getSource() >> Optional.empty()
            getParent() >> Optional.empty()
            getType() >> TestDescriptor.Type.TEST
            getTags() >> []
        }
        TestDescriptor mockTestDescriptor = Mock(TestDescriptor) {
            getUniqueId() >> UniqueId.parse(idGenerator.generateId())
            getSource() >> Optional.empty()
            getParent() >> Optional.of(mockParentTestDescriptor)
            getType() >> TestDescriptor.Type.TEST
            getTags() >> []
        }
        TestIdentifier testIdentifier = TestIdentifier.from(mockTestDescriptor)
        TestPlan mockTestPlan = Mock()
        TestResultProcessor mockResultProcessor = Mock()
        File workingDir = new File("working-dir")

        when:
        def recentTime = Instant.now().minusSeconds(600)
        ReportEntry entry = ReportEntry.from("key", "value")
        FileEntry fileEntry = FileEntry.from(workingDir.toPath(), "application/directory")
        FileEntry nonExistent = FileEntry.from(Path.of("does-not-exist"), "text/plain")

        then:
        testClock.currentTime == startTime

        when:
        JUnitPlatformTestExecutionListener listener = new JUnitPlatformTestExecutionListener(mockResultProcessor, testClock, idGenerator, workingDir)
        listener.testPlanExecutionStarted(mockTestPlan)
        listener.executionStarted(testIdentifier)
        listener.reportingEntryPublished(testIdentifier, entry)
        listener.fileEntryPublished(testIdentifier, fileEntry)
        listener.fileEntryPublished(testIdentifier, nonExistent)

        then:
        1 * mockResultProcessor.published(id) { e ->
            assert e instanceof DefaultTestKeyValueDataEvent
            assert e.values.size() == 1
            assert e.values["key"] == "value"
            assert recentTime.isBefore(e.logTime)
        }
        and:
        1 * mockResultProcessor.published(id) { e ->
            assert e instanceof DefaultTestFileAttachmentDataEvent
            assert e.path.isAbsolute()
            assert Files.isSameFile(e.path, workingDir.toPath().toAbsolutePath())
            assert e.mediaType == "application/directory"
            assert recentTime.isBefore(e.logTime)
        }
        and:
        1 * mockResultProcessor.published(id) { e ->
            assert e instanceof DefaultTestFileAttachmentDataEvent
            assert e.path.isAbsolute()
            assert !Files.exists(e.path)
            assert Files.isSameFile(e.path, nonExistent.path.toAbsolutePath())
            assert e.mediaType == "text/plain"
            assert recentTime.isBefore(e.logTime)
        }
    }
}
