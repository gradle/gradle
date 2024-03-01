/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.junit.AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec

/**
 * Base class for JUnit 4 category/tag coverage integration tests.  Provides JUnit4-specific tests and test sources for both JUnit4 and JUnit Vintage.
 */
abstract class AbstractJUnit4CategoriesOrTagsCoverageIntegrationTest extends AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec {
    @Override
    TestSourceGenerator getTestSourceGenerator() {
        return new JUnit4TestSourceGenerator()
    }

    abstract boolean supportsCategoryOnNestedClass()

    def "can specify both includes and excludes for categories"() {
        given:
        testSources.with {
            testClass('CategoryACTests')
                .withCategoryOrTag('CategoryA')
                .withCategoryOrTag('CategoryC')
                .with {
                    testMethod('catACOk1').shouldPass()
                    testMethod('catACOk2').shouldPass()
                    testMethod('catACOk3').shouldPass()
                    testMethod('catACOk4').shouldPass()
                }
            testClass('CategoryADTests')
                .withCategoryOrTag('CategoryA')
                .with {
                    testMethod('catAOk1').shouldPass()
                    testMethod('catAOk2').shouldPass()
                    testMethod('catCOk3').withCategoryOrTag('CategoryC').shouldPass()
                    testMethod('catDOk4').withCategoryOrTag('CategoryD').shouldPass()
                }
            testClass('CategoryATests')
                .withCategoryOrTag('CategoryA')
                .with {
                    testMethod('catAOk1').shouldPass()
                    testMethod('catAOk2').shouldPass()
                    testMethod('catAOk3').shouldPass()
                    testMethod('catAOk4').shouldPass()
                }
            testClass('CategoryBTests')
                .withCategoryOrTag('CategoryB')
                .with {
                    testMethod('catBOk1').shouldPass()
                    testMethod('catBOk2').shouldPass()
                    testMethod('catBOk3').shouldPass()
                    testMethod('catBOk4').shouldPass()
                }
            testClass('CategoryCBTests')
                .withCategoryOrTag('CategoryC')
                .with {
                    testMethod('catCOk1').shouldPass()
                    testMethod('catCOk2').shouldPass()
                    testMethod('catAOk3').shouldPass()
                    testMethod('catBOk4').withCategoryOrTag('CategoryB').shouldPass()
                }
            testClass('CategoryCTests')
                .withCategoryOrTag('CategoryC')
                .with {
                    testMethod('catCOk1').shouldPass()
                    testMethod('catCOk2').shouldPass()
                    testMethod('catCOk3').shouldPass()
                    testMethod('catCOk4').shouldPass()
                }
            testClass('CategoryDTests')
                .withCategoryOrTag('CategoryD')
                .with {
                    testMethod('catDOk1').shouldPass()
                    testMethod('catDOk2').shouldPass()
                    testMethod('catDOk3').shouldPass()
                    testMethod('catDOk4').shouldPass()
                }
            testClass('CategoryZTests')
                .withCategoryOrTag('CategoryZ')
                .with {
                    testMethod('catZOk1').shouldPass()
                    testMethod('catZOk2').shouldPass()
                    testMethod('catZOk3').shouldPass()
                    testMethod('catZOk4').shouldPass()
                }
            testClass('MixedTests')
                .with {
                    testMethod('catAOk1').withCategoryOrTag('CategoryA').shouldPass()
                    testMethod('catBOk2').withCategoryOrTag('CategoryB').shouldPass()
                    testMethod('ignoredWithCategoryA').withCategoryOrTag('CategoryA').ignoreOrDisable()
                    testMethod('noCatOk4').shouldPass()
                }
            testClass('NoCategoryTests')
                .with {
                    testMethod('noCatOk1').shouldPass()
                    testMethod('noCatOk2').shouldPass()
                    testMethod('noCatOk3').shouldPass()
                    testMethod('noCatOk4').shouldPass()
                }
            testCategory('CategoryA')
            testCategory('CategoryB').extendsCategory('CategoryA')
            testCategory('CategoryC')
            testCategory('CategoryD').extendsCategory('CategoryC')
            testCategory('CategoryZ')
        }
        testSourceGenerator.writeAllSources(testSources)

        buildFile << """
            test {
                ${configureTestFramework} {
                    ${includeCategoryOrTag('CategoryA')}
                    ${excludeCategoryOrTag('CategoryC')}
                }
            }
        """

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('CategoryATests', 'CategoryBTests', 'CategoryADTests', 'MixedTests')
        result.testClass("CategoryATests").assertTestCount(4, 0, 0)
        result.testClass("CategoryATests").assertTestsExecuted('catAOk1', 'catAOk2', 'catAOk3', 'catAOk4')
        result.testClass("CategoryBTests").assertTestCount(4, 0, 0)
        result.testClass("CategoryBTests").assertTestsExecuted('catBOk1', 'catBOk2', 'catBOk3', 'catBOk4')
        result.testClass("CategoryADTests").assertTestCount(2, 0, 0)
        result.testClass("CategoryADTests").assertTestsExecuted('catAOk1', 'catAOk2')
        result.testClass("MixedTests").assertTestCount(3, 0, 0)
        result.testClass("MixedTests").assertTestsExecuted('catAOk1', 'catBOk2')
        result.testClass("MixedTests").assertTestsSkipped('ignoredWithCategoryA')
    }

