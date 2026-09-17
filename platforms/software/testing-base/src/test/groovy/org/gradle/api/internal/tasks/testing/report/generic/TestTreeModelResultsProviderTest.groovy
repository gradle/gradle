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

package org.gradle.api.internal.tasks.testing.report.generic

import org.gradle.api.internal.tasks.testing.DecoratingTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class TestTreeModelResultsProviderTest extends Specification {

    @TempDir
    Path tempDir

    def "closes the output events file once the results are no longer in use"() {
        given:
        def storeDir = writeStore()

        when:
        TestTreeModelResultsProvider captured = null
        long classId = -1
        def outputWhileInUse = new StringWriter()
        TestTreeModelResultsProvider.useResultsFrom(storeDir) { provider ->
            captured = provider
            provider.visitClasses { classId = it.id }
            provider.writeAllOutput(classId, TestOutputEvent.Destination.StdOut, outputWhileInUse)
        }

        then:
        outputWhileInUse.toString() == "hello"

        when:
        captured.writeAllOutput(classId, TestOutputEvent.Destination.StdOut, new StringWriter())

        then:
        thrown(IllegalStateException)
    }

    private Path writeStore() {
        def storeDir = tempDir.resolve("store")
        def writer = new SerializableTestResultStore(storeDir).openWriter(0)
        try {
            def root = new DefaultTestSuiteDescriptor(1, "root")
            def testA = new DecoratingTestDescriptor(new DefaultTestDescriptor(2, "com.example.MySuite", "testA"), root)

            writer.started(root, new TestStartEvent(100))
            writer.started(testA, new TestStartEvent(100))
            writer.output(testA, new DefaultTestOutputEvent(100, TestOutputEvent.Destination.StdOut, "hello"))
            writer.completed(testA, successResult(100, 200), new TestCompleteEvent(200))
            writer.completed(root, successResult(100, 200), new TestCompleteEvent(200))
        } finally {
            writer.close()
        }
        return storeDir
    }

    private static TestResult successResult(long startTime, long endTime) {
        return new DefaultTestResult(TestResult.ResultType.SUCCESS, startTime, endTime, 1, 1, 0, [], null)
    }
}
