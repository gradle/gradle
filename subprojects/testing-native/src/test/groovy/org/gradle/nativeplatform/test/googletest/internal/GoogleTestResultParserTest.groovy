/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.googletest.internal

import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.internal.id.LongIdGenerator
import org.gradle.internal.time.MockClock
import spock.lang.Specification
import spock.lang.Subject

class GoogleTestResultParserTest extends Specification {
    def listener = Mock(TestResultProcessor)
    def idGenerator = new LongIdGenerator()
    def clock = new MockClock()

    @Subject
    def parser = new GoogleTestResultParser("test", listener, idGenerator, clock)

    def "feeding google test output should produce the expected events"() {
        def googleTestOutput = """
[==========] Running 15 tests from 1 test case.
[----------] Global test environment set-up.
[----------] 15 tests from MessageTest
[ RUN      ] MessageTest.DefaultConstructor
[       OK ] MessageTest.DefaultConstructor (1 ms)
[ RUN      ] MessageTest.CopyConstructor
external/gtest/test/gtest-message_test.cc:67: Failure
Value of: 5
Expected: 2
external/gtest/test/gtest-message_test.cc:68: Failure
Value of: 1 == 1
Actual: true
Expected: false
[  FAILED  ] MessageTest.CopyConstructor (2 ms)
[----------] 15 tests from MessageTest (26 ms total)

[----------] Global test environment tear-down
[==========] 15 tests from 1 test case ran. (26 ms total)
[  PASSED  ] 6 tests.
[  FAILED  ] 9 tests, listed below:
[  FAILED  ] MessageTest.CopyConstructor
[  FAILED  ] MessageTest.ConstructsFromCString
[  FAILED  ] MessageTest.StreamsCString
[  FAILED  ] MessageTest.StreamsNullCString
[  FAILED  ] MessageTest.StreamsString
[  FAILED  ] MessageTest.StreamsStringWithEmbeddedNUL
[  FAILED  ] MessageTest.StreamsNULChar
[  FAILED  ] MessageTest.StreamsInt
[  FAILED  ] MessageTest.StreamsBasicIoManip
9 FAILED TESTS
"""
        when:
        googleTestOutput.eachLine { line ->
            parser.text(line)
        }

        then:
        3 * listener.started(_, _)
        3 * listener.completed(_, _)
        7 * listener.output(_, _)
        0 * _
    }
}
