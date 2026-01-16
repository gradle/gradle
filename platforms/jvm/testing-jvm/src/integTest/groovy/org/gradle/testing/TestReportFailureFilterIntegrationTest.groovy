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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT4_VERSION

class TestReportFailureFilterIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
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

        // Verify that the failure filter checkbox is present and checked by default
        testResults.assertHtml("#failure-filter-toggle") { e ->
            verifyAll {
                e.size() == 1
                e[0].tagName() == "input"
                e[0].attr("type") == "checkbox"
                e[0].hasAttr("checked")
            }
        }

        // Verify that the label for the checkbox is present
        testResults.assertHtml("#label-for-failure-filter-toggle") { e ->
            verifyAll {
                e.size() == 1
                e[0].tagName() == "label"
                e[0].text().contains("Failures only")
            }
        }

        // Verify that the failure summary link is present
        testResults.assertHtml("#failure-summary-link") { e ->
            verifyAll {
                e.size() == 1
                e[0].tagName() == "a"
                e[0].text().contains("Failure Summary")
            }
        }
    }

    def "failure filter checkbox and link are not present when there are no failures"() {
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

        when:
        succeeds("test")

        then:
        def testResults = resultsFor()

        // Verify that the failure filter checkbox is hidden (has hidden class)
        testResults.assertHtml("#label-for-failure-filter-toggle") { e ->
            verifyAll {
                e.size() == 1
                e[0].hasClass("hidden")
            }
        }

        // Verify that the failure summary link is hidden (has hidden class)
        testResults.assertHtml("#failure-summary-link") { e ->
            verifyAll {
                e.size() == 1
                e[0].hasClass("hidden")
            }
        }
    }

    def "large test case with nested classes and parameterized tests"() {
        given:
        LargeTestCaseGenerator.generateTests("Test", testDirectory)

        when:
        executer.withStackTraceChecksDisabled()
        fails("test")

        then:
        def testResults = resultsFor()

        // Verify that the failure filter checkbox is present and checked by default
        testResults.assertHtml("#failure-filter-toggle") { e ->
            verifyAll {
                e.size() == 1
                e[0].tagName() == "input"
                e[0].attr("type") == "checkbox"
                e[0].hasAttr("checked")
            }
        }
    }
}
