/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.testing.testng

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestOutcome
import org.gradle.testing.DryRunFilteringTest
import org.gradle.testing.fixture.TestNGCoverage
import org.hamcrest.text.IsEmptyString
import org.gradle.util.Matchers

@TargetCoverage({ TestNGCoverage.SUPPORTS_DRY_RUN })
class TestNGDryRunFilteringIntegrationTest extends AbstractTestNGFilteringIntegrationTest implements DryRunFilteringTest {
    @Override
    TestOutcome getPassedTestOutcome() {
        return TestOutcome.PASSED
    }

    @Override
    TestOutcome getFailedTestOutcome() {
        return TestOutcome.PASSED
    }

    def "dry-run property is not preserved across invocations"() {
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            dependencies {
                ${testFrameworkDependencies}
            }

            testing {
                suites {
                    dryRunTest(JvmTestSuite) {
                        ${configureTestFramework}

                        targets {
                            all {
                                testTask.configure {
                                    dryRun.set(true)
                                }
                            }
                        }
                    }
                    test {
                        ${configureTestFramework}

                        targets {
                            all {
                                testTask.configure {
                                    mustRunAfter(dryRunTest)
                                }
                            }
                        }
                    }
                }
            }
        """

        file("src/test/java/FooTest.java") << """
            ${testFrameworkImports}
            public class FooTest {
                @Test public void foo() {
                    System.err.println("Run foo!");
                }
            }
        """

        file("src/dryRunTest/java/BarTest.java") << """
            ${testFrameworkImports}
            public class BarTest {
                @Test public void bar() {
                    System.err.println("Run bar!");
                }
            }
        """

        when:
        def testResult = new DefaultTestExecutionResult(testDirectory)
        def dryRunTestResult = new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'dryRunTest')
        run "dryRunTest", "test"

        then:
        testResult.testClass("FooTest").assertStderr(Matchers.containsText("Run foo!"))
        dryRunTestResult.testClass("BarTest").assertStderr(IsEmptyString.emptyString())
    }
}
