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

package org.gradle.testing.junit.jupiter

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.junit.AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER

@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterCategoriesOrTagsCoverageIntegrationTest extends AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec implements JUnitJupiterMultiVersionTest {
    String singularCategoryOrTagName = "tag"
    String pluralCategoryOrTagName = "tags"

    def "can specify include and exclude tags"() {
        given:
        testSources.with {
            testClass('TagACTests')
                .withCategoryOrTag('TagA')
                .withCategoryOrTag('TagC')
                .with {
                    testMethod('tagACOk1').shouldPass()
                    testMethod('tagACOk2').shouldPass()
                    testMethod('tagACOk3').shouldPass()
                    testMethod('tagACOk4').shouldPass()
                }
            testClass('TagADTests')
                .withCategoryOrTag('TagA')
                .with {
                    testMethod('tagAOk1').shouldPass()
                    testMethod('tagAOk2').shouldPass()
                    testMethod('tagCOk3').withCategoryOrTag('TagC').shouldPass()
                    testMethod('tagDOk4').withCategoryOrTag('TagD').shouldPass()
                }
            testClass('TagATests')
                .withCategoryOrTag('TagA')
                .with {
                    testMethod('tagAOk1').shouldPass()
                    testMethod('tagAOk2').shouldPass()
                    testMethod('tagAOk3').shouldPass()
                    testMethod('tagAOk4').shouldPass()
                }
            testClass('TagBTests')
                .withCategoryOrTag('TagB')
                .with {
                    testMethod('tagBOk1').shouldPass()
                    testMethod('tagBOk2').shouldPass()
                    testMethod('tagBOk3').shouldPass()
                    testMethod('tagBOk4').shouldPass()
                }
            testClass('TagCBTests')
                .withCategoryOrTag('TagC')
                .with {
                    testMethod('tagCOk1').shouldPass()
                    testMethod('tagCOk2').shouldPass()
                    testMethod('tagAOk3').shouldPass()
                    testMethod('tagBOk4').withCategoryOrTag('TagB').shouldPass()
                }
            testClass('TagCTests')
                .withCategoryOrTag('TagC')
                .with {
                    testMethod('tagCOk1').shouldPass()
                    testMethod('tagCOk2').shouldPass()
                    testMethod('tagCOk3').shouldPass()
                    testMethod('tagCOk4').shouldPass()
                }
            testClass('TagDTests')
                .withCategoryOrTag('TagD')
                .with {
                    testMethod('tagDOk1').shouldPass()
                    testMethod('tagDOk2').shouldPass()
                    testMethod('tagDOk3').shouldPass()
                    testMethod('tagDOk4').shouldPass()
                }
            testClass('TagZTests')
                .withCategoryOrTag('TagZ')
                .with {
                    testMethod('tagZOk1').shouldPass()
                    testMethod('tagZOk2').shouldPass()
                    testMethod('tagZOk3').shouldPass()
                    testMethod('tagZOk4').shouldPass()
                }
            testClass('MixedTests')
                .with {
                    testMethod('tagAOk1').withCategoryOrTag('TagA').shouldPass()
                    testMethod('tagBOk2').withCategoryOrTag('TagB').shouldPass()
                    testMethod('ignoredWithTagA').withCategoryOrTag('TagA').ignoreOrDisable()
                    testMethod('noTagOk4').shouldPass()
                }
            testClass('NoTagTests')
                .with {
                    testMethod('noTagOk1').shouldPass()
                    testMethod('noTagOk2').shouldPass()
                    testMethod('noTagOk3').shouldPass()
                    testMethod('noTagOk4').shouldPass()
                }
        }
        testSourceGenerator.writeAllSources(testSources)

        buildFile << """
            test {
                useJUnitPlatform {
                    includeTags 'TagA'
                    excludeTags 'TagC'
                }
            }
        """

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('TagATests', 'TagADTests', 'MixedTests')
        result.testClass("TagATests").assertTestCount(4, 0, 0)
        result.testClass("TagATests").assertTestsExecuted('tagAOk1', 'tagAOk2', 'tagAOk3', 'tagAOk4')
        result.testClass("TagADTests").assertTestCount(3, 0, 0)
        result.testClass("TagADTests").assertTestsExecuted('tagAOk1', 'tagAOk2', 'tagDOk4')
        result.testClass("MixedTests").assertTestCount(2, 0, 0)
        result.testClass("MixedTests").assertTestsExecuted('tagAOk1')
        result.testClass("MixedTests").assertTestsSkipped('ignoredWithTagA')
    }

    def "can combine tags with custom extension"() {
        given:
        testSources.with {
            sourceFile("Locales.java").withSource("""
                import org.junit.jupiter.api.extension.Extension;
                import org.junit.jupiter.api.extension.ExtensionContext;
                import org.junit.jupiter.api.extension.ParameterContext;
                import org.junit.jupiter.api.extension.ParameterResolver;
                import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
                import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

                import java.util.ArrayList;
                import java.util.Arrays;
                import java.util.Collections;
                import java.util.List;
                import java.util.Locale;
                import java.util.stream.Stream;

                public class Locales implements TestTemplateInvocationContextProvider {
                    @Override
                    public boolean supportsTestTemplate(ExtensionContext context) {
                        return true;
                    }

                    @Override
                    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
                        ExtensionContext context) {

                        return Stream.of(invocationContext(Locale.FRENCH), invocationContext(Locale.GERMAN), invocationContext(Locale.ENGLISH));
                    }

                    private TestTemplateInvocationContext invocationContext(Locale locale) {
                        return new TestTemplateInvocationContext() {
                            @Override
                            public String getDisplayName(int invocationIndex) {
                                return locale.getDisplayName();
                            }

                            @Override
                            public List<Extension> getAdditionalExtensions() {
                                return Collections.singletonList(new ParameterResolver() {
                                    @Override
                                    public boolean supportsParameter(ParameterContext parameterContext,
                                                                     ExtensionContext extensionContext) {
                                        return parameterContext.getParameter().getType().equals(Locale.class);
                                    }

                                    @Override
                                    public Object resolveParameter(ParameterContext parameterContext,
                                                                   ExtensionContext extensionContext) {
                                        return locale;
                                    }
                                });
                            }
                        };
                    }
                }
            """)
            sourceFile("SomeLocaleTests.java").withSource("""
                import org.junit.jupiter.api.Tag;
                import org.junit.jupiter.api.TestTemplate;
                import org.junit.jupiter.api.extension.ExtendWith;

                import java.util.Locale;

                @ExtendWith(Locales.class)
                public class SomeLocaleTests {
                    @TestTemplate
                    public void ok1(Locale locale) {
                        System.out.println("Locale in use: " + locale);
                    }

                    @TestTemplate
                    @Tag("TagA")
                    public void ok2(Locale locale) {
                        System.out.println("Locale in use: " + locale);
                    }
                }
            """)
            sourceFile("SomeMoreLocaleTests.java").withSource("""
                import org.junit.jupiter.api.Tag;
                import org.junit.jupiter.api.TestTemplate;
                import org.junit.jupiter.api.extension.ExtendWith;

                import java.util.Locale;

                @ExtendWith(Locales.class)
                @Tag("TagA")
                public class SomeMoreLocaleTests {
                    @TestTemplate
                    public void someMoreTest1(Locale locale) {
                        System.out.println("Locale in use: " + locale);
                    }

                    @TestTemplate
                    public void someMoreTest2(Locale locale) {
                        System.out.println("Locale in use: " + locale);
                    }
                }
            """)
        }
        testSourceGenerator.writeAllSources(testSources)

        buildFile << """
            test {
                useJUnitPlatform {
                    excludeTags 'TagA'
                }
            }
        """

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('SomeLocaleTests')
        result.testClass("SomeLocaleTests").assertTestCount(3, 0, 0)
        result.testClass("SomeLocaleTests").assertTestsExecuted(
            result.testCase('ok1(Locale)[1]', 'French'),
            result.testCase('ok1(Locale)[2]', 'German'),
            result.testCase('ok1(Locale)[3]', 'English')
        )
    }

    def "can run parameterized tests with tags"() {
        given:
        testSources.with {
            sourceFile("NestedTestsWithTags.java").withSource("""
                import org.junit.jupiter.api.Tag;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.params.ParameterizedTest;
                import org.junit.jupiter.params.provider.ValueSource;

                public class NestedTestsWithTags {

                    @Tag("SomeTag")
                    public static class TagOnClass {
                        @ParameterizedTest
                        @ValueSource(strings = {"tag on class"})
                        public void run(String param) {
                            System.err.println("executed " + param);
                        }
                    }

                    public static class TagOnMethod {
                        @ParameterizedTest
                        @ValueSource(strings = {"tag on method"})
                        @Tag("SomeTag")
                        public void run(String param) {
                            System.err.println("executed " + param);
                        }

                        @Test
                        public void filteredOut() {
                            throw new AssertionError("should be filtered out");
                        }
                    }

                    public static class TagOnMethodNoParam {
                        @Test
                        @Tag("SomeTag")
                        public void run() {
                            System.err.println("executed tag on method (no param)");
                        }

                        @Test
                        public void filteredOut() {
                            throw new AssertionError("should be filtered out");
                        }
                    }

                    public static class Untagged {
                        @ParameterizedTest
                        @ValueSource(strings = {"untagged"})
                        public void run(String param) {
                            System.err.println("executed " + param);
                        }
                    }
                }
            """)
        }
        testSourceGenerator.writeAllSources(testSources)

        buildFile << """
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter-params")
            }
            test {
                useJUnitPlatform {
                    includeTags 'SomeTag'
                }
            }
        """

        when:
        run('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('NestedTestsWithTags$TagOnMethodNoParam', 'NestedTestsWithTags$TagOnMethod', 'NestedTestsWithTags$TagOnClass')
        result.testClass('NestedTestsWithTags$TagOnMethodNoParam').assertTestCount(1, 0, 0)
        result.testClass('NestedTestsWithTags$TagOnMethod').assertTestCount(1, 0, 0)
        result.testClass('NestedTestsWithTags$TagOnClass').assertTestCount(1, 0, 0)
    }

    @Override
    TestSourceGenerator getTestSourceGenerator() {
        return new JUnit5TestSourceGenerator()
    }

    private class JUnit5TestSourceGenerator implements AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.TestSourceGenerator {
        @Override
        void writeAllSources(AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.TestSourceFixture fixture) {
            fixture.testClasses.each { testClass ->
                writeTestClass(testClass)
            }
            fixture.sources.each { sourceFile ->
                writeSourceFile(sourceFile)
            }
        }

        private TestFile writeTestClass(AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.TestClass testClass) {
            String packagePath = testClass.packageName.replace('.', '/')
            testDirectory.file("src/${testClass.sourceSet}/java/${packagePath}/${testClass.name}.java") << """
                    ${testClass.packageName}

                    import org.junit.jupiter.api.Test;
                    import org.junit.jupiter.api.Tag;
                    import org.junit.jupiter.api.Disabled;

                    ${testClass.categoriesOrTags.collect { "@Tag(\"${it}\")" }.join('\n')}
                    public class ${testClass.name} {
                        ${testClass.methods.collect { generateTestMethod(it) }.join('\n')}
                    }
                """.stripIndent()
        }

        private TestFile writeSourceFile(AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.TestSource testSource) {
            testDirectory.file("src/test/java/${testSource.relativePath}") << testSource.source.stripIndent()
        }

        private String generateTestMethod(AbstractJUnitCategoriesOrTagsCoverageIntegrationSpec.TestMethod method) {
            return """
                @Test
                ${method.isIgnoredOrDisabled ? "@Disabled" : ""}
                ${method.categoriesOrTags.collect { "@Tag(\"${it}\")" }.join('\n')}
                public void ${method.name}() {
                    ${method.shouldPass ? "assert true;" : "assert false;"}
                }
            """
        }
    }
}
