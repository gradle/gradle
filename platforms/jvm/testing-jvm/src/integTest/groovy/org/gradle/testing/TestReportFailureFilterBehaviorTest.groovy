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
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT4_VERSION

class TestReportFailureFilterBehaviorTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.JUNIT4
    }

    def setup() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:${LATEST_JUNIT4_VERSION}'
            }
        """
    }

    def "failure filter checkbox and link are present when there are failures"() {
        given:
        file("src/test/java/PassingTest.java") << """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class PassingTest {
                @Test
                public void testSuccess() {
                    assertTrue(true);
                }
            }
        """

        file("src/test/java/FailingTest.java") << """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class FailingTest {
                @Test
                public void testFailure() {
                    fail("This test should fail");
                }
            }
        """

        when:
        executer.withStackTraceChecksDisabled()
        fails("test")

        then:
        def testResults = resultsFor()

        // Verify checkbox exists and is checked by default
        testResults.assertHtml("#failure-filter-toggle") { e ->
            verifyAll {
                e.size() == 1
                e[0].tagName() == "input"
                e[0].attr("type") == "checkbox"
                e[0].hasAttr("checked")
            }
        }

        // Verify failure summary link exists
        testResults.assertHtml("#failure-summary-link") { e ->
            verifyAll {
                e.size() == 1
                e[0].tagName() == "a"
                e[0].text().contains("Failure Summary")
            }
        }

        // Verify that classes with failure indicators are present in the HTML
        testResults.assertHtml(".failureGroup") { e ->
            assert e.size() > 0
        }
    }

    def "large test case with filtering"() {
        given:
        LargeTestCaseGenerator.generateTests("Test", testDirectory)

        when:
        executer.withStackTraceChecksDisabled()
        fails("test")

        then:
        def testResults = resultsFor()

        // Verify checkbox and failure summary link are present
        testResults.assertHtml("#failure-filter-toggle") { e ->
            assert e.size() == 1
        }

        testResults.assertHtml("#failure-summary-link") { e ->
            assert e.size() == 1
        }

        // Verify there are failure indicators
        testResults.assertHtml(".failureGroup") { e ->
            assert e.size() > 0
        }
    }

    def "aggregated report with multiple subprojects shows failure filter"() {
        given:
        settingsFile << """
            rootProject.name = 'aggregate-test-report-test'
            include 'application', 'direct', 'transitive', 'direct-passing', 'transitive-passing'
        """
        buildFile << """
            apply plugin: 'org.gradle.test-report-aggregation'
            dependencies {
                testReportAggregation project(":application")
                testReportAggregation project(":direct")
                testReportAggregation project(":direct-passing")
                testReportAggregation project(":transitive")
                testReportAggregation project(":transitive-passing")
            }

            reporting {
                reports {
                    testAggregateTestReport(AggregateTestReport) {
                        testSuiteName = "test"
                    }
                }
            }

            subprojects {
                apply plugin: 'java-library'

                ${mavenCentralRepository()}

                testing.suites.test {
                    useJUnit()
                }
            }
        """
        file("application/build.gradle") << """
            dependencies {
                implementation project(":direct")
            }
        """
        file("direct/build.gradle") << """
            dependencies {
                implementation project(":transitive")
            }
        """
        file("direct/build.gradle") << """
            dependencies {
                implementation project(":transitive-passing")
            }
        """

        // Generate tests for each subproject
        LargeTestCaseGenerator.generateTests("Application", testDirectory.file("application"))
        LargeTestCaseGenerator.generateTests("Direct", testDirectory.file("direct"))
        LargeTestCaseGenerator.generateTests("Direct_Passing", testDirectory.file("direct-passing"), false)
        LargeTestCaseGenerator.generateTests("Transitive", testDirectory.file("transitive"))
        LargeTestCaseGenerator.generateTests("Transitive_Passing", testDirectory.file("transitive-passing"), false)

        when:
        fails(':testAggregateTestReport', "--continue")

        then:
        failure.assertHasFailure("Execution failed for task ':application:test'.") {}
        failure.assertHasFailure("Execution failed for task ':direct:test'.") {}
        failure.assertHasFailure("Execution failed for task ':transitive:test'.") {}

        def aggregatedResults = new GenericHtmlTestExecutionResult(testDirectory, 'build/reports/tests/test/aggregated-results', getTestFramework())

        // Verify checkbox and failure summary link are present in aggregated report
        aggregatedResults.assertHtml("#failure-filter-toggle") { e ->
            assert e.size() == 1
        }

        aggregatedResults.assertHtml("#failure-summary-link") { e ->
            assert e.size() == 1
        }

        // Verify there are failure indicators in aggregated report
        aggregatedResults.assertHtml(".failureGroup") { e ->
            assert e.size() > 0
        }
    }
}
