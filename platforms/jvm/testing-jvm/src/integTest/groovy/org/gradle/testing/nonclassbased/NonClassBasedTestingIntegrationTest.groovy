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
 * Tests that exercise and demonstrate Non-Class-Based Testing using the {@code Test} task
 * and a sample resource-based JUnit Platform Test Engine defined in this project's {@code testFixtures}.
 */
class NonClassBasedTestingIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest {
    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.BASIC_RESOURCE_BASED]
    }

    def "resource-based test engine detects and executes test definitions (excluding jupiter engine = #excludingJupiter)"() {
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
                        testDefinitionDirs.from(project.layout.projectDirectory.file("${DEFAULT_DEFINITIONS_LOCATION}"))

                        options {
                            if ($excludingJupiter) {
                                excludeEngines("junit-jupiter")
                            }
                        }
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        succeeds("test", "--info")

        then:
        nonClassBasedTestsExecuted()

        where:
        excludingJupiter << [true, false]
    }

    def "resource-based test engine detects and executes test definitions using custom test suite/task"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites {
                integrationTest(JvmTestSuite) {
                    ${enableEngineForSuite()}

                    targets.all {
                        testTask.configure {
                            testDefinitionDirs.from(project.layout.projectDirectory.file("${DEFAULT_DEFINITIONS_LOCATION}"))
                        }
                    }
                }
            }
        """

        writeTestDefinitions(DEFAULT_DEFINITIONS_LOCATION)

        when:
        succeeds("integrationTest", "--info")

        then:
        nonClassBasedTestsExecuted()
    }

    def "resource-based test engine detects and executes test definitions in multiple locations"() {
        String otherDefinitionsLocation = "src/test/some-other-place"

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
                        testDefinitionDirs.from(project.layout.projectDirectory.file("$otherDefinitionsLocation"))
                    }
                }
            }
        """

        writeTestDefinitions()
        file("$otherDefinitionsLocation/SomeThirdTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="third" />
            </tests>
        """

        when:
        succeeds("test", "--info")

        then:
        nonClassBasedTestsExecuted()
        outputContains("INFO: Executing resource-based test: Test [file=SomeThirdTestSpec.rbt, name=third]")
    }

    def "empty test definitions location skips"() {
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
                    }
                }
            }
        """

        when:
        succeeds("test", "--info")

        then:
        testTaskWasSkippedDueToNoSources()
    }

    def "resource-based test engine detects and executes test definitions only once in overlapping locations"() {
        String parentLocation = "src/test/parent"
        String childLocation = "src/test/parent/child"

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
                        testDefinitionDirs.from(project.layout.projectDirectory.file("$parentLocation"))
                        testDefinitionDirs.from(project.layout.projectDirectory.file("$childLocation"))
                    }
                }
            }
        """

        writeTestDefinitions(childLocation)

        when:
        succeeds("test", "--info")

        then:
        nonClassBasedTestsExecuted()

        ["INFO: Executing resource-based test: Test [file=SomeTestSpec.rbt, name=foo]",
        "INFO: Executing resource-based test: Test [file=SomeTestSpec.rbt, name=bar]",
        "INFO: Executing resource-based test: Test [file=subSomeOtherTestSpec.rbt, name=other]"].forEach {
            result.getOutput().findAll(it).size() == 1
        }
    }
}
