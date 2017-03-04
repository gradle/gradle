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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

import static org.hamcrest.Matchers.startsWith

public class JUnitCategoriesIntegrationSpec extends AbstractIntegrationSpec {

    @Rule TestResources resources = new TestResources(temporaryFolder)

    def reportsUnloadableExcludeCategory() {
        given:
        resources.maybeCopy("JUnitCategoriesIntegrationSpec/reportsUnloadableCategories")
        buildFile << "test.useJUnit { excludeCategories 'org.gradle.CategoryA' }"

        when:
        fails("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTestClass')
        result.testClass("org.gradle.SomeTestClass").assertTestCount(1, 1, 0)
        result.testClass("org.gradle.SomeTestClass").assertTestFailed("initializationError", startsWith("org.gradle.api.InvalidUserDataException: Can't load category class [org.gradle.CategoryA]"))
    }

    def reportsUnloadableIncludeCategory() {
        given:
        resources.maybeCopy("JUnitCategoriesIntegrationSpec/reportsUnloadableCategories")
        buildFile << "test.useJUnit { excludeCategories 'org.gradle.CategoryA' }"

        when:
        fails('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTestClass')
        result.testClass("org.gradle.SomeTestClass").assertTestCount(1, 1, 0)
        result.testClass("org.gradle.SomeTestClass").assertTestFailed("initializationError", startsWith("org.gradle.api.InvalidUserDataException: Can't load category class [org.gradle.CategoryA]"))
    }

    def testTaskFailsIfCategoriesNotSupported() {
        when:
        fails('test')
        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("org.gradle.SomeTest").assertTestFailed("initializationError", startsWith("org.gradle.api.GradleException: JUnit Categories defined but declared JUnit version does not support Categories."))
    }

    def supportsTestCategoriesAndCucumber() {
        when:
        run "test"
        then:
        ":test" in nonSkippedTasks
        and:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        def scenario = "Scenario: Using Categories and Cucumber in the same project"
        result.assertTestClassesExecuted("CucumberTest", scenario)
        def testClass = result.testClass(scenario)
        testClass.assertTestCount(4, 0, 0)
        testClass.assertTestPassed("Given a project containing cucumber tests")
        testClass.assertTestPassed("When restricting junit tests using categories")
        testClass.assertTestPassed("Then the test should not fail to initialize with an NullPointerException")
    }
}
