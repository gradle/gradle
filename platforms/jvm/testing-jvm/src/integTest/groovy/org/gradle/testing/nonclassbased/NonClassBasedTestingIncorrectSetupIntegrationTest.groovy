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

import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.TestNGCoverage
import testengines.TestEngines

class NonClassBasedTestingIncorrectSetupIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest {
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
                        scanForTestDefinitions = true
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
                        scanForTestDefinitions = true
                        testDefinitionDirs.from(project.layout.projectDirectory.file("$badPath"))
                    }
                }
            }
        """

        // Write some test def to default dir (which is still scanned), not badPath, needed to avoid "no sources" skip
        def defaultTestDefsDir = file("src/test/definitions")
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
                        scanForTestDefinitions = true
                        testDefinitionDirs.setFrom(project.layout.projectDirectory.file("$badPath"))
                    }
                }
            }
        """

        when:
        fails("test")

        then:
        failureCauseContains("Test definitions directory is not a directory: " + testDirectory.file(badPath).absolutePath)
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "non-readable test definitions directory fails"() {
        def badPath = "src/test/i-cant-be-read"
        // create the directory and make it non-readable at the filesystem level
        def dir = file(badPath).createDir()

        // try to remove read permission for everyone (owner=false, ownerOnly=false)
        // fall back to single-arg version if the two-arg form isn't supported/has no effect
        if (!dir.setReadable(false, false)) {
            dir.setReadable(false)
        }

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
                        scanForTestDefinitions = true
                        testDefinitionDirs.setFrom(project.layout.projectDirectory.file("$badPath"))
                    }
                }
            }
        """

        when:
        fails("test")

        then:
        failureCauseContains("Cannot access input property 'candidateDefinitionDirs' of task ':test'. Accessing unreadable inputs or outputs is not supported.")
        failureCauseContains("java.nio.file.AccessDeniedException: ${dir.absolutePath}")

        cleanup:
        // restore read permission for cleanup
        dir.setReadable(true, false)
    }

    def "non-readable files in test definitions directory fails"() {
        def badPath = "src/test/definitions/i-cant-be-read.txt"
        // create the file and make it non-readable at the filesystem level
        def badFile = file(badPath).createFile()

        // try to remove read permission for everyone (owner=false, ownerOnly=false)
        // fall back to single-arg version if the two-arg form isn't supported/has no effect
        if (!badFile.setReadable(false, false)) {
            badFile.setReadable(false)
        }

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
                        scanForTestDefinitions = true
                    }
                }
            }
        """

        when:
        fails("test", "-S")

        then:
        if (OperatingSystem.current().isWindows()) {
            sourcesPresentAndNoTestsFound()
        } else {
            failureCauseContains("Cannot access input property 'candidateDefinitionDirs' of task ':test'. Accessing unreadable inputs or outputs is not supported.")
            failureCauseContains("Failed to create MD5 hash for file: ${badFile.absolutePath} (Permission denied)")
        }

        cleanup:
        // restore read permission for cleanup
        badFile.setReadable(true, false)
    }

    def "missing test classes and/or definitions is skipped or fails when appropriate (scan for test classes = #scanForTestClasses, has test classes = #hasTestClasses, scan for test defs = #scanForTestDefs, has test defs = #hasTestDefs )"() {
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
                        scanForTestDefinitions = $scanForTestDefs
                    }
                }
            }
        """

        file("src/test/definitions").createDir()

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
            if (scanForTestDefs && hasTestDefs) {
                nonClassBasedTestsExecuted()
            }
        }

        where:
        scanForTestClasses  | hasTestClasses    | scanForTestDefs   | hasTestDefs   || shouldBeSkipped  || shouldFail
        true                | true              | true              | true          || false            || false
        true                | false             | true              | true          || false            || false
        true                | true              | false             | true          || false            || false
        true                | true              | true              | false         || false            || false
        true                | false             | false             | true          || false            || true
        true                | false             | true              | false         || true             || false
        true                | true              | false             | false         || false            || false
        true                | false             | false             | false         || true             || false
        false               | true              | true              | true          || false            || false
        false               | false             | true              | true          || false            || false
        false               | true              | false             | true          || false            || true
        false               | true              | true              | false         || false            || true
        false               | false             | false             | true          || false            || true
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

                dependencies {
                    implementation 'org.testng:testng:${TestNGCoverage.NEWEST}'
                }

                targets.all {
                    testTask.configure {
                        scanForTestDefinitions = true
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
        "Test NG"         | "useTestNG()"
        "JUnit"           | "useJUnit()"
    }
}
