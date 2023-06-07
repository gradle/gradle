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
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.VersionNumber
import org.junit.Assume
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT4_CATEGORIES
import static org.hamcrest.CoreMatchers.startsWith

@TargetCoverage({ JUNIT4_CATEGORIES })
class JUnit4CategoriesOrTagsCoverageIntegrationTest extends AbstractJUnit4CategoriesOrTagsCoverageIntegrationTest implements JUnit4MultiVersionTest {
    String singularCategoryOrTagName = "category"
    String pluralCategoryOrTagName = "categories"

    @Override
    boolean supportsCategoryOnNestedClass() {
        return !(version in ['4.10', '4.11', '4.12'])
    }

    def 'reports unloadable #type'() {
        given:
        testSources.with {
            testClass('SomeTestClass').with {
                testMethod('ok1')
                testMethod('ok2')
            }
        }
        testSourceGenerator.writeAllSources(testSources)
        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework} { ${type} 'org.gradle.CategoryA' }
        """.stripIndent()

        when:
        fails("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('SomeTestClass')
        result.testClass("SomeTestClass").assertTestCount(1, 1, 0)
        result.testClass("SomeTestClass").assertTestFailed("initializationError", startsWith("org.gradle.api.InvalidUserDataException: Can't load category class [org.gradle.CategoryA]"))

        where:
        type << ['includeCategories', 'excludeCategories']
    }

    def "supports categories and null test class description"() {
        // Our custom runner class won't work with JUnit < 4.11
        Assume.assumeTrue(VersionNumber.parse(version) >= VersionNumber.parse('4.11'))

        given:
        file('src/test/java/CategoryA.java') << """
            public interface CategoryA { }
        """
        file('src/test/java/CustomRunner.java') << """
            import org.junit.runner.Description;
            import org.junit.runners.BlockJUnit4ClassRunner;
            import org.junit.runners.model.FrameworkMethod;
            import org.junit.runners.model.InitializationError;

            public class CustomRunner extends BlockJUnit4ClassRunner {

                public CustomRunner(Class<?> klass) throws InitializationError {
                    super(klass);
                }

                /**
                 * Returns a test Description with a null TestClass.
                 * @param method method under test
                 * @return a Description
                 */
                @Override
                protected Description describeChild(FrameworkMethod method) {
                    return Description.createTestDescription("Not a real class name", testName(method), "someSerializable");
                }
            }
        """
        file('src/test/java/DescriptionWithNullClassTest.java') << """
            ${testFrameworkImports}

            ${getRunOrExtendWithAnnotation('CustomRunner.class')}
            public class DescriptionWithNullClassTest {
                @Test
                public void someTest() {
                }
            }

        """
        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework} { ${excludeCategoryOrTag('org.gradle.CategoryA')} }
        """.stripIndent()

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        def testClass = result.testClass("Not a real class name")
        testClass.assertTestCount(1, 0, 0)
        testClass.assertTestPassed("someTest")
    }

    @Issue('https://github.com/gradle/gradle/issues/3189')
    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    def "can work with PowerMock"() {
        given:
        file('src/test/java/FastTest.java') << '''
            public interface FastTest {
            }
        '''.stripIndent()
        file('src/test/java/MyTest.java') << """
            ${testFrameworkImports}
            import org.junit.experimental.categories.Category;
            import org.powermock.modules.junit4.PowerMockRunner;
            @RunWith(PowerMockRunner.class)
            @Category(FastTest.class)
            public class MyTest {
                @Test
                public void testMyMethod() {
                    assertTrue("This is an error", false);
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            dependencies {
                ${testFrameworkDependencies}
                testImplementation "org.powermock:powermock-api-mockito:1.6.5"
                testImplementation "org.powermock:powermock-module-junit4:1.6.5"
            }

            test {
                ${configureTestFramework} { ${includeCategoryOrTag('FastTest')} }
            }
        """.stripIndent()

        when:
        fails('test')

        then:
        outputContains('MyTest > testMyMethod FAILED')
    }

    @Issue('https://github.com/gradle/gradle/issues/4924')
    def "re-executes test when options are changed in #suiteName"() {
        given:
        testSources.with {
            ['test', 'integTest'].each { sourceSet ->
                testClass('SomeTestClass', sourceSet).with {
                    testMethod('ok1').withCategoryOrTag('CategoryA')
                    testMethod('ok2').withCategoryOrTag('CategoryB')
                }
                testCategory('CategoryA', sourceSet)
                testCategory('CategoryB', sourceSet)
            }
        }
        testSourceGenerator.writeAllSources(testSources)
        buildFile << """
            testing {
               apply plugin: 'java'
               ${mavenCentralRepository()}
               suites {
                   $suiteDeclaration {
                       useJUnit()
                       targets {
                           all {
                               testTask.configure {
                                   options {
                                       includeCategories 'CategoryA'
                                   }
                               }
                           }
                       }
                   }
               }
            }
        """.stripIndent()

        when:
        succeeds ":$task"

        then:
        executedAndNotSkipped ":$task"

        when:
        buildFile.text = """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            testing {
               suites {
                   $suiteDeclaration {
                       useJUnit()
                       targets {
                           all {
                               testTask.configure {
                                   options {
                                       includeCategories 'CategoryB'
                                   }
                               }
                           }
                       }
                   }
               }
            }
        """.stripIndent()

        and:
        succeeds ":$task"

        then:
        executedAndNotSkipped ":$task"

        where:
        suiteName   | suiteDeclaration              | task
        'test'      | 'test'                        | 'test'
        'integTest' | 'integTest(JvmTestSuite)'     | 'integTest'
    }

    @Issue('https://github.com/gradle/gradle/issues/4924')
    def "skips test on re-run when options are NOT changed"() {
        given:
        testSources.with {
            testClass('SomeTestClass').with {
                testMethod('ok1').withCategoryOrTag('CategoryA')
                testMethod('ok2').withCategoryOrTag('CategoryB')
            }
            testCategory('CategoryA')
            testCategory('CategoryB')
        }
        testSourceGenerator.writeAllSources(testSources)
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            testing {
               suites {
                   test {
                       useJUnit()
                       targets {
                           all {
                               testTask.configure {
                                   options {
                                       includeCategories 'CategoryA'
                                   }
                               }
                           }
                       }
                   }
               }
            }
        """.stripIndent()

        when:
        succeeds ':test'

        then:
        executedAndNotSkipped ':test'

        when:
        succeeds ':test'

        then:
        skipped ':test'
    }
}
