/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testing.junit.jupiter

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo

class JUnitJupiterAssumptionFailureIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.JUNIT_JUPITER
    }

    def "captures assumption failures"() {
        buildFile << """
            plugins {
                id 'java-library'
            }
            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                        targets {
                            all {
                                testTask.configure {
                                    addTestListener(new TestListener() {
                                        void beforeSuite(TestDescriptor suite) {}
                                        void afterSuite(TestDescriptor suite, TestResult result) {}
                                        void beforeTest(TestDescriptor testDescriptor) {}
                                        void afterTest(TestDescriptor testDescriptor, TestResult result) {
                                            assert result.assumptionFailure != null
                                            println("Assumption failure: " + result.assumptionFailure.details.message)
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        """
        file("src/test/java/com/example/MyTest.java") << """
            package com.example;

            import org.junit.jupiter.api.Assumptions;
            import org.junit.jupiter.api.Test;

            public class MyTest {
                @Test
                public void theTest() {
                    Assumptions.assumeTrue(false, "skipped reason");
                }
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("Assumption failure: Assumption failed: skipped reason")

        def testResult = resultsFor()
        testResult.testPath("com.example.MyTest", "theTest").onlyRoot().assertHasResult(TestResult.ResultType.SKIPPED)
            .assertFailureMessages(containsString("Assumption failed: skipped reason"))
    }

    def "test aborted failures are available as assumptionFailures"() {
        buildFile << """
            plugins {
                id 'java-library'
            }
            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                        targets {
                            all {
                                testTask.configure {
                                    addTestListener(new TestListener() {
                                        void beforeSuite(TestDescriptor suite) {}
                                        void afterSuite(TestDescriptor suite, TestResult result) {}
                                        void beforeTest(TestDescriptor testDescriptor) {}
                                        void afterTest(TestDescriptor testDescriptor, TestResult result) {
                                            assert result.assumptionFailure != null
                                            println("Assumption failure: " + result.assumptionFailure.details.message)
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        """
        file("src/test/java/com/example/MyTest.java") << """
            package com.example;

            import org.junit.jupiter.api.Test;
            import org.opentest4j.TestAbortedException;


            public class MyTest {
                @Test
                public void theTest() {
                    throw new TestAbortedException();
                }
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("Assumption failure: ")

        def testResult = resultsFor()
        testResult.testPath("com.example.MyTest", "theTest").onlyRoot().assertHasResult(TestResult.ResultType.SKIPPED)
            .assertFailureMessages(containsString("org.opentest4j.TestAbortedException"))
    }

    def "does not capture ignored tests as assumption failures"() {
        buildFile << """
            plugins {
                id 'java-library'
            }
            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                        targets {
                            all {
                                testTask.configure {
                                    addTestListener(new TestListener() {
                                        void beforeSuite(TestDescriptor suite) {}
                                        void afterSuite(TestDescriptor suite, TestResult result) {}
                                        void beforeTest(TestDescriptor testDescriptor) {}
                                        void afterTest(TestDescriptor testDescriptor, TestResult result) {
                                            println("No assumption failure")
                                            assert result.assumptionFailure == null
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        """
        file("src/test/java/com/example/MyTest.java") << """
            package com.example;

            import org.junit.jupiter.api.Disabled;
            import org.junit.jupiter.api.Test;

            public class MyTest {
                @Test
                @Disabled
                public void theTest() {
                    // fail the test if it actually runs
                    assert false;
                }
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("No assumption failure")

        def testResult = resultsFor()
        testResult.testPath("com.example.MyTest", "theTest").onlyRoot().assertHasResult(TestResult.ResultType.SKIPPED)
            .assertFailureMessages(equalTo(""))
    }
}
