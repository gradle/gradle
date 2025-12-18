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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.fixture.TestFrameworkStartupTestFixture

import static org.gradle.util.Matchers.matchesRegexp

/**
 * Tests behavior of the test task there are problems starting test processing.
 *
 * See also {@link TestFrameworkMissingDependenciesIntegrationTest} for related tests.
 */
class TestProcessingStartupFailureIntegrationTest extends AbstractIntegrationSpec implements TestFrameworkStartupTestFixture {
    def "bad jvm arg stops worker from starting"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("org.junit.jupiter:junit-jupiter:${JUnitCoverage.LATEST_JUPITER_VERSION}")
            }

            test {
                useJUnitPlatform()
                jvmArgs('-phasers=stun')
            }

            ${addLoggingTestListener()}
        """

        addMyTestForJunit5()
        executer.withStackTraceChecksDisabled()

        when:
        fails('test')

        then: "Test JVM startup failure is explained"
        failure.assertHasErrorOutput("Unrecognized option: -phasers=stun")
        failure.assertHasErrorOutput("Error: Could not create the Java Virtual Machine.")
        failure.assertHasErrorOutput("Error: A fatal exception has occurred. Program will exit.")

        and: "Task failure is reported"
        assertTestWorkerFailedToStart()

        and: "No test class results are created"
        new DefaultTestExecutionResult(testDirectory).testClassDoesNotExist("MyTest")
    }

    def "mix of startup failure and regular failures show startup failures first"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }

            ${addLoggingTestListener()}
        """

        addMyTestForJunit5()
        succeeds("compileTestJava")
        file("build/classes/java/test/MyOtherTest.class").text = "corrupted content"

        when:
        fails('test', "-x", "compileTestJava")

        then: "Task failure is reported"

        failure.assertHasFailure("Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').") {
            it.assertHasCause("Could not execute test class 'MyOtherTest'.")
            it.assertHasCause("Incompatible magic value 1668248178 in class file MyOtherTest")
        }
        def testResults = new DefaultTestExecutionResult(testDirectory)
        testResults.testClassDoesNotExist("MyOtherTest")
        testResults.testClassExists("MyTest")
    }


    def "test process failing in an unusual way is shown"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }

            ${addLoggingTestListener()}
        """

        addMyTestForJunit5()
        file("src/test/java/MyOtherTest.java") << """
            public class MyOtherTest {
                @org.junit.jupiter.api.Test
                void test() {
                    System.exit(-25);
                }
            }
        """
        when:
        executer.withStackTraceChecksDisabled()
        fails('test')

        then: "Task failure is reported"

        failure.assertHasDescription("Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').")
        failure.assertHasCause("Test process encountered an unexpected problem.")
        failure.assertThatCause(matchesRegexp(/Process 'Gradle Test Executor \d+' finished with non-zero exit value.*/))
    }

    def "tests not found due to incorrect framework used (junit 4 default)"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("org.junit.jupiter:junit-jupiter:${JUnitCoverage.LATEST_JUPITER_VERSION}")
            }

            ${addLoggingTestListener()}
        """

        addMyTestForJunit5()

        when:
        fails('test')

        then: "Task failure is reported"
        failure.assertHasDescription("Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').")
        failure.assertHasCause("There are test sources present and no filters are applied, but the test task did not discover any tests to execute. This is likely due to a misconfiguration. Please check your test configuration. If this is not a misconfiguration, this error can be disabled by setting the 'failOnNoDiscoveredTests' property to false.")

        and: "No test class results are created"
        new DefaultTestExecutionResult(testDirectory).testClassDoesNotExist("MyTest")
    }

    private TestFile addMyTestForJunit5() {
        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.jupiter.api.Test
                void test() {}
            }
        """
    }
}
