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

package org.gradle.testing.cucumberjvm

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import spock.lang.Issue

class CucumberJVMReportIntegrationTest extends AbstractSampleIntegrationTest implements VerifiesGenericTestReportResults {

    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.CUCUMBER
    }
    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    @Issue("https://issues.gradle.org/browse/GRADLE-2739")
    def testReportingSupportsCucumberStepsWithSlashes() {
        when:
        run "test"

        then:
        executedAndNotSkipped(":test")

        and:
        def result = resultsFor()
        result.testPath("RunCukesTest", "Hello World /one", "Say hello /two/three").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }
}
