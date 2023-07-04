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
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

abstract class AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec extends AbstractTestingMultiVersionIntegrationTest {
    TestSourceFixture testSources = new TestSourceFixture()

    abstract TestSourceGenerator getTestSourceGenerator()
    abstract String getSingularCategoryOrTagName()
    abstract String getPluralCategoryOrTagName()

    def setup() {
        executer.noExtraLogging()
        buildFile << """
            apply plugin: "java"

            repositories {
                mavenCentral()
            }

            dependencies {
                ${testFrameworkDependencies}
            }
        """
    }

    def "can exclude categories or tags only"() {
        given:
        testSources.with {
            testClass('CategoryATests')
                .withCategoryOrTag('CategoryA')
                .with {
                    testMethod('catAOk1').shouldPass()
                    testMethod('catAOk2').shouldPass()
                    testMethod('catAOk3').shouldPass()
                    testMethod('catAOk4').shouldPass()
                }
            testClass('NoCatTests')
                .with {
                    testMethod('noCatOk1').shouldPass()
                    testMethod('noCatOk2').shouldPass()
                }
            testClass('SomeOtherCatTests')
                .withCategoryOrTag('SomeOtherCat')
                .with {
                    testMethod('someOtherOk1').shouldPass()
                    testMethod('someOtherOk2').shouldPass()
                }
            testClass('SomeTests')
                .with {
                    testMethod('catAOk1').withCategoryOrTag('CategoryA').shouldPass()
                    testMethod('someOtherCatOk2').withCategoryOrTag('SomeOtherCat').shouldPass()
                    testMethod('noCatOk3').shouldPass()
                    testMethod('noCatOk4').shouldPass()
                }

            testCategory('CategoryA')
            testCategory('SomeOtherCat')
        }
        testSourceGenerator.writeAllSources(testSources)

        buildFile << """
            test {
                ${configureTestFramework} {
                    ${excludeCategoryOrTag('CategoryA')}
                }
            }
        """

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('NoCatTests', 'SomeTests', 'SomeOtherCatTests')
        result.testClass("SomeOtherCatTests").assertTestCount(2, 0, 0)
        result.testClass("SomeOtherCatTests").assertTestsExecuted('someOtherOk1', 'someOtherOk2')
        result.testClass("NoCatTests").assertTestCount(2, 0, 0)
        result.testClass("NoCatTests").assertTestsExecuted('noCatOk1', 'noCatOk2')
        result.testClass("SomeTests").assertTestCount(3, 0, 0)
        result.testClass("SomeTests").assertTestsExecuted('noCatOk3', 'noCatOk4', 'someOtherCatOk2')
    }

    def "can include categories or tags only"() {
        given:
        testSources.with {
            testClass('CategoryATests')
                .withCategoryOrTag('CategoryA')
                .with {
                    testMethod('catAOk1').shouldPass()
                    testMethod('catAOk2').shouldPass()
                    testMethod('catAOk3').shouldPass()
                    testMethod('catAOk4').shouldPass()
                }
            testClass('NoCatTests')
                .with {
                    testMethod('noCatOk1').shouldPass()
                    testMethod('noCatOk2').shouldPass()
                }
            testClass('SomeOtherCatTests')
                .withCategoryOrTag('SomeOtherCat')
                .with {
                    testMethod('someOtherOk1').shouldPass()
                    testMethod('someOtherOk2').shouldPass()
                }
            testClass('SomeTests')
                .with {
                    testMethod('catAOk1').withCategoryOrTag('CategoryA').shouldPass()
                    testMethod('someOtherCatOk2').withCategoryOrTag('SomeOtherCat').shouldPass()
                    testMethod('noCatOk3').shouldPass()
                    testMethod('noCatOk4').shouldPass()
                }

            testCategory('CategoryA')
            testCategory('SomeOtherCat')
        }
        testSourceGenerator.writeAllSources(testSources)

        buildFile << """
            test {
                ${configureTestFramework} {
                    ${includeCategoryOrTag('CategoryA')}
                }
            }
        """

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('CategoryATests', 'SomeTests')
        result.testClass("CategoryATests").assertTestCount(4, 0, 0)
        result.testClass("CategoryATests").assertTestsExecuted('catAOk1', 'catAOk2', 'catAOk3', 'catAOk4')
        result.testClass("SomeTests").assertTestCount(1, 0, 0)
        result.testClass("SomeTests").assertTestsExecuted('catAOk1')
    }

