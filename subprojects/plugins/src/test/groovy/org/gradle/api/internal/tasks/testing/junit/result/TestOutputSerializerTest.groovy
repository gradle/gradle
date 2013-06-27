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

import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut

/**
 * by Szczepan Faber, created at: 11/19/12
 */
class TestOutputSerializerTest extends Specification {
    @Rule
    private TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    private serializer = new TestOutputSerializer(temp.testDirectory)

    def "flushes all output when output finishes"() {
        when:
        serializer.onOutput("Class1", "method1", StdOut, "[out]")
        serializer.onOutput("Class2", "method1", StdErr, "[err]")
        serializer.onOutput("Class1", "method1", StdErr, "[err]")
        serializer.onOutput("Class1", "method1", StdOut, "[out2]")
        serializer.finishOutputs()

        then:
        collectOutput("Class1", StdOut) == "[out][out2]"
        collectOutput("Class1", StdErr) == "[err]"
        collectOutput("Class2", StdErr) == "[err]"
    }

    def "writes nothing for unknown test class"() {
        when:
        serializer.finishOutputs()

        then:
        collectOutput("Unknown", StdErr) == ""
    }

    def "can query whether output is available for a test class"() {
        when:
        serializer.onOutput("Class1", "method1", StdOut, "[out]")
        serializer.finishOutputs()

        then:
        serializer.hasOutput("Class1", StdOut)
        !serializer.hasOutput("Class1", StdErr)
        !serializer.hasOutput("Unknown", StdErr)
    }

    String collectOutput(String className, TestOutputEvent.Destination destination) {
        def writer = new StringWriter()
        serializer.writeOutputs(className, destination, writer)
        return writer.toString()
    }
}
