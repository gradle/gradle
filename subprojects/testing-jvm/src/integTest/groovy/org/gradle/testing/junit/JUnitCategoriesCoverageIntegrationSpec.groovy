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
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.CATEGORIES
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE

@TargetCoverage({ CATEGORIES + JUNIT_VINTAGE })
class JUnitCategoriesCoverageIntegrationSpec extends JUnitMultiVersionIntegrationSpec {

    @Rule TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        executer.noExtraLogging()
    }

    def configureJUnit() {
        buildFile << "dependencies { testCompile '$dependencyNotation' }"
    }

    def canSpecifyIncludeAndExcludeCategories() {
        configureJUnit()

        when:
        run('test')

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

    def canSpecifyExcludesOnly() {
        configureJUnit()

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

    @Issue('https://github.com/gradle/gradle/issues/1153')
    def canSpecifyIncludeAndExcludeCategoriesWithParameterized() {
        given:
        resources.maybeCopy('JUnitCategoriesCoverageIntegrationSpec/canSpecifyIncludeAndExcludeCategories')
        configureJUnit()
        rewriteTestWithParameterized()

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.CatATests', 'org.gradle.CatBTests', 'org.gradle.CatADTests', 'org.gradle.MixedTests')
        result.testClass("org.gradle.CatATests").assertTestCount(4, 0, 0)
        result.testClass("org.gradle.CatATests").assertTestsExecuted('catAOk1[0]', 'catAOk2[0]', 'catAOk3[0]', 'catAOk4[0]')
        result.testClass("org.gradle.CatBTests").assertTestCount(4, 0, 0)
        result.testClass("org.gradle.CatBTests").assertTestsExecuted('catBOk1[0]', 'catBOk2[0]', 'catBOk3[0]', 'catBOk4[0]')
        result.testClass("org.gradle.CatADTests").assertTestCount(2, 0, 0)
        result.testClass("org.gradle.CatADTests").assertTestsExecuted('catAOk1[0]', 'catAOk2[0]')
        result.testClass("org.gradle.MixedTests").assertTestCount(3, 0, 0)
        result.testClass("org.gradle.MixedTests").assertTestsExecuted('catAOk1[0]', 'catBOk2[0]')
        result.testClass("org.gradle.MixedTests").assertTestsSkipped('someIgnoredTest[0]')
    }

    @Issue('https://github.com/gradle/gradle/issues/1153')
    def canSpecifyExcludesOnlyWithParameterized() {
        given:
        resources.maybeCopy('JUnitCategoriesCoverageIntegrationSpec/canSpecifyExcludesOnly')
        configureJUnit()
        rewriteTestWithParameterized()

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.NoCatTests', 'org.gradle.SomeTests', 'org.gradle.SomeOtherCatTests')
        result.testClass("org.gradle.SomeOtherCatTests").assertTestCount(2, 0, 0)
        result.testClass("org.gradle.SomeOtherCatTests").assertTestsExecuted('someOtherOk1[0]', 'someOtherOk2[0]')
        result.testClass("org.gradle.NoCatTests").assertTestCount(2, 0, 0)
        result.testClass("org.gradle.NoCatTests").assertTestsExecuted('noCatOk1[0]', 'noCatOk2[0]')
        result.testClass("org.gradle.SomeTests").assertTestCount(3, 0, 0)
        result.testClass("org.gradle.SomeTests").assertTestsExecuted('noCatOk3[0]', 'noCatOk4[0]', 'someOtherCatOk2[0]')
    }


    /*
    This method converts a non-parameterized test class to the parameterized version without changing its behaviour
    For example,

    package org.gradle;
    public class CatTests {
        @Test public void ok() {}
    }

    will be converted to

    package org.gradle;
    import org.junit.runners.Parameterized;
    import org.junit.runner.RunWith;
    import java.util.Arrays;

    @RunWith(Parameterized.class)
    public class CatTests {
        @Parameterized.Parameter
        public String name;

        @Parameterized.Parameters
        public static Iterable<Object[]> getParameters() { return Arrays.asList(new Object[] { "1" }); }

        @Test public void ok() {}
    }
    */

    def rewriteTestWithParameterized() {
        // @Parameterized.Parameter is unavailable in 4.8
        Assume.assumeTrue(version.toString() != '4.8')
        file('src/test/java/org/gradle').listFiles().findAll { it.name.endsWith('Tests.java') }.each {
            String text = it.text
            text = text.replace('package org.gradle;', '''package org.gradle;
                                                                       import org.junit.runners.Parameterized;
                                                                       import org.junit.runner.RunWith;
                                                                       import java.util.Arrays;''')
            text = text.replace('public class', '@RunWith(Parameterized.class) public class')
            text = text.replace('Tests {', '''Tests {
                                                            @Parameterized.Parameter
                                                            public String name;

                                                            @Parameterized.Parameters
                                                            public static Iterable<Object[]> getParameters() { return Arrays.<Object[]>asList(new Object[] { "1" }); }''')
            it.text = text
        }
    }

    def canCombineCategoriesWithCustomRunner() {
        configureJUnit()

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeLocaleTests')
        result.testClass("org.gradle.SomeLocaleTests").assertTestCount(3, 0, 0)
        result.testClass("org.gradle.SomeLocaleTests").assertTestsExecuted('ok1 [de]', 'ok1 [en]', 'ok1 [fr]')
    }
}