    def "emits warning when specifying a conflicting include/exclude"() {
        given:
        testSources.with {
            testClass('CategoryATests')
                .withCategoryOrTag('CategoryA')
                .with {
                    testMethod('catAOk1').shouldPass()
                    testMethod('catAOk2').shouldPass()
                    testMethod('catAOk3').shouldPass()
                    testMethod('catAOk4').shouldPass()
                }
            testClass('CategoryCTests')
                .withCategoryOrTag('CategoryC')
                .with {
                    testMethod('catCOk1').shouldPass()
                    testMethod('catCOk2').shouldPass()
                    testMethod('catCOk3').shouldPass()
                    testMethod('catCOk4').shouldPass()
                }
            testCategory('CategoryA')
            testCategory('CategoryC')
        }
        testSourceGenerator.writeAllSources(testSources)

        buildFile << """
            test {
                ${configureTestFramework} {
                    ${includeCategoryOrTag('CategoryA')}
                    ${includeCategoryOrTag('CategoryC')}
                    ${excludeCategoryOrTag('CategoryC')}
                }
            }
        """

        when:
        run('test')

        then:
        assertOutputContainsCategoryOrTagWarning('CategoryC')

        and:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('CategoryATests')
        result.testClass("CategoryATests").assertTestCount(4, 0, 0)
        result.testClass("CategoryATests").assertTestsExecuted('catAOk1', 'catAOk2', 'catAOk3', 'catAOk4')
    }

