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

package org.gradle.testing.nonclassbased

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework

/**
 * Tests that exercise and demonstrate a broken Non-Class-Based Testing Engine that fails during execution.
 */
class FailsExecutionNonClassBasedTestingIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest implements VerifiesGenericTestReportResults {
    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.FAILS_EXECUTION_RESOURCE_BASED]
    }

    @Override
    TestFramework getTestFramework() {
        return TestFramework.JUNIT_JUPITER
    }

    def "engine failing during execution is handled gracefully"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        fails("test")

        then:
        def results = resultsFor()
        def testPath = results.testPath(":engine_fails-execution-rbt-engine")
        testPath.onlyRoot().assertChildCount(1, 1)
        results.testPathPreNormalized(":engine_fails-execution-rbt-engine:initializationError").onlyRoot()
            .assertFailureMessages(containsNormalizedString("org.junit.platform.commons.JUnitException: TestEngine with ID 'fails-execution-rbt-engine' failed to execute tests"))
            .assertFailureMessages(containsNormalizedString("java.lang.RuntimeException: Test execution failed"))
    }

    def "engine failing during execution is not started if no test def dirs specified"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}
            }
        """

        writeTestDefinitions()

        when:
        succeeds("test")

        then:
        result.assertTaskSkipped(":test")
    }
}
