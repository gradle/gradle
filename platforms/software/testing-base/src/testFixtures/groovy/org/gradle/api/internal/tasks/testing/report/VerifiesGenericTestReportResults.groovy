/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report

import groovy.transform.SelfType
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestReportGenerator
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.test.fixtures.file.TestFile

/**
 * A trait to be applied to tests that verify the results of the {@link GenericHtmlTestReportGenerator}.
 */
@SelfType(AbstractIntegrationSpec)
trait VerifiesGenericTestReportResults {
    String resultsUrlFor(String testTaskName = "test") {
        def expectedReportFile = file("build/reports/tests/${testTaskName}/index.html")
        String renderedUrl = new ConsoleRenderer().asClickableFileUrl(expectedReportFile)
        renderedUrl
    }

    abstract TestFramework getTestFramework()

    GenericHtmlTestExecutionResult resultsFor(String testTaskReportsDirPath = 'tests/test', TestFramework testFramework = null) {
        return resultsFor(testDirectory, testTaskReportsDirPath, testFramework ?: getTestFramework())
    }

    GenericHtmlTestExecutionResult resultsFor(TestFile rootBuildDir, String testTaskReportsDirPath = 'tests/test', TestFramework testFramework = null) {
        return new GenericHtmlTestExecutionResult(rootBuildDir, "build/reports/${testTaskReportsDirPath}", testFramework ?: getTestFramework())
    }

    GenericHtmlTestExecutionResult aggregateResults(String testTaskReportsDirPath = '', TestFramework testFramework = null) {
        return aggregateResults(testDirectory, testTaskReportsDirPath, 'aggregate-test-results', testFramework ?: getTestFramework())
    }

    GenericHtmlTestExecutionResult aggregateResults(TestFile rootBuildDir, String testTaskReportsDirPath = 'tests/test', String reportName = 'aggregate-test-results', TestFramework testFramework = null) {
        return new GenericHtmlTestExecutionResult(rootBuildDir, "build/reports/$testTaskReportsDirPath/$reportName", testFramework ?: getTestFramework())
    }
}
