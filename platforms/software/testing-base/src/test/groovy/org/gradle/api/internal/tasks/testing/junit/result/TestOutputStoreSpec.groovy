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

    def "output for class includes all events with the given test id"() {
        when:
        def writer = output.writer()
        writer.onOutput(1, output(StdOut, "[out-1]"))
        writer.onOutput(1, output(StdOut, "[out-2]"))
        writer.onOutput(2, output(StdErr, "[out-3]"))
        writer.onOutput(1, output(StdErr, "[out-4]"))
        writer.onOutput(1, output(StdOut, "[out-5]"))
        writer.onOutput(1, output(StdOut, "[out-6]"))
        writer.close()
        def reader = output.reader()

        then:
        collectOutput(reader, 1, StdOut) == "[out-1][out-2][out-5][out-6]"
        collectOutput(reader, 1, StdErr) == "[out-4]"
        collectOutput(reader, 2, StdErr) == "[out-3]"

        cleanup:
        reader.close()
    }

    DefaultTestOutputEvent output(TestOutputEvent.Destination destination, String msg) {
        new DefaultTestOutputEvent(System.currentTimeMillis(), destination, msg)
    }

    def "writes nothing for unknown test"() {
        when:
        def writer = output.writer()
        writer.close()
        def reader = output.reader()

        then:
        collectOutput(reader, 20, StdErr) == ""

        cleanup:
        reader.close()
    }

    def "can query whether output is available for a test"() {
        when:
        def writer = output.writer()
        writer.onOutput(1, output(StdOut, "[out]"))
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

    String collectOutput(TestOutputStore.Reader reader, long testId, TestOutputEvent.Destination destination) {
        def writer = new StringWriter()
        reader.copyOutput(testId, destination, writer)
        return writer.toString()
    }
}
