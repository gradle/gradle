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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.junit.Assume
import org.junit.Rule

import static org.gradle.testing.fixture.JUnitCoverage.CATEGORIES
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE_JUPITER

@TargetCoverage({ CATEGORIES + JUNIT_VINTAGE_JUPITER })
class JUnitCategoriesCoverageIntegrationSpec extends JUnitMultiVersionIntegrationSpec {

    @Rule TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        executer.noExtraLogging()
        buildFile << "dependencies { ${dependencyNotation.collect { "testImplementation '$it'" }.join('\n')} }"
    }

    def canSpecifyIncludeAndExcludeCategories() {
        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        if (isJupiter()) {
            result.assertTestClassesExecuted('org.gradle.CatATests', 'org.gradle.CatADTests', 'org.gradle.MixedTests')
            result.testClass("org.gradle.CatATests").assertTestCount(4, 0, 0)
            result.testClass("org.gradle.CatATests").assertTestsExecuted('catAOk1', 'catAOk2', 'catAOk3', 'catAOk4')
            result.testClass("org.gradle.CatADTests").assertTestCount(3, 0, 0)
            result.testClass("org.gradle.CatADTests").assertTestsExecuted('catAOk1', 'catAOk2', 'catDOk4')
            result.testClass("org.gradle.MixedTests").assertTestCount(2, 0, 0)
            result.testClass("org.gradle.MixedTests").assertTestsExecuted('catAOk1')
            result.testClass("org.gradle.MixedTests").assertTestsSkipped('someIgnoredTest')
        } else {
            result.assertTestClassesExecuted('org.gradle.CatATests', 'org.gradle.CatBTests', 'org.gradle.CatADTests', 'org.gradle.MixedTests')
            result.testClass("org.gradle.CatATests").assertTestCount(4, 0, 0)
            result.testClass("org.gradle.CatATests").assertTestsExecuted('catAOk1', 'catAOk2', 'catAOk3', 'catAOk4')
            result.testClass("org.gradle.CatBTests").assertTestCount(4, 0, 0)
            result.testClass("org.gradle.CatBTests").assertTestsExecuted('catBOk1', 'catBOk2', 'catBOk3', 'catBOk4')
            result.testClass("org.gradle.CatADTests").assertTestCount(2, 0, 0)
            result.testClass("org.gradle.CatADTests").assertTestsExecuted('catAOk1', 'catAOk2')
            result.testClass("org.gradle.MixedTests").assertTestCount(3, 0, 0)
            result.testClass("org.gradle.MixedTests").assertTestsExecuted('catAOk1', 'catBOk2')
            result.testClass("org.gradle.MixedTests").assertTestsSkipped('someIgnoredTest')
        }
    }

    def canSpecifyExcludesOnly() {
        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.NoCatTests', 'org.gradle.SomeTests', 'org.gradle.SomeOtherCatTests')
        result.testClass("org.gradle.SomeOtherCatTests").assertTestCount(2, 0, 0)
        result.testClass("org.gradle.SomeOtherCatTests").assertTestsExecuted('someOtherOk1', 'someOtherOk2')
        result.testClass("org.gradle.NoCatTests").assertTestCount(2, 0, 0)
        result.testClass("org.gradle.NoCatTests").assertTestsExecuted('noCatOk1', 'noCatOk2')
        result.testClass("org.gradle.SomeTests").assertTestCount(3, 0, 0)
        result.testClass("org.gradle.SomeTests").assertTestsExecuted('noCatOk3', 'noCatOk4', 'someOtherCatOk2')
    }

    def canCombineCategoriesWithCustomRunner() {
        Assume.assumeFalse(isJupiter())

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeLocaleTests')
        result.testClass("org.gradle.SomeLocaleTests").assertTestCount(3, 0, 0)
        result.testClass("org.gradle.SomeLocaleTests").assertTestsExecuted('ok1 [de]', 'ok1 [en]', 'ok1 [fr]')
    }

    def canCombineTagsWithCustomExtension() {
        Assume.assumeTrue(isJupiter())

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeLocaleTests')
        result.testClass("org.gradle.SomeLocaleTests").assertTestCount(3, 0, 0)
        result.testClass("org.gradle.SomeLocaleTests").assertTestsExecuted(
            result.testCase('ok1(Locale)[1]', 'French'),
            result.testCase('ok1(Locale)[2]', 'German'),
            result.testCase('ok1(Locale)[3]', 'English')
        )
    }

    def canRunParameterizedTestsWithCategories() {
        Assume.assumeFalse(isJupiter())

        when:
        run('test')

        then:
        def expectedTestClasses = ['org.gradle.NestedTestsWithCategories$TagOnMethodNoParam', 'org.gradle.NestedTestsWithCategories$TagOnMethod']
        if (isVintage() || !(version in ['4.10', '4.11', '4.12'])) {
            expectedTestClasses << 'org.gradle.NestedTestsWithCategories$TagOnClass'
        }
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted(expectedTestClasses as String[])
        expectedTestClasses.each {
            result.testClass(it).assertTestCount(1, 0, 0)
        }
    }

    def canRunParameterizedTestsWithTags() {
        Assume.assumeTrue(isJupiter())

        when:
        run('test')

        then:
        def expectedTestClasses = ['org.gradle.NestedTestsWithTags$TagOnMethodNoParam', 'org.gradle.NestedTestsWithTags$TagOnMethod', 'org.gradle.NestedTestsWithTags$TagOnClass']
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted(expectedTestClasses as String[])
        expectedTestClasses.each {
            result.testClass(it).assertTestCount(1, 0, 0)
        }
    }

    def warningWhenSpecifyingConflictingIncludeAndExcludeCategory() {
        when:
        run('test')

        then:
        assertOutputContainsCategoryOrTagWarning('org.gradle.CategoryC')

        and:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        if (isJupiter()) {
            result.assertTestClassesExecuted('org.gradle.CatATests', 'org.gradle.CatADTests', 'org.gradle.MixedTests')
            result.testClass("org.gradle.CatATests").assertTestCount(4, 0, 0)
            result.testClass("org.gradle.CatATests").assertTestsExecuted('catAOk1', 'catAOk2', 'catAOk3', 'catAOk4')
            result.testClass("org.gradle.CatADTests").assertTestCount(3, 0, 0)
            result.testClass("org.gradle.CatADTests").assertTestsExecuted('catAOk1', 'catAOk2', 'catDOk4')
            result.testClass("org.gradle.MixedTests").assertTestCount(2, 0, 0)
            result.testClass("org.gradle.MixedTests").assertTestsExecuted('catAOk1')
            result.testClass("org.gradle.MixedTests").assertTestsSkipped('someIgnoredTest')
        } else {
            result.assertTestClassesExecuted('org.gradle.CatATests', 'org.gradle.CatBTests', 'org.gradle.CatADTests', 'org.gradle.MixedTests')
            result.testClass("org.gradle.CatATests").assertTestCount(4, 0, 0)
            result.testClass("org.gradle.CatATests").assertTestsExecuted('catAOk1', 'catAOk2', 'catAOk3', 'catAOk4')
            result.testClass("org.gradle.CatBTests").assertTestCount(4, 0, 0)
            result.testClass("org.gradle.CatBTests").assertTestsExecuted('catBOk1', 'catBOk2', 'catBOk3', 'catBOk4')
            result.testClass("org.gradle.CatADTests").assertTestCount(2, 0, 0)
            result.testClass("org.gradle.CatADTests").assertTestsExecuted('catAOk1', 'catAOk2')
            result.testClass("org.gradle.MixedTests").assertTestCount(3, 0, 0)
            result.testClass("org.gradle.MixedTests").assertTestsExecuted('catAOk1', 'catBOk2')
            result.testClass("org.gradle.MixedTests").assertTestsSkipped('someIgnoredTest')
        }

    }

    def warningWhenSpecifyingMultipleConflictingIncludeAndExcludeCategories() {
        when:
        run('test')

        then:
        assertOutputContainsCategoryOrTagWarning('org.gradle.CategoryA', 'org.gradle.CategoryC')

        and:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertNoTestClassesExecuted()
    }

    private void assertOutputContainsCategoryOrTagWarning(String... categories) {
        String singular
        String plural
        if (isJUnitPlatform()) {
            singular = "tag"
            plural = "tags"
        } else {
            singular = "category"
            plural = "categories"
        }

        if (categories.size() == 1) {
            outputContains("The ${singular} '${categories[0]}' is both included and excluded.")
        } else {
            String allCategories = categories.collect {"'${it}'" }.join(", ")
            outputContains("The ${plural} ${allCategories} are both included and excluded.")
        }
    }
}
