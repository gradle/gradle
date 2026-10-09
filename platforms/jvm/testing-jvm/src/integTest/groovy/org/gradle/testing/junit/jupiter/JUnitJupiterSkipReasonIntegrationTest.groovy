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
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JUnitJupiterSkipReasonIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    def "captures skip reason from @Disabled"() {
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
                                            assert result.assumptionFailure == null
                                            println("Skip reason: " + result.skipReason)
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
                @Disabled("temporarily disabled")
                public void theTest() {
                    // fail the test if it actually runs
                    assert false;
                }
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("Skip reason: temporarily disabled")

        def testResult = resultsFor()
        testResult.testPath("com.example.MyTest", "theTest()").onlyRoot().assertHasResult(TestResult.ResultType.SKIPPED)
    }

    def "captures default skip reason when @Disabled has no custom reason"() {
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
                                            assert result.assumptionFailure == null
                                            println("Skip reason: " + result.skipReason)
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
        // https://github.com/junit-team/junit-framework/blob/76824f254752504ee08a11d8b0b3254e197cc17e/junit-jupiter-engine/src/main/java/org/junit/jupiter/engine/extension/DisabledCondition.java#L50
        // When @Disabled has no custom reason, JUnit builds a default
        outputContains("Skip reason: public void com.example.MyTest.theTest() is @Disabled")

        def testResult = resultsFor()
        testResult.testPath("com.example.MyTest", "theTest()").onlyRoot().assertHasResult(TestResult.ResultType.SKIPPED)
    }

    def "assumption failures do not set skip reason"() {
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
                                            assert result.skipReason == null
                                            println("Assumption without skip reason")
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
        outputContains("Assumption without skip reason")

        def testResult = resultsFor()
        testResult.testPath("com.example.MyTest", "theTest()").onlyRoot().assertHasResult(TestResult.ResultType.SKIPPED)
    }
}
