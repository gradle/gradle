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

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.matchesRegexp

/**
 * Tests that exercise and demonstrate Non-Class-Based Testing using the {@code Test} task
 * and a sample resource-based JUnit Platform Test Engine defined in this project's {@code testFixtures}.
 */
class NonClassBasedTestingIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest implements VerifiesGenericTestReportResults {
    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.BASIC_RESOURCE_BASED]
    }

    @Override
    TestFramework getTestFramework() {
        return TestFramework.CUSTOM
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
        succeeds("test")

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
        succeeds("integrationTest")

        then:
        resultsFor("tests/integrationTest").assertTestPathsExecuted(":SomeTestSpec.rbt:foo", ":SomeTestSpec.rbt:bar", ":sub/SomeOtherTestSpec.rbt:other")
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
        succeeds("test")

        then:
        nonClassBasedTestsExecuted(false)
        resultsFor().assertAtLeastTestPathsExecuted(":SomeThirdTestSpec.rbt:third")
    }

    def "resource-based tests in multiple directories are discovered and executed with daemon-side discovery"() {
        String locationA = "src/test/defs-a"
        String locationB = "src/test/defs-b"

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
                        useDaemonSideTestDiscovery = true
                        testDefinitionDirs.from("$locationA")
                        testDefinitionDirs.from("$locationB")
                    }
                }
            }
        """

        file("$locationA/AlphaSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="alpha1" />
                <test name="alpha2" />
            </tests>
        """
        file("$locationA/BetaSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="beta1" />
            </tests>
        """
        file("$locationB/GammaSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="gamma1" />
                <test name="gamma2" />
                <test name="gamma3" />
            </tests>
        """

        when:
        succeeds("test", "--info")

        then: 'all tests from both directories are discovered and executed'
        output.contains("alpha1")
        output.contains("alpha2")
        output.contains("beta1")
        output.contains("gamma1")
        output.contains("gamma2")
        output.contains("gamma3")
    }

    def "resource-based tests are distributed across workers with daemon-side discovery (#strategy)"() {
        String locationA = "src/test/defs-a"
        String locationB = "src/test/defs-b"
        String locationC = "src/test/defs-c"

        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            import org.gradle.api.tasks.testing.distribution.TestDistributionStrategy

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        useDaemonSideTestDiscovery = true
                        testDistributionStrategy = TestDistributionStrategy.${strategy}
                        maxParallelForks = 4
                        testDefinitionDirs.from("$locationA")
                        testDefinitionDirs.from("$locationB")
                        testDefinitionDirs.from("$locationC")
                    }
                }
            }
        """

        file("$locationA/AlphaSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="alpha1" />
                <test name="alpha2" />
            </tests>
        """
        file("$locationB/BetaSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="beta1" />
                <test name="beta2" />
            </tests>
        """
        file("$locationC/GammaSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="gamma1" />
                <test name="gamma2" />
            </tests>
        """

        when:
        succeeds("test", "--info")

        then: 'all tests from all directories are discovered and executed'
        output.contains("alpha1")
        output.contains("alpha2")
        output.contains("beta1")
        output.contains("beta2")
        output.contains("gamma1")
        output.contains("gamma2")

        and: 'tests were distributed across the expected number of worker JVMs'
        def pids = output.readLines()
            .findAll { it.contains("RBT_EXEC worker_pid=") }
            .collect { (it =~ /worker_pid=(\d+)/)[0][1] }
            .toSet()
        pids.size() == expectedWorkers

        where:
        strategy                       | expectedWorkers
        'BY_TOP_LEVEL_TEST_CONTAINER'  | 3 // only 3 top-level test containers across the 3 definitions files, so only 3 workers should be used
        'BY_INDIVIDUAL_TEST'           | 4 // 6 individual tests across the 3 definitions files, so up to the max of 4 workers should be used
    }

    def "daemon-side discovery with file-based filter includes only matching definitions"() {
        String locationA = "src/test/defs-a"
        String locationB = "src/test/defs-b"

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
                        useDaemonSideTestDiscovery = true
                        testDefinitionDirs.from("$locationA")
                        testDefinitionDirs.from("$locationB")

                        filter {
                            includeTestsMatching "*AlphaSpec*"
                        }
                    }
                }
            }
        """

        file("$locationA/AlphaSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="alpha1" />
                <test name="alpha2" />
            </tests>
        """
        file("$locationB/BetaSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="beta1" />
            </tests>
        """
        file("$locationB/GammaSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="gamma1" />
            </tests>
        """

        when:
        succeeds("test", "--info")

        then: 'only AlphaSpec tests execute — daemon-side filtering excludes BetaSpec and GammaSpec'
        def executedTests = output.readLines().findAll { it.contains("RBT_EXEC") }
        executedTests.size() == 2
        executedTests.count { it.contains("alpha1") } == 1
        executedTests.count { it.contains("alpha2") } == 1

        and: 'only one worker was used since only one file container matched'
        def pids = executedTests.collect { (it =~ /worker_pid=(\d+)/)[0][1] }.toSet()
        pids.size() == 1
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
        succeeds("test")

        then:
        skipped(":test")
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

        then: 'all three tests are executed exactly once despite overlapping directories'
        output.readLines().findAll { it.contains("Executing resource-based test:") }.size() == 3
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

        containsLine(result.getOutput(), "START [Test foo] [foo]")
        containsLine(result.getOutput(), "FINISH [Test foo] [foo] [SUCCESS] [1] [null]")
        containsLine(result.getOutput(), "START [Test bar] [bar]")
        containsLine(result.getOutput(), "FINISH [Test bar] [bar] [SUCCESS] [1] [null]")
        containsLine(result.getOutput(), "START [Test other] [other]")
        containsLine(result.getOutput(), "FINISH [Test other] [other] [SUCCESS] [1] [null]")
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

        containsLine(result.getOutput(), "START [Test foo] [foo]")
        containsLine(result.getOutput(), "FINISH [Test foo] [foo] [SKIPPED] [1] [null]")
        containsLine(result.getOutput(), "START [Test bar] [bar]")
        containsLine(result.getOutput(), "FINISH [Test bar] [bar] [SKIPPED] [1] [null]")
        containsLine(result.getOutput(), "START [Test other] [other]")
        containsLine(result.getOutput(), "FINISH [Test other] [other] [SKIPPED] [1] [null]")
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
                            excludeTestsMatching "*SomeTestSpec"
                        }
                    }
                }
            }
        """
        writeTestDefinitions()

        when:
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(":sub/SomeOtherTestSpec.rbt:other")
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
                            excludeTestsMatching "*AdditionalDir.*"
                        }
                    }
                }
            }
        """

        writeTestDefinitions()
        file("$DEFAULT_DEFINITIONS_LOCATION/subdir1/AdditionalDir/AdditionalDefs.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
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
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(":SomeTestSpec.rbt:foo", ":SomeTestSpec.rbt:bar", ":sub/SomeOtherTestSpec.rbt:other")
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
                            includeTestsMatching "*SomeTestSpec"
                        }
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(":SomeTestSpec.rbt:foo", ":SomeTestSpec.rbt:bar")
    }

    def "resource-based test engine can include test definitions in subdirectories (using pattern = #filterPattern)"() {
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
                            includeTestsMatching "*subdir1.SomeTestSpec.*"
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
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(":subdir1/SomeTestSpec.rbt:subfoo")
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
                            includeTestsMatching "*SomeTestSpec.*"
                            excludeTestsMatching "*SomeTestSpecThatShouldntRun.*"
                        }
                    }
                }
            }
        """

        writeTestDefinitions()
        file("$DEFAULT_DEFINITIONS_LOCATION/SomeTestSpecThatShouldntRun.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="dontrun" />
            </tests>
        """

        when:
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(":SomeTestSpec.rbt:foo", ":SomeTestSpec.rbt:bar")
    }

    def "when running class-based and non-class-based tests, filters (#pattern) apply to both non-class-based tests"() {
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

        file("src/test/java/example/SampleTest.java") << """
            package example;

            import org.junit.jupiter.api.Test;

            public class SampleTest {
                @Test
                public void foo() {
                    System.out.println("Tested!");
                }
            }
        """
        file("src/test/java/example/OtherTest.java") << """
            package example;

            import org.junit.jupiter.api.Test;

            public class OtherTest {
                @Test
                public void foo() {
                    System.out.println("Tested!");
                }
            }
        """
        file("$DEFAULT_DEFINITIONS_LOCATION/example/SampleTest.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
            </tests>
        """
        file("$DEFAULT_DEFINITIONS_LOCATION/example/OtherTest.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
            </tests>
        """
        when:
        succeeds("test", "--tests", pattern)

        then:
        resultsFor().assertTestPathsExecuted(":example.SampleTest", ":example.SampleTest:foo()", ":example/SampleTest.rbt:foo")

        where:
        pattern << ["example.SampleTest.*", "*SampleTest.*"]
    }

    def "resource-based test engine can select test definitions using --tests"() {
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

        writeTestDefinitions()

        when:
        succeeds("test", "--tests", "*sub.SomeOtherTestSpec")

        then:
        // TODO: Update org/gradle/testing/AbstractTestFilteringIntegrationTest.groovy when de-incubated
        outputContains("Filtering non-class-based tests is an incubating feature.")
        resultsFor()
            .assertTestPathsExecuted(":sub/SomeOtherTestSpec.rbt:other")
            .assertTestPathsNotExecuted(":SomeTestSpec.rbt:foo")
            .assertTestPathsNotExecuted(":SomeTestSpec.rbt:bar")
    }
}
