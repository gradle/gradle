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
 * Tests that exercise and demonstrate incorrect Non-Class-Based Testing setups.
 */
class IncorrectSetupNonClassBasedTestingIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest {
    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.BASIC_RESOURCE_BASED]
    }

    def "empty test definitions directory skips"() {
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

        when:
        succeeds("test", "--info")

        then:
        testTaskWasSkippedDueToNoSources()
    }

    def "non-existent test definitions directory fails"() {
        def badPath = "src/test/i-dont-exist"

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
                        testDefinitionDirs.from("$badPath")
                    }
                }
            }
        """

        // Write some test def to default dir (which is still scanned), not badPath, needed to avoid "no sources" skip
        def defaultTestDefsDir = file(DEFAULT_DEFINITIONS_LOCATION)
        defaultTestDefsDir.mkdirs()
        defaultTestDefsDir.file("SomeTestDefinition.xml").createNewFile()

        when:
        fails("test")

        then:
        failureCauseContains("Test definitions directory does not exist: " + testDirectory.file(badPath).absolutePath)
    }

    def "non-directory test definitions directory fails"() {
        def badPath = "src/test/i-dont-exist.txt"
        file(badPath).createFile()

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
                        testDefinitionDirs.from("$badPath")
                    }
                }
            }
        """

        when:
        fails("test")

        then:
        failureCauseContains("Test definitions directory is not a directory: " + testDirectory.file(badPath).absolutePath)
    }

    def "missing test classes and/or definitions is skipped or fails when appropriate (scan for test classes = #scanForTestClasses, has test classes = #hasTestClasses, add test defs dir = #addTestDefsDir, has test defs = #hasTestDefs)"() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        scanForTestClasses = $scanForTestClasses
                        if ($addTestDefsDir) {
                            testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")
                        }
                    }
                }
            }
        """

        // Ensure test definitions dir exists even if no defs are added
        file("$DEFAULT_DEFINITIONS_LOCATION").createDir()

        if (hasTestClasses) {
            writeTestClasses()
        }
        if (hasTestDefs) {
            writeTestDefinitions()
        }

        when:
        if (shouldFail) {
            fails("test", "--info")
        } else {
            succeeds("test", "--info")
        }

        then:
        if (shouldBeSkipped) {
            testTaskWasSkippedDueToNoSources()
        } else if (shouldFail) {
            sourcesPresentAndNoTestsFound()
        } else {
            if (scanForTestClasses && hasTestClasses) {
                classBasedTestsExecuted()
            }
            if (addTestDefsDir && hasTestDefs) {
                nonClassBasedTestsExecuted()
            }
        }

        where:
        scanForTestClasses  | hasTestClasses    | addTestDefsDir | hasTestDefs || shouldBeSkipped || shouldFail
        true                | true              | true              | true          || false            || false
        true                | false             | true              | true          || false            || false
        true                | true              | false             | true          || false            || false
        true                | true              | true              | false         || false            || false
        true                | false             | false             | true          || true             || false
        true                | false             | true              | false         || true             || false
        true                | true              | false             | false         || false            || false
        true                | false             | false             | false         || true             || false
        false               | true              | true              | true          || false            || false
        false               | false             | true              | true          || false            || false
        false               | true              | false             | true          || false            || true
        false               | true              | true              | false         || false            || true
        false               | false             | false             | true          || true             || false
        false               | false             | true              | false         || true             || false
        false               | true              | false             | false         || false            || true
        false               | false             | false             | false         || true             || false
    }

    def "can't do resource-based testing with unsupported test framework = #testFrameworkName"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                $testFrameworkMethod

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        fails("test")

        then:
        failure.assertHasCause("The $testFrameworkName test framework does not support resource-based testing.")

        where:
        testFrameworkName | testFrameworkMethod
        "TestNG"         | "useTestNG()"
        "JUnit"           | "useJUnit()"
    }

    // Once reporting is addressed, this should use more robust verification using existing report-checking fixtures
    @Override
    protected void classBasedTestsExecuted() {
        outputContains("SomeTest > testMethod()")
        outputContains("Tested!")
    }
}
