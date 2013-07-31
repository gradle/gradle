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

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut

class TestOutputStoreSpec extends Specification {
    @Rule
    private TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    private output = new TestOutputStore(temp.testDirectory)

    TestDescriptorInternal descriptor(String className, Object testId) {
        Stub(TestDescriptorInternal) {
            getClassName() >> className
            getId() >> testId
        }
    }

    def "flushes all output when output finishes"() {
        when:
        def writer = output.writer()
        writer.onOutput(11, descriptor("Class1", "method1"), StdOut, "[out]")
        writer.onOutput(21, descriptor("Class2", "method1"), StdErr, "[err]")
        writer.onOutput(11, descriptor("Class1", "method1"), StdErr, "[err]")
        writer.onOutput(11, descriptor("Class1", "method1"), StdOut, "[out2]")
        writer.finishOutputs()
        def reader = output.reader()

        then:
        collectOutput(reader, "Class1", StdOut) == "[out][out2]"
        collectOutput(reader, "Class1", StdErr) == "[err]"
        collectOutput(reader, "Class2", StdErr) == "[err]"
    }

    def "writes nothing for unknown test class"() {
        when:
        def writer = output.writer()
        writer.finishOutputs()

        then:
        def reader = output.reader()
        collectOutput(reader, "Unknown", StdErr) == ""
    }

    def "can query whether output is available for a test class"() {
        when:
        def writer = output.writer()
        writer.onOutput(11, descriptor("Class1", "method1"), StdOut, "[out]")
        writer.finishOutputs()
        def reader = output.reader()

        then:
        reader.hasOutput("Class1", StdOut)
        !reader.hasOutput("Class1", StdErr)
        !reader.hasOutput("Unknown", StdErr)
    }

    String collectOutput(TestOutputStore.Reader reader, String className, TestOutputEvent.Destination destination) {
        def writer = new StringWriter()
        reader.writeAllOutput(className, destination, writer)
        return writer.toString()
    }
}
