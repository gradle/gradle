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

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.matchesRegexp

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
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")

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
                            testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")
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
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")
                        testDefinitionDirs.from("$otherDefinitionsLocation")
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
        outputContains("INFO: Executing resource-based test: Test[file=SomeThirdTestSpec.rbt, name=third]")
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
                        testDefinitionDirs.from("$parentLocation")
                        testDefinitionDirs.from("$childLocation")
                    }
                }
            }
        """

        writeTestDefinitions(childLocation)

        when:
        succeeds("test", "--info")

        then:
        nonClassBasedTestsExecuted()

        ["INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=foo]",
         "INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=bar]",
         "INFO: Executing resource-based test: Test[file=SomeOtherTestSpec.rbt, name=other]"].forEach {
            result.getOutput().findAll(it).size() == 1
        }
    }

    def "can listen for non-class-based tests"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [\$suite] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [\$suite] [\$suite.name] [\$result.resultType] [\$result.testCount]" }
                void beforeTest(TestDescriptor test) { println "START [\$test] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [\$test] [\$test.name] [\$result.resultType] [\$result.testCount] [\$result.exception]" }
            }
            def listener = new TestListenerImpl()

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        addTestListener(listener)
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")
                    }
                }
            }
        """

        writeTestDefinitions(DEFAULT_DEFINITIONS_LOCATION)

        when:
        def result = succeeds("test")

        then:
        containsLine(result.getOutput(), "START [Gradle Test Run :test] [Gradle Test Run :test]")
        containsLine(result.getOutput(), "FINISH [Gradle Test Run :test] [Gradle Test Run :test] [SUCCESS] [3]")

        containsLine(result.getOutput(), matchesRegexp("START \\[Gradle Test Executor \\d+\\] \\[Gradle Test Executor \\d+\\]"))
        containsLine(result.getOutput(), matchesRegexp("FINISH \\[Gradle Test Executor \\d+\\] \\[Gradle Test Executor \\d+\\] \\[SUCCESS\\] \\[3\\]"))

        containsLine(result.getOutput(), "START [Test SomeTestSpec.rbt : foo()] [SomeTestSpec.rbt : foo]")
        containsLine(result.getOutput(), "FINISH [Test SomeTestSpec.rbt : foo()] [SomeTestSpec.rbt : foo] [SUCCESS] [1] [null]")
        containsLine(result.getOutput(), "START [Test SomeTestSpec.rbt : bar()] [SomeTestSpec.rbt : bar]")
        containsLine(result.getOutput(), "FINISH [Test SomeTestSpec.rbt : bar()] [SomeTestSpec.rbt : bar] [SUCCESS] [1] [null]")
        containsLine(result.getOutput(), "START [Test SomeOtherTestSpec.rbt : other()] [SomeOtherTestSpec.rbt : other]")
        containsLine(result.getOutput(), "FINISH [Test SomeOtherTestSpec.rbt : other()] [SomeOtherTestSpec.rbt : other] [SUCCESS] [1] [null]")
    }

    def "can listen for non-class-based tests using dry-run and tests are reported as skipped"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [\$suite] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [\$suite] [\$suite.name] [\$result.resultType] [\$result.testCount]" }
                void beforeTest(TestDescriptor test) { println "START [\$test] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [\$test] [\$test.name] [\$result.resultType] [\$result.testCount] [\$result.exception]" }
            }
            def listener = new TestListenerImpl()

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        dryRun = true
                        addTestListener(listener)
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")
                    }
                }
            }
        """

        writeTestDefinitions(DEFAULT_DEFINITIONS_LOCATION)

        when:
        def result = succeeds("test")

        then:
        containsLine(result.getOutput(), "START [Gradle Test Run :test] [Gradle Test Run :test]")
        containsLine(result.getOutput(), "FINISH [Gradle Test Run :test] [Gradle Test Run :test] [SUCCESS] [3]")

        containsLine(result.getOutput(), matchesRegexp("START \\[Gradle Test Executor \\d+\\] \\[Gradle Test Executor \\d+\\]"))
        containsLine(result.getOutput(), matchesRegexp("FINISH \\[Gradle Test Executor \\d+\\] \\[Gradle Test Executor \\d+\\] \\[SUCCESS\\] \\[3\\]"))

        containsLine(result.getOutput(), "START [Test SomeTestSpec.rbt : foo()] [SomeTestSpec.rbt : foo]")
        containsLine(result.getOutput(), "FINISH [Test SomeTestSpec.rbt : foo()] [SomeTestSpec.rbt : foo] [SKIPPED] [1] [null]")
        containsLine(result.getOutput(), "START [Test SomeTestSpec.rbt : bar()] [SomeTestSpec.rbt : bar]")
        containsLine(result.getOutput(), "FINISH [Test SomeTestSpec.rbt : bar()] [SomeTestSpec.rbt : bar] [SKIPPED] [1] [null]")
        containsLine(result.getOutput(), "START [Test SomeOtherTestSpec.rbt : other()] [SomeOtherTestSpec.rbt : other]")
        containsLine(result.getOutput(), "FINISH [Test SomeOtherTestSpec.rbt : other()] [SomeOtherTestSpec.rbt : other] [SKIPPED] [1] [null]")
    }

    def "resource-based test engine can exclude test definitions"() {
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

                        filter {
                            excludeTestsMatching "*SomeTestSpec*"
                        }
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        succeeds("test", "--info")

        then:
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=foo]")
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=bar]")
        outputContains("INFO: Executing resource-based test: Test[file=SomeOtherTestSpec.rbt, name=other]")
    }

    def "resource-based test engine can exclude test definitions in subdirectories"() {
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

                        filter {
                            excludeTestsMatching "*Additional*"
                        }
                    }
                }
            }
        """

        writeTestDefinitions()
        file("$DEFAULT_DEFINITIONS_LOCATION/subdir1/AdditionalDefs.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo2" />
            </tests>
        """
        file("$DEFAULT_DEFINITIONS_LOCATION/AdditionalDir/OtherTests.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo3" />
            </tests>
        """

        when:
        succeeds("test", "--info")

        then:
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=AdditionalDefs.rbt, name=foo2]")
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=OtherTests.rbt, name=foo3]")

        outputContains("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=foo]")
        outputContains("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=bar]")
        outputContains("INFO: Executing resource-based test: Test[file=SomeOtherTestSpec.rbt, name=other]")
    }

    def "resource-based test engine can include test definitions"() {
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

                        filter {
                            includeTestsMatching "*SomeTestSpec*"
                        }
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        succeeds("test", "--info")

        then:
        outputContains("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=foo]")
        outputContains("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=bar]")
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=SomeOtherTestSpec.rbt, name=other]")
    }

    def "resource-based test engine can include test definitions in subdirectories"() {
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

                        filter {
                            includeTestsMatching "*subdir1/SomeTestSpec*"
                        }
                    }
                }
            }
        """

        writeTestDefinitions()
        file("$DEFAULT_DEFINITIONS_LOCATION/subdir1/SomeTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="subfoo" />
            </tests>
        """

        when:
        succeeds("test", "--info")

        then:
        outputContains("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=subfoo]")
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=foo]")
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=bar]")
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=SomeOtherTestSpec.rbt, name=other]")
    }

    def "resource-based test engine can include and exclude test definitions"() {
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

                        filter {
                            includeTestsMatching "*SomeTestSpec*"
                            excludeTestsMatching "*SomeTestSpecThatShoudlntRun*"
                        }
                    }
                }
            }
        """

        writeTestDefinitions()
        file("$DEFAULT_DEFINITIONS_LOCATION/SomeTestSpecThatShoudlntRun.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="dontrun" />
            </tests>
        """

        when:
        succeeds("test", "--info")

        then:
        outputContains("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=foo]")
        outputContains("INFO: Executing resource-based test: Test[file=SomeTestSpec.rbt, name=bar]")
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=SomeOtherTestSpec.rbt, name=other]")
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=SomeTestSpecThatShoudlntRun.rbt, name=dontrun]")
    }

    // TODO: Test filters using file separators to ensure they work on all platforms
}