    def "emits warning when specifying multiple conflicting includes/excludes"() {
        given:
        testSources.with {
            testClass('CategoryATests')
                .withCategoryOrTag('CategoryA')
                .with {
                    testMethod('catAOk1').shouldPass()
                    testMethod('catAOk2').shouldPass()
                    testMethod('catAOk3').shouldPass()
                    testMethod('catAOk4').shouldPass()
                }
            testClass('CategoryCTests')
                .withCategoryOrTag('CategoryC')
                .with {
                    testMethod('catCOk1').shouldPass()
                    testMethod('catCOk2').shouldPass()
                    testMethod('catCOk3').shouldPass()
                    testMethod('catCOk4').shouldPass()
                }
            testCategory('CategoryA')
            testCategory('CategoryC')
        }
        testSourceGenerator.writeAllSources(testSources)

        buildFile << """
            test {
                ${configureTestFramework} {
                    ${includeCategoryOrTag('CategoryA')}
                    ${includeCategoryOrTag('CategoryC')}
                    ${excludeCategoryOrTag('CategoryA')}
                    ${excludeCategoryOrTag('CategoryC')}
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("No test executed. This behavior has been deprecated. " +
            "This will fail with an error in Gradle 9.0. There are test sources present but no test was executed. Please check your test configuration. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_fail_on_no_test_executed")
        run('test')

        then:
        assertOutputContainsCategoryOrTagWarning('CategoryA', 'CategoryC')

        and:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertNoTestClassesExecuted()
    }

    void assertOutputContainsCategoryOrTagWarning(String... categories) {
        if (categories.size() == 1) {
            outputContains("The ${singularCategoryOrTagName} '${categories[0]}' is both included and excluded.")
        } else {
            String allCategories = categories.collect {"'${it}'" }.join(", ")
            outputContains("The ${pluralCategoryOrTagName} ${allCategories} are both included and excluded.")
        }
    }

    /**
     * Fixture for generating test classes from a given test source fixture.  This should be implemented by subclasses that generate
     * unique test sources (e.g. JUnit4 vs JUnit5).
     */
    interface TestSourceGenerator {
        void writeAllSources(TestSourceFixture fixture)
    }

    /**
     * Fixture for capturing test source requirements.  This will be provided to a {@link TestSourceGenerator} to generate the test sources.
     */
    class TestSourceFixture {
        List<TestClass> testClasses = []
        List<Category> categories = []
        List<TestSource> sources = []

        /**
         * Add a new simple test class to the sources.
         */
        TestClass testClass(String name, String sourceSet = 'test') {
            TestClass testClass = new TestClass(name, sourceSet)
            testClasses << testClass
            return testClass
        }

        /**
         * Add a JUnit4 category class to the sources.  Note that these are not used by JUnit5 test generators.
         */
        Category testCategory(String name, String sourceSet = 'test') {
            Category category = new Category(name, sourceSet)
            categories << category
            return category
        }

        /**
         * Add an arbitrary test source file to the sources.  This can be used to add supplementary classes or complex test classes to the sources.
         */
        TestSource sourceFile(String relativePath, String sourceSet = 'test') {
            TestSource source = new TestSource(relativePath, sourceSet)
            sources << source
            return source
        }
    }

    /**
     * Fixture for capturing simple test class requirements.  Note that this class should be used only for simple test classes
     * and should not be enhanced to capture complex test classes with arbitrary features.  Complex test classes should be captured
     * with a raw {@link TestSource} fixture.
     */
    class TestClass {
        final String name
        final String packageName
        final String sourceSet

        List<String> categoriesOrTags = []
        List<TestMethod> methods = []

        TestClass(String name, String sourceSet) {
            this.name = name.substring(name.lastIndexOf('.') + 1)
            this.packageName = name.contains('.') ? name.substring(0, name.lastIndexOf('.')) : ''
            this.sourceSet = sourceSet
        }

        TestClass withCategoryOrTag(String name) {
            categoriesOrTags << name
            return this
        }

        TestMethod testMethod(String name) {
            TestMethod method = new TestMethod(name)
            methods << method
            return method
        }
    }

    /**
     * Fixture for capturing simple test method requirements.
     */
    class TestMethod {
        final String name
        boolean shouldPass = true
        boolean isIgnoredOrDisabled
        List<String> categoriesOrTags = []

        TestMethod(name) {
            this.name = name
        }

        TestMethod withCategoryOrTag(String name) {
            categoriesOrTags << name
            return this
        }

        TestMethod shouldPass() {
            shouldPass = true
            return this
        }

        TestMethod ignoreOrDisable() {
            isIgnoredOrDisabled = true
            return this
        }

        TestMethod shouldFail() {
            shouldPass = false
            return this
        }
    }

    /**
     * Fixture for capturing test categories for JUnit4.
     */
    class Category {
        final String name
        final String packageName
        final String sourceSet
        List<String> extendsCategories = []

        Category(String name, String sourceSet) {
            this.name = name.substring(name.lastIndexOf('.') + 1)
            this.packageName = name.contains('.') ? name.substring(0, name.lastIndexOf('.')) : ''
            this.extendsCategories = extendsCategories
            this.sourceSet = sourceSet
        }

        Category extendsCategory(String name) {
            extendsCategories << name
            return this
        }
    }

    /**
     * Fixture for capturing raw test source files.  This should be used for complex test classes that cannot be captured with {@link TestClass}.
     */
    class TestSource {
        final String relativePath
        final String sourceSet
        String source

        TestSource(String relativePath, String sourceSet) {
            this.relativePath = relativePath
            this.source = source
            this.sourceSet = sourceSet
        }

        TestSource withSource(String source) {
            this.source = source
            return this
        }
    }
}
