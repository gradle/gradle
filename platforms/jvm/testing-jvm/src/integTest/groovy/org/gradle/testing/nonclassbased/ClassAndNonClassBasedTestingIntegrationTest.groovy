/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.nonclassbased

import org.gradle.api.tasks.testing.TestResult
import testengines.TestEnginesFixture.TestEngines

/**
 * Tests that exercise and demonstrate a TestEngines that runs both class and non-class test definitions.
 * <p>
 * Note that the {@link TestEngines#RESOURCE_AND_CLASS_BASED} engine is not a complete implementation of
 * a class-based testing engine, it will only execute the class and because of this only reports results properly for
 * test classes in the default package.
 */
class ClassAndNonClassBasedTestingIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest {
    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.RESOURCE_AND_CLASS_BASED]
    }

    def "can use same engine for class and resource-based testing (classes: #classesPresent, non-class defs: #nonClassDefinitionsPresent)"() {
        given:

        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")

                        options {
                            excludeEngines("junit-jupiter")
                        }
                    }
                }
            }

            // Ensure the definitions directory exists even if no definitions are added; otherwsie the task will fail with "Test definitions directory does not exist"
            project.layout.projectDirectory.file("$DEFAULT_DEFINITIONS_LOCATION").getAsFile().mkdirs()
        """

        if (classesPresent) {
            writeTestClasses()
        }
        if (nonClassDefinitionsPresent) {
            writeTestDefinitions()
        }

        when:
        succeeds("test")

        then:
        if (classesPresent) {
            resultsFor().testPathPreNormalized(":SomeTest").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        }
        if (nonClassDefinitionsPresent) {
            nonClassBasedTestsExecuted(false)
        }

        where:
        classesPresent | nonClassDefinitionsPresent
        true           | true
        true           | false
        false          | true
    }

    def "can use same engine and same test definitions dir for class and resource-based testing"() {
        String definitionsLocation = "src/test/java"

        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$definitionsLocation")

                        options {
                            excludeEngines("junit-jupiter")
                        }
                    }
                }
            }
        """

        writeTestClasses()
        writeTestDefinitions(definitionsLocation)

        when:
        succeeds("test")

        then:
        resultsFor().testPathPreNormalized(":SomeTest").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        nonClassBasedTestsExecuted(false)
    }

    def "when multiple engines do class-based testing and create different class tests with the same name, this is handled sensibly"() {
        given:

        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")
                    }
                }
            }
        """

        if (classesPresent) {
            writeTestClasses()
        }
        if (nonClassDefinitionsPresent) {
            writeTestDefinitions()
        }

        when:
        succeeds("test")

        then:
        if (classesPresent) {
            def results = resultsFor()
            results.testPathPreNormalized(":SomeTest").onlyRoot().assertChildCount(1, 0)
            results.testPathPreNormalized(":SomeTest:testMethod()").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        }
        if (nonClassDefinitionsPresent) {
            nonClassBasedTestsExecuted(false)
        }

        where:
        classesPresent | nonClassDefinitionsPresent
        true           | true
        true           | false
        false          | true
    }
}
