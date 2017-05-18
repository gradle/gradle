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

package org.gradle.internal.logging

import org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec

class ConsoleJvmTestLoggingFunctionalTest extends AbstractConsoleFunctionalSpec {

    private static final String TEST_TASK_NAME = 'test'
    private static final String JAVA_TEST_FILE_PATH = 'src/test/java/MyTest.java'
    private static final String TEST_TASK_GROUP_HEADER = "> Task :$TEST_TASK_NAME"
    private static final String BUILD_SUCCESSFUL_OUTPUT = 'BUILD SUCCESSFUL'

    def setup() {
        buildFile << javaProject()
    }

    def "can group failed test log event with task by default"() {
        executer.withStackTraceChecksDisabled()

        given:
        file(JAVA_TEST_FILE_PATH) << javaTestClass {
            'throw new RuntimeException("expected");'
        }

        when:
        fails(TEST_TASK_NAME)

        then:
        parseAndAssertTaskOutput(output, TEST_TASK_GROUP_HEADER, '1 test completed, 1 failed', testLogEventRegex(TestLogEvent.FAILED.consoleMarker))
    }

    def "can group skipped test log event with task if configured"() {
        given:
        buildFile << testLoggingEvents(TestLogEvent.SKIPPED.testLoggingIdentifier)

        file(JAVA_TEST_FILE_PATH) << """
            import org.junit.Test;
            import org.junit.Ignore;

            public class MyTest {
                @Ignore
                @Test
                public void testExpectation() { }
            }
        """

        when:
        succeeds(TEST_TASK_NAME)

        then:
        parseAndAssertTaskOutput(output, TEST_TASK_GROUP_HEADER, BUILD_SUCCESSFUL_OUTPUT, testLogEventRegex(TestLogEvent.SKIPPED.consoleMarker))
    }

    def "can group started test log event with task if configured"() {
        given:
        buildFile << testLoggingEvents(TestLogEvent.STARTED.testLoggingIdentifier)
        file(JAVA_TEST_FILE_PATH) << javaTestClass { '' }

        when:
        succeeds(TEST_TASK_NAME)

        then:
        parseAndAssertTaskOutput(output, TEST_TASK_GROUP_HEADER, BUILD_SUCCESSFUL_OUTPUT, testLogEventRegex(TestLogEvent.STARTED.consoleMarker))
    }

    def "can group standard output streams with task if configured"() {
        given:
        buildFile << testLoggingStandardStream()

        file(JAVA_TEST_FILE_PATH) << javaTestClass {
            """
                System.out.println("standard output");
                System.err.println("standard error");
            """
        }

        when:
        succeeds(TEST_TASK_NAME)

        then:
        def groupedTaskOutput = parseTaskOutput(output, TEST_TASK_GROUP_HEADER, BUILD_SUCCESSFUL_OUTPUT)
        groupedTaskOutput.contains("""MyTest > testExpectation ${TestLogEvent.STANDARD_OUT.consoleMarker}
    standard output""")
        groupedTaskOutput.contains("""MyTest > testExpectation ${TestLogEvent.STANDARD_ERROR.consoleMarker}
    standard error""")
    }

    def "can group multiple test log events with task"() {
        executer.withStackTraceChecksDisabled()

        given:
        buildFile << testLoggingEvents(TestLogEvent.STARTED.testLoggingIdentifier, TestLogEvent.FAILED.testLoggingIdentifier)
        buildFile << testLoggingStandardStream()

        file(JAVA_TEST_FILE_PATH) << javaTestClass {
            """
                System.out.println("standard output");
                throw new RuntimeException("expected");
            """
        }

        when:
        fails(TEST_TASK_NAME)

        then:
        def groupedTaskOutput = parseTaskOutput(output, TEST_TASK_GROUP_HEADER, '1 test completed, 1 failed')
        groupedTaskOutput.contains("""MyTest > testExpectation ${TestLogEvent.STANDARD_OUT.name()}
    standard output""")
        assertTaskOutput(groupedTaskOutput, testLogEventRegex(TestLogEvent.STARTED.consoleMarker))
        assertTaskOutput(groupedTaskOutput, testLogEventRegex(TestLogEvent.FAILED.consoleMarker))
    }

    static String javaProject() {
        """
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """
    }

    static String testLoggingEvents(String... events) {
        """
            test {
                testLogging {
                    events ${events.collect { "'$it'" }.join(', ')}
                }
            }
        """
    }

    static String testLoggingStandardStream() {
        """
            test {
                testLogging {
                    showStandardStreams = true
                }
            }
        """
    }

    static String javaTestClass(Closure<String> testMethodBody) {
        """
            import org.junit.Test;

            public class MyTest {
                @Test
                public void testExpectation() {
                    ${testMethodBody()}
                }
            }
        """
    }

    static void parseAndAssertTaskOutput(String output, String taskOutputStart, String taskOutputEnd, String regexToFind) {
        def groupedTaskOutput = parseTaskOutput(output, taskOutputStart, taskOutputEnd)
        assertTaskOutput(groupedTaskOutput, regexToFind)
    }

    static String parseTaskOutput(String output, String taskOutputStart, String taskOutputEnd) {
        def matcher = output =~ /(?ms)($taskOutputStart.*?$taskOutputEnd)/
        assert matcher.find()
        matcher[0][1]
    }

    static void assertTaskOutput(String groupedTaskOutput, String regexToFind) {
        assert (groupedTaskOutput =~ /(?ms)($regexToFind)/).matches()
    }

    static String testLogEventRegex(String event) {
        ".*MyTest > testExpectation.*$event.*"
    }

    private enum TestLogEvent {
        STARTED, FAILED, SKIPPED, STANDARD_OUT, STANDARD_ERROR

        String getConsoleMarker() {
            name()
        }

        String getTestLoggingIdentifier() {
            name().toLowerCase()
        }
    }
}
