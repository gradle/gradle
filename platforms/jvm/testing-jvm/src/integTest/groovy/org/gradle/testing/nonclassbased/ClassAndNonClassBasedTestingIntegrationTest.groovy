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

/**
 * Tests that exercise and demonstrate a TestEngines that runs both class and non-class test definitions.
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
                        testDefinitionDirs.from(project.layout.projectDirectory.file("$DEFAULT_DEFINITIONS_LOCATION"))

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
        succeeds("test", "--info")

        then:
        if (classesPresent) {
            classBasedTestsExecuted()
        }
        if (nonClassDefinitionsPresent) {
            nonClassBasedTestsExecuted()
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
                        testDefinitionDirs.from(project.layout.projectDirectory.file("$definitionsLocation"))

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
        succeeds("test", "--info")

        then:
        classBasedTestsExecuted()
        nonClassBasedTestsExecuted()
    }
}
