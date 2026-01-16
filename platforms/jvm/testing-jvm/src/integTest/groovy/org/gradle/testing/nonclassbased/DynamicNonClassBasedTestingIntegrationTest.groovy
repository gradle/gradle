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
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestResult

import static org.hamcrest.Matchers.equalTo

class DynamicNonClassBasedTestingIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest implements VerifiesGenericTestReportResults {
    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.BASIC_RESOURCE_BASED_DYNAMIC]
    }

    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.CUSTOM
    }

    def "dynamic resource-based test engine detects and executes test definitions"() {
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

        file("${DEFAULT_DEFINITIONS_LOCATION}/SomeTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
            </tests>
        """

        when:
        succeeds("test")

        then:
        def testResults = resultsFor()
        testResults.assertTestPathsExecuted(
            ":src/test/definitions/SomeTestSpec.rbt:Dynamic Test",
        )
        testResults.testPath(
            ":src/test/definitions/SomeTestSpec.rbt:Dynamic Test"
        ).onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertDisplayName(equalTo("Dynamic Test"))
    }
}
