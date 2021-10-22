/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.report.HtmlTestResultsFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.anything

class RetainClassNameForNestedSuiteInReportTest extends AbstractIntegrationSpec {
    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)
    final TestFile indexFile = testDirectoryProvider.file("build/reports/tests/test/index.html")

    @Issue("https://github.com/gradle/gradle/issues/18682")
    def "retainClassNameForNestedSuite"() {
        given:
        executer.withRepositoryMirrors()
        executer.withStackTraceChecksDisabled()

        when:
        runAndFail "test"

        then:
        def index = new HtmlTestResultsFixture(indexFile)
        index.assertHasFailedTest("classes/LibraryTest", "regular test")
        index.assertHasFailedTest("classes/LibraryTest", "nested suite")

        def result = new JUnitXmlTestExecutionResult(testDirectory)
        result.testClass("LibraryTest").assertTestCount(2, 2, 0)
            .assertTestFailed("regular test", anything())
            .assertTestFailed("nested suite", anything())
    }
}
