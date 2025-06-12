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
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.fixture.TestNGCoverage

/**
 * Tests behavior of different test frameworks when their required
 * runtime dependencies are not included on the test runtime classpath.
 */
class TestFrameworkMissingDependenciesIntegrationTest extends AbstractIntegrationSpec {
    def "junit 4 fails with sensible error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("junit:junit:${JUnitCoverage.LATEST_JUNIT4_VERSION}")
            }

            test.useJUnit()

            testing {
                suites {
                    test {
                        targets {
                            all {
                                testTask.configure {
                                    addTestListener(new TestListener() {
                                        void beforeSuite(TestDescriptor suite) {
                                            println("beforeSuite " + suite + " ")
                                        }
                                        void afterSuite(TestDescriptor suite, TestResult result) {
                                            println("afterSuite " + suite + " " + result.failures + " " + result)
                                        }
                                        void beforeTest(TestDescriptor testDescriptor) {
                                            println("beforeTest " + testDescriptor + " ")
                                        }
                                        void afterTest(TestDescriptor testDescriptor, TestResult result) {
                                            println("afterTest " + testDescriptor + " " + result.failures)
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.Test
                public void test() {}
            }
        """

        executer.withToolchainDetectionEnabled()
        executer.withStackTraceChecksDisabled()

        when:
        fails('test')

        then: "Test framework startup failure is reported"
        failureDescriptionContains("Execution failed for task ':test'.")
        failureHasCause(~/Could not start Gradle Test Executor \d+: Failed to load JUnit 4\. {2}Please ensure that JUnit 4 is available on the test runtime classpath\./)

        and: "Resolutions are provided"
        assertSuggestsInspectTaskConfiguration()

        and: "No test class results created"
        new DefaultTestExecutionResult(testDirectory).testClassDoesNotExist("MyTest")
    }

    def "junit platform fails with sensible error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("org.junit.jupiter:junit-jupiter-api:${JUnitCoverage.LATEST_JUPITER_VERSION}")
            }

            test.useJUnitPlatform()
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.jupiter.api.Test
                void test() {}
            }
        """

        executer.withStackTraceChecksDisabled()

        when:
        fails('test')

        then: "Test framework startup failure is reported"
        failureDescriptionContains("Execution failed for task ':test'.")
        failureHasCause(~/Could not start Gradle Test Executor \d+: Failed to load JUnit Platform\. {2}Please ensure that the JUnit Platform is available on the test runtime classpath\./)

        and: "Resolutions are provided"
        assertSuggestsInspectTaskConfiguration()

        and: "No test class results created"
        new DefaultTestExecutionResult(testDirectory).testClassDoesNotExist("MyTest")
    }

    def "testng fails with sensible error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("org.testng:testng:${TestNGCoverage.NEWEST}")
            }

            test.useTestNG()
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.testng.annotations.Test
                public void test() {}
            }
        """

        executer.withStackTraceChecksDisabled()

        when:
        fails('test')

        then: "Test framework startup failure is reported"
        failureDescriptionContains("Execution failed for task ':test'.")
        failureHasCause(~/Could not start Gradle Test Executor \d+: Failed to load TestNG\. {2}Please ensure that TestNG is available on the test runtime classpath\./)

        and: "Resolutions are provided"
        assertSuggestsInspectTaskConfiguration()

        and: "No test class results created"
        new DefaultTestExecutionResult(testDirectory).testClassDoesNotExist("MyTest")
    }

    private assertSuggestsInspectTaskConfiguration() {
        failure.assertHasResolution("Inspect your task configuration for errors.")
    }
}