    def "can combine categories with custom runner"() {
        given:
        testSources.with {
            testCategory('CategoryA')
            sourceFile('LocaleHolder.java').withSource("""
                import java.util.Locale;

                class LocaleHolder {
                    private static Locale locale;

                    public static Locale set(Locale locale) {
                        Locale old = LocaleHolder.locale;
                        LocaleHolder.locale = locale;
                        return old;
                    }
                    public static Locale get() {
                        return locale;
                    }
                }
            """)
            sourceFile('Locales.java').withSource("""
                import org.junit.runner.Runner;
                import org.junit.runners.BlockJUnit4ClassRunner;
                import org.junit.runners.Suite;
                import org.junit.runners.model.FrameworkMethod;
                import org.junit.runners.model.InitializationError;
                import org.junit.runners.model.Statement;

                import java.util.ArrayList;
                import java.util.Arrays;
                import java.util.List;
                import java.util.Locale;

                public class Locales extends Suite {
                    private static final Iterable<Locale> localesToUse = Arrays.asList(Locale.FRENCH, Locale.GERMAN, Locale.ENGLISH);

                    public Locales(Class<?> klass) throws InitializationError {
                        super(klass, extractAndCreateRunners(klass));
                    }

                    private static List<Runner> extractAndCreateRunners(Class<?> klass) throws InitializationError {
                        List<Runner> runners = new ArrayList<Runner>();
                        for (Locale locale : localesToUse) {
                            runners.add(new LocalesRunner(locale, klass));
                        }
                        return runners;
                    }

                    private static class LocalesRunner extends BlockJUnit4ClassRunner {
                        private final Locale locale;

                        LocalesRunner(Locale locale, Class<?> klass) throws InitializationError {
                            super(klass);
                            this.locale = locale;
                        }

                        @Override
                        protected Statement methodBlock(final FrameworkMethod method) {
                            return new Statement() {
                                @Override
                                public void evaluate() throws Throwable {
                                    Locale oldLocale = LocaleHolder.set(locale);
                                    try {
                                        LocalesRunner.super.methodBlock(method).evaluate();
                                    } finally {
                                        LocaleHolder.set(oldLocale);
                                    }
                                }
                            };
                        }

                        @Override// The name of the test class
                        protected String getName() {
                            return String.format("%s [%s]", super.getName(), locale);
                        }

                        @Override// The name of the test method
                        protected String testName(final FrameworkMethod method) {
                            return String.format("%s [%s]", method.getName(), locale);
                        }
                    }

                }
            """)
            sourceFile("SomeLocaleTests.java").withSource("""
                import org.junit.Test;
                import org.junit.experimental.categories.Category;
                import org.junit.runner.RunWith;

                @RunWith(Locales.class)
                public class SomeLocaleTests {
                    @Test
                    public void ok1() {
                        System.out.println("Locale in use: " + LocaleHolder.get());
                    }

                    @Test
                    @Category(CategoryA.class)
                    public void ok2() {
                        System.out.println("Locale in use: " + LocaleHolder.get());
                    }
                }
            """)
            sourceFile("SomeMoreLocaleTests.java").withSource("""
                import org.junit.Test;
                import org.junit.experimental.categories.Category;
                import org.junit.runner.RunWith;

                @RunWith(Locales.class)
                @Category(CategoryA.class)
                public class SomeMoreLocaleTests {
                    @Test
                    public void someMoreTest1() {
                        System.out.println("Locale in use: " + LocaleHolder.get());
                    }

                    @Test
                    public void someMoreTest2() {
                        System.out.println("Locale in use: " + LocaleHolder.get());
                    }
                }
            """)
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
        result.assertTestClassesExecuted('SomeLocaleTests')
        result.testClass("SomeLocaleTests").assertTestCount(3, 0, 0)
        result.testClass("SomeLocaleTests").assertTestsExecuted('ok1 [de]', 'ok1 [en]', 'ok1 [fr]')
    }

    def "can run parameterized tests with categories"() {
        given:
        testSources.with {
            testCategory('SomeCategory')
            sourceFile("NestedTestsWithCategories.java").withSource("""
                import org.junit.Test;
                import org.junit.experimental.categories.Category;
                import org.junit.runner.RunWith;
                import org.junit.runners.Parameterized;

                import java.util.ArrayList;

                import static org.junit.Assert.fail;

                public class NestedTestsWithCategories {

                    @Category(SomeCategory.class)
                    @RunWith(Parameterized.class)
                    public static class TagOnClass {
                        @Parameterized.Parameters
                        public static Iterable<Object[]> getParameters() {
                            ArrayList<Object[]> parameters = new ArrayList<>();
                            parameters.add(new Object[] { "tag on class" });
                            return parameters;
                        }

                        private final String param;

                        public TagOnClass(String param) {
                            this.param = param;
                        }

                        @Test
                        public void run() {
                            System.err.println("executed " + param);
                        }
                    }

                    @RunWith(Parameterized.class)
                    public static class TagOnMethod {
                        @Parameterized.Parameters
                        public static Iterable<Object[]> getParameters() {
                            ArrayList<Object[]> parameters = new ArrayList<>();
                            parameters.add(new Object[] { "tag on method" });
                            return parameters;
                        }

                        private final String param;

                        public TagOnMethod(String param) {
                            this.param = param;
                        }

                        @Test
                        @Category(SomeCategory.class)
                        public void run() {
                            System.err.println("executed " + param);
                        }

                        @Test
                        public void filteredOut() {
                            throw new AssertionError("should be filtered out");
                        }
                    }

                    public static class TagOnMethodNoParam {
                        @Test
                        @Category(SomeCategory.class)
                        public void run() {
                            System.err.println("executed tag on method (no param)");
                        }

                        @Test
                        public void filteredOut() {
                            throw new AssertionError("should be filtered out");
                        }
                    }

                    @RunWith(Parameterized.class)
                    public static class Untagged {
                        @Parameterized.Parameters
                        public static Iterable<Object[]> getParameters() {
                            ArrayList<Object[]> parameters = new ArrayList<>();
                            parameters.add(new Object[] { "untagged" });
                            return parameters;
                        }

                        private final String param;

                        public Untagged(String param) {
                            this.param = param;
                        }

                        @Test
                        public void run() {
                            System.err.println("executed " + param);
                        }
                    }
                }
            """)
        }
        testSourceGenerator.writeAllSources(testSources)

        buildFile << """
            test {
                ${configureTestFramework} {
                    ${includeCategoryOrTag('SomeCategory')}
                }
            }
        """

        when:
        run('test')

        then:
        def expectedTestClasses = ['NestedTestsWithCategories$TagOnMethodNoParam', 'NestedTestsWithCategories$TagOnMethod']
        if (supportsCategoryOnNestedClass()) {
            expectedTestClasses << 'NestedTestsWithCategories$TagOnClass'
        }
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted(expectedTestClasses as String[])
        expectedTestClasses.each {
            result.testClass(it).assertTestCount(1, 0, 0)
        }
    }

    private class JUnit4TestSourceGenerator implements AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.TestSourceGenerator {
        @Override
        void writeAllSources(AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.TestSourceFixture fixture) {
            fixture.testClasses.each { testClass ->
                writeTestClass(testClass)
            }
            fixture.categories.each { category ->
                writeCategoryClass(category)
            }
            fixture.sources.each { source ->
                writeSourceFile(source)
            }
        }

        private TestFile writeTestClass(AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.TestClass testClass) {
            String packagePath = testClass.packageName.replace('.', '/')
            testDirectory.file("src/${testClass.sourceSet}/java/${packagePath}/${testClass.name}.java") << """
                    ${testClass.packageName}

                    import org.junit.Test;
                    import org.junit.Ignore;
                    import org.junit.experimental.categories.Category;

                    ${categoryAnnotation(testClass.categoriesOrTags)}
                    public class ${testClass.name} {
                        ${testClass.methods.collect { generateTestMethod(it) }.join('\n')}
                    }
                """.stripIndent()
        }

        private TestFile writeCategoryClass(AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.Category category) {
            String packagePath = category.packageName.replace('.', '/')
            testDirectory.file("src/${category.sourceSet}/java/${packagePath}/${category.name}.java") << """
                    ${category.packageName}

                    import org.junit.experimental.categories.Category;

                    public interface ${category.name} ${!category.extendsCategories.isEmpty() ? "extends ${category.extendsCategories.join(', ')}" : ""} {
                    }
                """.stripIndent()
        }

        private TestFile writeSourceFile(AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.TestSource testSource) {
            testDirectory.file("src/test/java/${testSource.relativePath}") << testSource.source.stripIndent()
        }

        private String categoryAnnotation(List<String> categories) {
            if (!categories.isEmpty()) {
                return "@Category({${categories.collect { it + '.class' }.join(', ')}})"
            } else {
                return ""
            }
        }

        private String generateTestMethod(AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.TestMethod method) {
            return """
                @Test
                ${method.isIgnoredOrDisabled ? "@Ignore" : ""}
                ${categoryAnnotation(method.categoriesOrTags)}
                public void ${method.name}() {
                    ${method.shouldPass ? "assert true;" : "assert false;"}
                }
            """
        }
    }
}
