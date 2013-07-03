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

class PersistedTestOutputSpec extends Specification {
    @Rule
    private TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    private output = new TestOutputStore(temp.testDirectory)
    private writer = output.writer()
    private reader = output.reader()

    TestDescriptorInternal descriptor(String className, String testName) {
        Stub(TestDescriptorInternal) {
            getClassName() >> className
            getName() >> testName
        }
    }

    def "flushes all output when output finishes"() {
        when:
        writer.onOutput(descriptor("Class1", "method1"), StdOut, "[out]")
        writer.onOutput(descriptor("Class2", "method1"), StdErr, "[err]")
        writer.onOutput(descriptor("Class1", "method1"), StdErr, "[err]")
        writer.onOutput(descriptor("Class1", "method1"), StdOut, "[out2]")
        writer.finishOutputs()

        then:
        collectOutput("Class1", StdOut) == "[out][out2]"
        collectOutput("Class1", StdErr) == "[err]"
        collectOutput("Class2", StdErr) == "[err]"
    }

    def "writes nothing for unknown test class"() {
        when:
        writer.finishOutputs()

        then:
        collectOutput("Unknown", StdErr) == ""
    }

    def "can query whether output is available for a test class"() {
        when:
        writer.onOutput(descriptor("Class1", "method1"), StdOut, "[out]")
        writer.finishOutputs()

        then:
        reader.hasOutput("Class1", StdOut)
        !reader.hasOutput("Class1", StdErr)
        !reader.hasOutput("Unknown", StdErr)
    }

    String collectOutput(String className, TestOutputEvent.Destination destination) {
        def writer = new StringWriter()
        reader.readTo(className, destination, writer)
        return writer.toString()
    }
}
