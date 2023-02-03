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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.startsWith

class JUnitCategoriesIntegrationSpec extends AbstractSampleIntegrationTest {

    @Rule TestResources resources = new TestResources(temporaryFolder)

    def 'reports unloadable #type'() {
        given:
        resources.maybeCopy("JUnitCategoriesIntegrationSpec/reportsUnloadableCategories")
        buildFile << "test.useJUnit { ${type} 'org.gradle.CategoryA' }"

        when:
        fails("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTestClass')
        result.testClass("org.gradle.SomeTestClass").assertTestCount(1, 1, 0)
        result.testClass("org.gradle.SomeTestClass").assertTestFailed("initializationError", startsWith("org.gradle.api.InvalidUserDataException: Can't load category class [org.gradle.CategoryA]"))

        where:
        type << ['includeCategories', 'excludeCategories']
    }

    def testTaskFailsIfCategoriesNotSupported() {
        when:
        fails('test')
        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("org.gradle.SomeTest").assertTestFailed("initializationError", startsWith("org.gradle.api.GradleException: JUnit Categories defined but declared JUnit version does not support Categories."))
    }

    def supportsCategoriesAndNullTestClassDescription() {
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
    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def canWorkWithPowerMock() {
        given:
        buildFile << """
apply plugin: 'java'

${mavenCentralRepository()}

dependencies {
    testImplementation "junit:junit:4.13"
    testImplementation "org.powermock:powermock-api-mockito:1.6.5"
    testImplementation "org.powermock:powermock-module-junit4:1.6.5"
}

test {
    useJUnit { includeCategories 'FastTest'  }
}
"""
        file('src/test/java/FastTest.java') << '''
public interface FastTest {
}
'''
        file('src/test/java/MyTest.java') << '''
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
@RunWith(PowerMockRunner.class)
@Category(FastTest.class)
public class MyTest {
    @Test
    public void testMyMethod() {
        assertTrue("This is an error", false);
    }
}
'''
        when:
        fails('test')

        then:
        outputContains('MyTest > testMyMethod FAILED')
    }

    @Issue('https://github.com/gradle/gradle/issues/4924')
    def "re-executes test when options are changed in #suiteName"() {
        given:
        resources.maybeCopy("JUnitCategoriesIntegrationSpec/reExecutesWhenPropertyIsChanged")
        buildFile << """
        |testing {
        |   suites {
        |       $suiteDeclaration {
        |           useJUnit()
        |           targets {
        |               all {
        |                   testTask.configure {
        |                       options {
        |                           includeCategories 'org.gradle.CategoryA'
        |                       }
        |                   }
        |               }
        |           }
        |       }
        |   }
        |}""".stripMargin()

        when:
        succeeds ":$task"

        then:
        executedAndNotSkipped ":$task"

        when:
        resources.maybeCopy("JUnitCategoriesIntegrationSpec/reExecutesWhenPropertyIsChanged")
        buildFile << """
        |testing {
        |   suites {
        |       $suiteDeclaration {
        |           useJUnit()
        |           targets {
        |               all {
        |                   testTask.configure {
        |                       options {
        |                           includeCategories 'org.gradle.CategoryB'
        |                       }
        |                   }
        |               }
        |           }
        |       }
        |   }
        |}""".stripMargin()

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
        resources.maybeCopy("JUnitCategoriesIntegrationSpec/reExecutesWhenPropertyIsChanged")
        buildFile << """
        |testing {
        |   suites {
        |       test {
        |           useJUnit()
        |           targets {
        |               all {
        |                   testTask.configure {
        |                       options {
        |                           includeCategories 'org.gradle.CategoryA'
        |                       }
        |                   }
        |               }
        |           }
        |       }
        |   }
        |}""".stripMargin()

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
