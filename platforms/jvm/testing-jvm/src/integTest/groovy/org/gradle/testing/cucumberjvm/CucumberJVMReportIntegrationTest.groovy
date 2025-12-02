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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.hamcrest.Matchers
import org.junit.Rule
import spock.lang.Issue


class CucumberJVMReportIntegrationTest extends AbstractSampleIntegrationTest implements VerifiesGenericTestReportResults {

    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.CUCUMBER
    }
    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    @Issue("https://github.com/gradle/gradle/issues/35570")
    def junit5Suite() {
        when:
        run "test"

        then:
        executedAndNotSkipped(":test")

        and:
        def result = resultsFor()
        result.assertTestPathsExecuted(
            ":RunCukesTest:feature_classpath_features/helloworld.feature:Say hello /two/three",
            ":RunCukesTest:feature_classpath_features/secondhello.feature:Say hi /two/three",
        )
    }

    @Requires(value = UnitTestPreconditions.NotWindows, reason = "Cannot use ':' in file names on Windows")
    def junit5SuiteNameClash() {
        given:
        // Has exact same content as my_thing.feature, but with different Feature name
        testDirectory.file("src/test/resources/features/my:thing.feature").text = '''
Feature: Another Thing

    @bar
    Scenario: A Scenario
        Given I have a hello app with Howdy and /four
        When I ask it to say hi and /five/six/seven
        Then it should answer with Howdy World

        '''

        when:
        run "test"

        then:
        executedAndNotSkipped(":test")

        and:
        def testResults = resultsFor()
        testResults.assertTestPathsExecuted(
            ":RunCukesTest:feature_classpath_features/my_thing.feature:A Scenario",
        )
        // "Another Thing" comes first due to lexical ordering of '_' vs ':'
        testResults.testPath(":RunCukesTest:feature_classpath_features/my_thing.feature").onlyRoot()
            .assertDisplayName(Matchers.is("Another Thing / My Thing"))
    }

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
