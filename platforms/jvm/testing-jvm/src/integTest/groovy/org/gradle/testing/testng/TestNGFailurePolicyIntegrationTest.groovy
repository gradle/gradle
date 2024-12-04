/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.integtests.fixtures.TestNGExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

import static org.hamcrest.CoreMatchers.containsString
import static org.junit.Assume.assumeFalse
import static org.junit.Assume.assumeTrue

class TestNGFailurePolicyIntegrationTest extends AbstractTestNGVersionIntegrationTest {

    @Rule public TestResources resources = new TestResources(testDirectoryProvider)

    TestClassExecutionResult getTestResults() {
        new TestNGExecutionResult(testDirectory).testClass("org.gradle.failurepolicy.TestWithFailureInConfigMethod")
    }

    def setup() {
        buildFile << """
            testing {
                suites {
                    test {
                        useTestNG('${version}')
                    }
                }
            }
        """
    }

    def "skips tests after a config method failure by default"() {
        expect:
        fails "test"

        and:
        testResults.assertConfigMethodFailed("fail")
        testResults.assertTestSkipped("someTest")
    }

    def "can be configured to continue executing tests after a config method failure"() {
        when:
        assumeTrue(supportConfigFailurePolicy())

        buildFile << """
            testing {
                suites {
                    test {
                        targets {
                            all {
                                testTask.configure {
                                    options {
                                        configFailurePolicy = "continue"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        then:
        fails "test"

        and:
        testResults.assertConfigMethodFailed("fail")
        testResults.assertTestPassed("someTest")
    }

    def "informative error is shown when trying to use config failure policy and a version that does not support it"() {
        when:
        assumeFalse(supportConfigFailurePolicy())

        buildFile << """
            testing {
                suites {
                    test {
                        targets {
                            all {
                                testTask.configure {
                                    options {
                                        configFailurePolicy = "continue"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        then:
        fails "test"

        and:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClassStartsWith('Gradle Test Executor').assertExecutionFailedWithCause(
            containsString("The version of TestNG used does not support setting config failure policy to 'continue'."))
    }
}
