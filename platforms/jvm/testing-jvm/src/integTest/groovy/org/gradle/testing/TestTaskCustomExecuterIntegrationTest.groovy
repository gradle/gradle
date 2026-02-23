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

import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestExecuter
import org.gradle.api.internal.tasks.testing.TestExecutionSpec
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestFailure
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

import static org.hamcrest.Matchers.containsString

/**
 * Integration tests showing the behavior of custom {@link TestExecuter} implementations configured on Test tasks.
 */
class TestTaskCustomExecuterIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.CUSTOM
    }

    def setup() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                targets.all {
                    testTask.configure {
                        useJUnitJupiter()
                    }
                }
            }
        """.stripIndent()
    }

    def "no results when executer fails during execution without emitting start or completed events"() {
        given:
        buildFile << """
            class BadExecuter implements ${TestExecuter.name}<${TestExecutionSpec.name}> {
                @Override
                public void execute(${TestExecutionSpec.name} spec, ${TestResultProcessor.name} resultProcessor) {
                    throw new RuntimeException("Bad executer always fails")
                }

                @Override
                public void stopNow() {}
            }

            testing.suites.test {
                targets.all {
                    testTask.configure {
                        setTestExecuter(new BadExecuter())
                    }
                }
            }
        """
        writeSimpleTest()

        when:
        fails("test")

        then:
        failureDescriptionContains("Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').")
        failureCauseContains("Bad executer always fails")

        then:
        !file("build/reports/tests/test/index.html").exists()
    }

    def "empty results exist when executer fails during execution after emitting start event"() {
        given:
        buildFile << """
            class BadExecuter implements ${TestExecuter.name}<${TestExecutionSpec.name}> {
                @Override
                public void execute(${TestExecutionSpec.name} spec, ${TestResultProcessor.name} resultProcessor) {
                    resultProcessor.started(new ${DefaultTestSuiteDescriptor.name}("executor", "executor"), new ${TestStartEvent.name}(System.currentTimeMillis()))
                    throw new RuntimeException("Bad executer always fails")

                }

                @Override
                public void stopNow() {}
            }

            testing.suites.test {
                targets.all {
                    testTask.configure {
                        setTestExecuter(new BadExecuter())
                    }
                }
            }
        """

        writeSimpleTest()

        when:
        fails("test")

        then:
        failureDescriptionContains("Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').")
        failureCauseContains("Bad executer always fails")

        resultsFor()
            .assertTestPathsExecuted(":")
            .testPath(":").rootNames == []
    }

    def "results exist when executer fails during execution after emitting start event and completes with failure"() {
        given:
        buildFile << """
            class BadExecutor implements ${TestExecuter.name}<${TestExecutionSpec.name}> {
                @Override
                public void execute(${TestExecutionSpec.name} spec, ${TestResultProcessor.name} resultProcessor) {
                    resultProcessor.started(new ${DefaultTestSuiteDescriptor.name}("executor", "executor"), new ${TestStartEvent.name}(System.currentTimeMillis()))
                    try {
                        throw new RuntimeException("Bad executer always fails")
                    } catch (Exception e) {
                        resultProcessor.completed("executor", new ${TestCompleteEvent.name}(System.currentTimeMillis(), ${TestResult.name}.ResultType.FAILURE))
                        throw e
                    }
                    // Won't run, but shows proper implementation
                    resultProcessor.completed("executor", new ${TestCompleteEvent.name}(System.currentTimeMillis(), ${TestResult.name}.ResultType.SUCCESS))
                }

                @Override
                public void stopNow() {}
            }

            testing.suites.test {
                targets.all {
                    testTask.configure {
                        setTestExecuter(new BadExecutor())
                    }
                }
            }
        """

        writeSimpleTest()

        when:
        fails("test")

        then:
        failureDescriptionContains("Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').")
        failureCauseContains("Bad executer always fails")

        resultsFor()
            .assertTestPathsExecuted(":")
            .testPath(":").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
    }

    def "results exist with the exception when executer fails during execution after emitting start event and completes with failure, reporting the exception properly during execution"() {
        given:
        buildFile << """
            class ProperlyFailingExecuter implements ${TestExecuter.name}<${TestExecutionSpec.name}> {
                @Override
                public void execute(${TestExecutionSpec.name} spec, ${TestResultProcessor.name} resultProcessor) {
                    resultProcessor.started(new ${DefaultTestSuiteDescriptor.name}("executor", "executor"), new ${TestStartEvent.name}(System.currentTimeMillis()))
                    try {
                        throw new RuntimeException("Properly failing executer always fails")
                    } catch (Exception e) {
                        resultProcessor.failure("executor", ${TestFailure.class.getName()}.fromTestFrameworkFailure(e))
                        resultProcessor.completed("executor", new ${TestCompleteEvent.name}(System.currentTimeMillis(), ${TestResult.name}.ResultType.FAILURE))
                        throw e
                    }
                    // Won't run, but shows proper implementation
                    resultProcessor.completed("executor", new ${TestCompleteEvent.name}(System.currentTimeMillis(), ${TestResult.name}.ResultType.SUCCESS))
                }

                @Override
                public void stopNow() {}
            }

            testing.suites.test {
                targets.all {
                    testTask.configure {
                        setTestExecuter(new ProperlyFailingExecuter())
                    }
                }
            }
        """

        writeSimpleTest()

        when:
        fails("test")

        then:
        failureDescriptionContains("Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').")
        failureCauseContains("Properly failing executer always fails")

        resultsFor()
            .assertTestPathsExecuted(":")
            .testPath(":").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString("Properly failing executer always fails"))
    }

    /**
     * Verify behavior of Develocity distribution plugin when no connected agent matches the specified requirements.
     * <p>
     * The Test Distribution executor reports an event: Distributed Test Run :test prior to failing.
     * In Gradle < 9.3.0 and DV <= 4.2.2 this results in no test report being generated;
     * in Gradle >= 9.3.0 a report is generated.  When a report is generated in DV <= 4.2.2, it shows a success;
     * in DV > 4.2.2, it shows a failure.
     */
    def "dv distribution plugin fails if no currently connected agent matches all requirements"() {
        given:
        settingsFile << """
            buildscript {
                repositories {
                    gradlePluginPortal()
                }

                dependencies {
                    classpath("com.gradle:develocity-gradle-plugin:4.2.2")
                }
            }

            apply(plugin: com.gradle.develocity.agent.gradle.DevelocityPlugin)

            develocity {
                buildScan {
                    termsOfUseUrl = 'https://gradle.com/help/legal-terms-of-use'
                    termsOfUseAgree = 'yes'
                }
            }

            rootProject.name = 'develocity-no-agent-match'
        """.stripIndent()

        buildFile << """
            test {
                testing.suites.test {
                    targets.all {
                        testTask.configure {
                            develocity {
                                testDistribution {
                                    enabled = true
                                    allowUntrustedServer = true
                                    maxLocalExecutors = 0
                                    requirements = ['os=nonExistingOs']
                                }
                            }
                        }
                    }
                }
            }
        """.stripIndent()

        writeAllResultsSlowTest()

        when:
        fails("test")

        then:
        failureDescriptionContains("Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').")
        failureCauseContains("Remote executors cannot be used as no Develocity server is configured. Try setting maxLocalExecutors > 0.")

        if (shouldShowFailure) {
            resultsFor().testPath(":").onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(containsString("Remote executors cannot be used as no Develocity server is configured. Try setting maxLocalExecutors > 0."))
        } else {
            resultsFor().testPath(":").onlyRoot()
                .assertHasResult(TestResult.ResultType.SUCCESS)
        }

        where:
        develocityVersion   || shouldShowFailure
        '4.2.2'             || false
//        '4.3.0'             || true (TODO: Uncomment when DV 4.3.0 is released)
    }

    private TestFile writeSimpleTest() {
        javaFile("src/test/java/SimpleTest.java", """
            public class SimpleTest {
                @org.junit.jupiter.api.Test
                public void successful() {}
            }
        """.stripIndent())
    }

    private TestFile writeAllResultsSlowTest() {
        javaFile('src/test/java/ExampleTest.java', """
            public class ExampleTest {
                @org.junit.jupiter.api.Test
                public void aTestSuccessful() {
                    sleep();
                    System.out.println("I feel this test aTest will succeed !");
                    System.err.println("Let's see !");
                }

                @org.junit.jupiter.api.Disabled
                @org.junit.jupiter.api.Test
                public void aTestSkipped() {
                    // test is skipped
                }

                @org.junit.jupiter.api.Test
                public void aTestFailed() {
                    sleep();
                    System.out.println("I feel this test aTest will fail !");
                    System.err.println("You're probably right");
                    org.junit.jupiter.api.Assertions.fail("aTest is failing !");
                }

                private void sleep() {
                    try {
                        Thread.sleep((long)(1+Math.random())*1000);
                    } catch (InterruptedException e) {
                        org.junit.jupiter.api.Assertions.fail();
                    }
                }
            }
        """.stripIndent())
    }
}
