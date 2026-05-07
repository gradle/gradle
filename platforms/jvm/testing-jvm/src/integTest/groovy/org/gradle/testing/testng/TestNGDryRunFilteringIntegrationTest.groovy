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

import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.DryRunFilteringTest
import org.gradle.testing.fixture.TestNGCoverage
import org.gradle.util.Matchers
import org.hamcrest.text.IsEmptyString

@TargetCoverage({ TestNGCoverage.SUPPORTS_DRY_RUN })
class TestNGDryRunFilteringIntegrationTest extends AbstractTestNGFilteringIntegrationTest implements DryRunFilteringTest {
    // TestNG reports dry-run tests as successes
    TestResult.ResultType getPassedTestOutcome() {
        return TestResult.ResultType.SUCCESS
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
        run "dryRunTest", "test"

        then:
        def testResult = resultsFor()
        testResult.assertAtLeastTestPathsExecuted("FooTest")
        testResult.testPath("FooTest").onlyRoot().assertChildCount(1, 0)
        testResult.testPath("FooTest:foo").onlyRoot().assertStderr(Matchers.containsText("Run foo!"))

        def dryRunTestResult = resultsFor("tests/dryRunTest")
        dryRunTestResult.assertAtLeastTestPathsExecuted("BarTest")
        dryRunTestResult.testPath("BarTest").onlyRoot().assertChildCount(1, 0)
        dryRunTestResult.testPath("BarTest:bar").onlyRoot().assertStderr(IsEmptyString.emptyString())
    }
}
