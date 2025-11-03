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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.TestNGCoverage
import org.gradle.util.internal.TextUtil

/**
 * Tests that exercise and demonstrate Non-Class-Based Testing using the {@code Test} task
 * and a sample resource-based JUnit Platform Test Engine defined in this project's {@code testFixtures}.
 */
class NonClassBasedTestingIntegrationTest extends AbstractIntegrationSpec {
    private static engineJarLibPath

    def setupSpec() {
        def version = IntegrationTestBuildContext.INSTANCE.getVersion().getBaseVersion().version
        // TODO: there's probably a better place to put this and/or way to get this on the path
        def path = IntegrationTestBuildContext.TEST_DIR.file("../../software/testing-base/build/libs/gradle-testing-base-$version-test-fixtures.jar").path
        engineJarLibPath = TextUtil.convertLineSeparatorsToUnix(path)
    }

    def "empty test definitions directory skips"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${setupSuiteWithEngineFixture()}

                targets.all {
                    testTask.configure {
                        scanForTestDefinitions = true

                        options {
                            includeEngines("rbt-engine")
                        }
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
                ${setupSuiteWithEngineFixture()}

                targets.all {
                    testTask.configure {
                        scanForTestDefinitions = true
                        testDefinitionDirs.from(project.layout.projectDirectory.file("$badPath"))
                    }
                }
            }
        """

        // Write test defs to default dir (which is still scanned), not badPath, needed to avoid "no sources" skip
        writeTestDefinitions()

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
                ${setupSuiteWithEngineFixture()}

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
                ${setupSuiteWithEngineFixture()}

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
        failureCauseContains("Cannot access input property 'testDefinitionDirs' of task ':test'. Accessing unreadable inputs or outputs is not supported.")
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
                ${setupSuiteWithEngineFixture()}

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
            failureCauseContains("Cannot access input property 'testDefinitionDirs' of task ':test'. Accessing unreadable inputs or outputs is not supported.")
            failureCauseContains("Failed to create MD5 hash for file: ${badFile.absolutePath} (Permission denied)")
        }

        cleanup:
        // restore read permission for cleanup
        badFile.setReadable(true, false)
    }

    def "resource-based test engine detects and executes test definitions (excluding jupiter engine = #excludingJupiter)"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${setupSuiteWithEngineFixture()}

                targets.all {
                    testTask.configure {
                        scanForTestDefinitions = true

                        options {
                            includeEngines("rbt-engine")
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

    def "resource-based test engine detects and executes test definitions in custom location"() {
        String customLocation = "src/test/some-other-place"

        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${setupSuiteWithEngineFixture()}

                targets.all {
                    testTask.configure {
                        scanForTestDefinitions = true
                        testDefinitionDirs.setFrom(project.layout.projectDirectory.file("$customLocation"))

                        options {
                            includeEngines("rbt-engine")
                        }
                    }
                }
            }
        """

        writeTestDefinitions(customLocation)

        when:
        succeeds("test", "--info")

        then:
        nonClassBasedTestsExecuted()
    }

    def "empty custom test definitions location skips"() {
        String customLocation = "src/test/some-other-place"

        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${setupSuiteWithEngineFixture()}

                targets.all {
                    testTask.configure {
                        scanForTestDefinitions = true
                        testDefinitionDirs.from(project.layout.projectDirectory.file("$customLocation"))

                        options {
                            includeEngines("rbt-engine")
                        }
                    }
                }
            }
        """

        when:
        succeeds("test", "--info")

        then:
        testTaskWasSkippedDueToNoSources()
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
        "TestNG"         | "useTestNG()"
        "JUnit"           | "useJUnit()"
    }

    def "missing test classes and/or definitions is skipped or fails when appropriate (scan for test classes = #scanForTestClasses, has test classes = #hasTestClasses, scan for test defs = #scanForTestDefs, has test defs = #hasTestDefs )"() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${setupSuiteWithEngineFixture()}

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

    private void testTaskWasSkippedDueToNoSources() {
        result.assertTaskSkipped(":test")
        outputContains("Skipping task ':test' as it has no source files and no previous output files.")
    }

    private void sourcesPresentAndNoTestsFound() {
        failureCauseContains("There are test sources present and no filters are applied, but the test task did not discover any tests to execute. This is likely due to a misconfiguration. Please check your test configuration. If this is not a misconfiguration, this error can be disabled by setting the 'failOnNoDiscoveredTests' property to false.")
    }

    private String setupSuiteWithEngineFixture() {
        return """
                useJUnitJupiter()

                dependencies {
                    implementation files("${TextUtil.normaliseFileSeparators(engineJarLibPath)}")
                }
        """
    }

    private void classBasedTestsExecuted() {
        outputContains("Tested!")
    }

    private void nonClassBasedTestsExecuted() {
        outputContains("INFO: Executing test: Test [file=SomeTestSpec.rbt, name=foo]")
        outputContains("INFO: Executing test: Test [file=SomeTestSpec.rbt, name=bar]")
        outputContains("INFO: Executing test: Test [file=subSomeOtherTestSpec.rbt, name=other]")
    }

    private void writeTestClasses() {
        file("src/test/java/SomeTest.java") << """
            import org.junit.jupiter.api.Test;

            public class SomeTest {
                @Test
                public void testMethod() {
                    System.out.println("Tested!");
                }
            }
        """
    }

    private void writeTestDefinitions(String path = "src/test/definitions") {
        file("$path/SomeTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
                <test name="bar" />
            </tests>
        """
        file("$path/subSomeOtherTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="other" />
            </tests>
        """
    }
}
