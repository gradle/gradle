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
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule
import org.junit.Test
import static org.hamcrest.Matchers.startsWith


public class JUnitCategoriesIntegrationSpec extends AbstractIntegrationSpec {
    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    @Test
    public void reportsUnloadableExcludeCategory() {
        given:
        resources.maybeCopy("JUnitCategoriesIntegrationSpec/reportsUnloadableCategories")
        TestFile buildFile = testDirectory.file('build.gradle');
        buildFile << '''test {
                                    useJUnit {
                                        excludeCategories 'org.gradle.CategoryA'
                                    }
                                }
                            '''

        when:
        executer.withTasks('test').runWithFailure();
        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTestClass')
        result.testClass("org.gradle.SomeTestClass").assertTestCount(1, 1, 0)
        result.testClass("org.gradle.SomeTestClass").assertTestFailed("initializationError", startsWith("org.gradle.api.InvalidUserDataException: Can't load category class [org.gradle.CategoryA]"))
    }

    @Test
    public void reportsUnloadableIncludeCategory() {
        given:
        resources.maybeCopy("JUnitCategoriesIntegrationSpec/reportsUnloadableCategories")
        TestFile buildFile = testDirectory.file('build.gradle');
        buildFile << '''test {
                                useJUnit {
                                    includeCategories 'org.gradle.CategoryA'
                                }
                            }
                            '''

        when:
        executer.withTasks('test').runWithFailure();
        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTestClass')
        result.testClass("org.gradle.SomeTestClass").assertTestCount(1, 1, 0)
        result.testClass("org.gradle.SomeTestClass").assertTestFailed("initializationError", startsWith("org.gradle.api.InvalidUserDataException: Can't load category class [org.gradle.CategoryA]"))
    }

    @Test
    public void testTaskFailsIfCategoriesNotSupported() {
        when:
        ExecutionResult failure = executer.withTasks('test').runWithFailure();
        then:
        failure.error.contains("JUnit Categories defined but declared JUnit version does not support Categories.")
    }
}
