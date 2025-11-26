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

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestFailure
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.Matchers.containsString

class TestTaskCustomExecutorIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.CUSTOM
    }

    def "empty results when executer fails without recording it in the processor during execution"() {
        // Replicates 4.2.2 DV behavior, where it always completes successfully even when an exception occurs
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            class BadExecuter implements org.gradle.api.internal.tasks.testing.TestExecuter {
                @Override
                public void execute(org.gradle.api.internal.tasks.testing.TestExecutionSpec spec, org.gradle.api.internal.tasks.testing.TestResultProcessor resultProcessor) {
                    try {
                        resultProcessor.started(new org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor("executor", "executor"), new org.gradle.api.internal.tasks.testing.TestStartEvent(System.currentTimeMillis()))
                        throw new RuntimeException("Bad executer always fails")
                    } finally {
                        resultProcessor.completed("executor", new org.gradle.api.internal.tasks.testing.TestCompleteEvent(System.currentTimeMillis()))
                    }
                }

                @Override
                public void stopNow() {
                }
            }

            testing.suites.test {
                targets.all {
                    testTask.configure {
                        setTestExecuter(new BadExecuter())
                    }
                }
            }
        """

        file("src/test/java/DummyTest.java") << """
            public class DummyTest {
            }
        """

        when:
        fails("test")

        then:
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Bad executer always fails")

        def testResults = resultsFor()
        testResults.assertTestPathsExecuted(":")
        testResults.testPath(":").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }

    def "no results when executer fails before starting tests"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            class BadExecuter implements org.gradle.api.internal.tasks.testing.TestExecuter {
                @Override
                public void execute(org.gradle.api.internal.tasks.testing.TestExecutionSpec spec, org.gradle.api.internal.tasks.testing.TestResultProcessor resultProcessor) {
                    throw new RuntimeException("Bad executer always fails")
                }

                @Override
                public void stopNow() {
                }
            }

            testing.suites.test {
                targets.all {
                    testTask.configure {
                        setTestExecuter(new BadExecuter())
                    }
                }
            }
        """

        file("src/test/java/DummyTest.java") << """
            public class DummyTest {
            }
        """

        when:
        fails("test")

        then:
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Bad executer always fails")

        !file("build/reports/tests/test/index.html").exists()
    }

    def "results have the exception when executer fails and reports it during execution"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            class BadExecuter implements org.gradle.api.internal.tasks.testing.TestExecuter {
                @Override
                public void execute(org.gradle.api.internal.tasks.testing.TestExecutionSpec spec, org.gradle.api.internal.tasks.testing.TestResultProcessor resultProcessor) {
                    try {
                        resultProcessor.started(new org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor("executor", "executor"), new org.gradle.api.internal.tasks.testing.TestStartEvent(System.currentTimeMillis()))
                        throw new RuntimeException("Bad executer always fails")
                    } catch (Exception e) {
                        resultProcessor.failure("executor", ${TestFailure.class.getName()}.fromTestFrameworkFailure(e))
                        resultProcessor.completed("executor", new org.gradle.api.internal.tasks.testing.TestCompleteEvent(System.currentTimeMillis(), org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE))
                    }
                }

                @Override
                public void stopNow() {
                }
            }

            testing.suites.test {
                targets.all {
                    testTask.configure {
                        setTestExecuter(new BadExecuter())
                    }
                }
            }
        """

        file("src/test/java/DummyTest.java") << """
            public class DummyTest {
            }
        """

        when:
        fails("test")

        then:
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Bad executer always fails")

        def testResults = resultsFor()
        testResults.assertTestPathsExecuted(":")
        testResults.testPath(":").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString("Bad executer always fails"))
    }
}
