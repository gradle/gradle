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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue

class CucumberJVMReportIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    @Issue("https://issues.gradle.org/browse/GRADLE-2739")
    @IgnoreIf({GradleContextualExecuter.parallel})
    def testReportingSupportsCucumberStepsWithSlashes() {
        when:
        run "test"
        then:
        ":test" in nonSkippedTasks
        and:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("RunCukesTest", "Scenario: Say hello /two/three")
        result.testClass("Scenario: Say hello /two/three").assertTestPassed("Given I have a hello app with Howdy and /four")
        result.testClass("Scenario: Say hello /two/three").assertTestPassed("Then it should answer with Howdy World")
        result.testClass("Scenario: Say hello /two/three").assertTestPassed("When I ask it to say hi and /five/six/seven")
    }
}
