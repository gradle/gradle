/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.test.fixtures.file.WorkspaceTest

import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut

class TestOutputStoreSpec extends WorkspaceTest {

    private output = new TestOutputStore(testDirectory)

    TestDescriptorInternal descriptor(String className, Object testId) {
        Stub(TestDescriptorInternal) {
            getClassName() >> className
            getId() >> testId
        }
    }

    def "output for class includes all events with the given class id"() {
        when:
        def writer = output.writer()
        writer.onOutput(1, output(StdOut, "[out-1]"))
        writer.onOutput(1, 1, output(StdOut, "[out-2]"))
        writer.onOutput(2, 1, output(StdErr, "[out-3]"))
        writer.onOutput(1, 1, output(StdErr, "[out-4]"))
        writer.onOutput(1, 1, output(StdOut, "[out-5]"))
        writer.onOutput(1, 2, output(StdOut, "[out-6]"))
        writer.close()
        def reader = output.reader()

        then:
        collectAllOutput(reader, 1, StdOut) == "[out-1][out-2][out-5][out-6]"
        collectAllOutput(reader, 1, StdErr) == "[out-4]"
        collectAllOutput(reader, 2, StdErr) == "[out-3]"

        cleanup:
        reader.close()
    }

    def "output for test includes all events with the given class and method ids"() {
        when:
        def writer = output.writer()
        writer.onOutput(1, output(StdOut, "[out-1]"))
        writer.onOutput(1, 1, output(StdOut, "[out-2]"))
        writer.onOutput(2, 1, output(StdErr, "[out-3]"))
        writer.onOutput(1, 1, output(StdErr, "[out-4]"))
        writer.onOutput(1, 1, output(StdOut, "[out-5]"))
        writer.onOutput(1, 2, output(StdOut, "[out-6]"))
        writer.close()
        def reader = output.reader()

        then:
        collectOutput(reader, 1, 1, StdOut) == "[out-2][out-5]"
        collectOutput(reader, 1, 1, StdErr) == "[out-4]"
        collectOutput(reader, 1, 2, StdOut) == "[out-6]"

        cleanup:
        reader.close()
    }

    def "non-test output includes all events with the given class id and no method id"() {
        when:
        def writer = output.writer()
        writer.onOutput(1, output(StdOut, "[out-1]"))
        writer.onOutput(1, 1, output(StdOut, "[out-2]"))
        writer.onOutput(1, output(StdErr, "[out-3]"))
        writer.onOutput(1, output(StdErr, "[out-4]"))
        writer.onOutput(1, output(StdOut, "[out-5]"))
        writer.onOutput(1, 2, output(StdOut, "[out-6]"))
        writer.onOutput(2, output(StdOut, "[out-6]"))
        writer.close()
        def reader = output.reader()

        then:
        collectOutput(reader, 1, StdOut) == "[out-1][out-5]"
        collectOutput(reader, 1, StdErr) == "[out-3][out-4]"
        collectOutput(reader, 2, StdOut) == "[out-6]"

        cleanup:
        reader.close()
    }

    def DefaultTestOutputEvent output(TestOutputEvent.Destination destination, String msg) {
        new DefaultTestOutputEvent(destination, msg)
    }

    def "writes nothing for unknown test class"() {
        when:
        def writer = output.writer()
        writer.close()
        def reader = output.reader()

        then:
        collectAllOutput(reader, 20, StdErr) == ""

        cleanup:
        reader.close()
    }

    def "writes nothing for unknown test method"() {
        when:
        def writer = output.writer()
        writer.onOutput(1, 1, output(StdOut, "[out]"))
        writer.close()
        def reader = output.reader()

        then:
        collectOutput(reader, 1, 10, StdOut) == ""

        cleanup:
        reader.close()
    }

    def "can query whether output is available for a test class"() {
        when:
        def writer = output.writer()
        writer.onOutput(1, 1, output(StdOut, "[out]"))
        writer.close()
        def reader = output.reader()

        then:
        reader.hasOutput(1, StdOut)
        !reader.hasOutput(1, StdErr)
        !reader.hasOutput(2, StdErr)

        cleanup:
        reader.close()
    }

    def "can open empty reader"() {
        // neither file
        expect:
        output.reader().close() // no exception
    }

    def "exception if no output file"() {
        when:
        output.indexFile.createNewFile()
        output.reader()

        then:
        thrown(IllegalStateException)
    }

    def "exception if no index file, but index"() {
        when:
        output.outputsFile.createNewFile()
        output.reader()

        then:
        thrown(IllegalStateException)
    }

    String collectAllOutput(TestOutputStore.Reader reader, long classId, TestOutputEvent.Destination destination) {
        def writer = new StringWriter()
        reader.writeAllOutput(classId, destination, writer)
        return writer.toString()
    }

    String collectOutput(TestOutputStore.Reader reader, long classId, long testId, TestOutputEvent.Destination destination) {
        def writer = new StringWriter()
        reader.writeTestOutput(classId, testId, destination, writer)
        return writer.toString()
    }

    String collectOutput(TestOutputStore.Reader reader, long classId, TestOutputEvent.Destination destination) {
        def writer = new StringWriter()
        reader.copyTestOutput(classId, destination, writer)
        return writer.toString()
    }
}
