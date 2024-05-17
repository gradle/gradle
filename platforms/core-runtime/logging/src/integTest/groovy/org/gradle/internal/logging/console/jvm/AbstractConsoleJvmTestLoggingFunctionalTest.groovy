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

package org.gradle.internal.logging.console.jvm

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.ExecutionResult

abstract class AbstractConsoleJvmTestLoggingFunctionalTest extends AbstractIntegrationSpec {
    private static final String TEST_TASK_NAME = 'test'
    private static final String TEST_TASK_PATH = ":$TEST_TASK_NAME"
    private static final String JAVA_TEST_FILE_PATH = 'src/test/java/MyTest.java'

    abstract ConsoleOutput getConsoleType()

    def setup() {
        buildFile << javaProject()
    }

    def "can group failed test log event with task by default"() {
        given:
        file(JAVA_TEST_FILE_PATH) << javaTestClass {
            'throw new RuntimeException("expected");'
        }

        when:
        executer.withConsole(consoleType)
        fails(TEST_TASK_NAME)

        then:
        def taskOutput = getTaskOutput(result)
        matchesTaskOutput(taskOutput, testLogEventRegex(TestLogEvent.FAILED.consoleMarker))
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
        executer.withConsole(consoleType)
        succeeds(TEST_TASK_NAME)

        then:
        def taskOutput = getTaskOutput(result)
        matchesTaskOutput(taskOutput, testLogEventRegex(TestLogEvent.SKIPPED.consoleMarker))
    }

    def "can group started test log event with task if configured"() {
        given:
        buildFile << testLoggingEvents(TestLogEvent.STARTED.testLoggingIdentifier)
        file(JAVA_TEST_FILE_PATH) << javaTestClass { '' }

        when:
        executer.withConsole(consoleType)
        succeeds(TEST_TASK_NAME)

        then:
        def taskOutput = getTaskOutput(result)
        matchesTaskOutput(taskOutput, testLogEventRegex(TestLogEvent.STARTED.consoleMarker))
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
        executer.withConsole(consoleType)
        succeeds(TEST_TASK_NAME)

        then:
        def taskOutput = getTaskOutput(result).readLines().findAll { !it.isBlank() } .join('\n')
        taskOutput.contains("""MyTest > testExpectation ${TestLogEvent.STANDARD_OUT.consoleMarker}
    standard output""")
        taskOutput.contains("""MyTest > testExpectation ${TestLogEvent.STANDARD_ERROR.consoleMarker}
    standard error""")
    }

    def "can group multiple test log events with task"() {
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
        executer.withConsole(consoleType)
        fails(TEST_TASK_NAME)

        then:
        def taskOutput = getTaskOutput(result)
        taskOutput.contains("""MyTest > testExpectation ${TestLogEvent.STANDARD_OUT.consoleMarker}
    standard output""")
        matchesTaskOutput(taskOutput, testLogEventRegex(TestLogEvent.STARTED.consoleMarker))
        matchesTaskOutput(taskOutput, testLogEventRegex(TestLogEvent.FAILED.consoleMarker))
    }

    @ToBeFixedForConfigurationCache(because = "https://github.com/gradle/gradle/issues/24613")
    def "can group output from custom test listener with task"() {
        buildFile << """
            test {
                beforeTest { descriptor ->
                    logger.quiet 'Starting test: ' + descriptor.className + ' > ' + descriptor.name
                }
                afterTest { descriptor, result ->
                    logger.quiet 'Finishing test: ' + descriptor.className + ' > ' + descriptor.name
                }
            }
        """
        file(JAVA_TEST_FILE_PATH) << javaTestClass { '' }

        when:
        executer.withConsole(consoleType)
        succeeds(TEST_TASK_NAME)

        then:
        def taskOutput = getTaskOutput(result)
        taskOutput.contains('Starting test: MyTest > testExpectation')
        taskOutput.contains('Finishing test: MyTest > testExpectation')
    }

    static String javaProject() {
        """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
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

    static String getTaskOutput(ExecutionResult result) {
        result.groupedOutput.task(TEST_TASK_PATH).output.trim()
    }

    static boolean matchesTaskOutput(String taskOutput, String regexToFind) {
        (taskOutput =~ /(?ms)($regexToFind)/).matches()
    }

    static String testLogEventRegex(String event) {
        "MyTest > testExpectation.*$event.*"
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
