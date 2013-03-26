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
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.TestResources
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@TargetVersions(['4.8', '4.11'])
public class JUnitCategoriesCrossVersionIntegrationSpec extends MultiVersionIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    String junitDependency = "junit:junit:$version"

    @Before
    public void before() {
        executer.noExtraLogging()
    }

    private void configureJUnit() {
        buildFile << """
        dependencies {
        testCompile '${junitDependency.toString()}'
        }"""
    }

    @Test
    public void canSpecifyIncludeAndExcludeCategories() {
        given:
        configureJUnit();
        when:
        executer.withTasks('test').run();
        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
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

    @Test
    public void canSpecifyExcludesOnly() {
        given:
        configureJUnit();
        when:
        executer.withTasks('test').run();
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

    @Test
    public void canCombineCategoriesWithCustomRunner() {
        given:
        configureJUnit();
        when:
        executer.withTasks('test').run();
        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeLocaleTests')
        result.testClass("org.gradle.SomeLocaleTests").assertTestCount(3, 0, 0)
        result.testClass("org.gradle.SomeLocaleTests").assertTestsExecuted('ok1 [de]', 'ok1 [en]', 'ok1 [fr]')
    }
}
