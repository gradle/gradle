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
        return TestFramework.JUNIT_JUPITER
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
        resultsFor("tests/integrationTest").assertTestPathsExecuted(":SomeTestSpec.rbt - foo", ":SomeTestSpec.rbt - bar", ":SomeOtherTestSpec.rbt - other")
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
        resultsFor().assertAtLeastTestPathsExecuted(":SomeThirdTestSpec.rbt - third")
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
        succeeds("test")

        then:
        nonClassBasedTestsExecuted()
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

        containsLine(result.getOutput(), "START [Test SomeTestSpec.rbt - foo()] [SomeTestSpec.rbt - foo]")
        containsLine(result.getOutput(), "FINISH [Test SomeTestSpec.rbt - foo()] [SomeTestSpec.rbt - foo] [SUCCESS] [1] [null]")
        containsLine(result.getOutput(), "START [Test SomeTestSpec.rbt - bar()] [SomeTestSpec.rbt - bar]")
        containsLine(result.getOutput(), "FINISH [Test SomeTestSpec.rbt - bar()] [SomeTestSpec.rbt - bar] [SUCCESS] [1] [null]")
        containsLine(result.getOutput(), "START [Test SomeOtherTestSpec.rbt - other()] [SomeOtherTestSpec.rbt - other]")
        containsLine(result.getOutput(), "FINISH [Test SomeOtherTestSpec.rbt - other()] [SomeOtherTestSpec.rbt - other] [SUCCESS] [1] [null]")
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

        containsLine(result.getOutput(), "START [Test SomeTestSpec.rbt - foo()] [SomeTestSpec.rbt - foo]")
        containsLine(result.getOutput(), "FINISH [Test SomeTestSpec.rbt - foo()] [SomeTestSpec.rbt - foo] [SKIPPED] [1] [null]")
        containsLine(result.getOutput(), "START [Test SomeTestSpec.rbt - bar()] [SomeTestSpec.rbt - bar]")
        containsLine(result.getOutput(), "FINISH [Test SomeTestSpec.rbt - bar()] [SomeTestSpec.rbt - bar] [SKIPPED] [1] [null]")
        containsLine(result.getOutput(), "START [Test SomeOtherTestSpec.rbt - other()] [SomeOtherTestSpec.rbt - other]")
        containsLine(result.getOutput(), "FINISH [Test SomeOtherTestSpec.rbt - other()] [SomeOtherTestSpec.rbt - other] [SKIPPED] [1] [null]")
    }

    def "invalid path filter handled gracefully (filter type = #filterType) "() {
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
                            $filterType "mis(matched/parens"
                        }
                    }
                }
            }
        """
        writeTestDefinitions()

        when:
        fails("test")

        then:
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Path filter pattern is not a valid regex: mis(matched/parens")

        where:
        filterType << ["includeTestsMatching", "excludeTestsMatching"]
    }

    def "resource-based test engine can exclude test definitions (with leading slash = #leadingSlash)"() {
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
                            excludeTestsMatching "${leadingSlash ? "/" : ""}src/test/definitions/SomeTestSpec.rbt"
                        }
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(":SomeOtherTestSpec.rbt - other")

        where:
        leadingSlash << [true, false]
    }

    def "resource-based test engine can exclude test definitions in subdirectories (using pattern = #filterPattern)"() {
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
                            excludeTestsMatching "$filterPattern"
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
        resultsFor().assertTestPathsExecuted(":SomeTestSpec.rbt - foo", ":SomeTestSpec.rbt - bar", ":SomeOtherTestSpec.rbt - other")

        where:
        filterPattern << ["src/test/definitions/AdditionalDir/.*|src/test/definitions/subdir1/AdditionalDir/.*", ".*/AdditionalDir/.*"]
    }

    def "resource-based test engine can include test definitions (with leading slash = #leadingSlash)"() {
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
                            includeTestsMatching "${leadingSlash ? "/" : ""}src/test/definitions/SomeTestSpec.rbt"
                        }
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(":SomeTestSpec.rbt - foo", ":SomeTestSpec.rbt - bar")

        where:
        leadingSlash << [true, false]
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
                            includeTestsMatching ".*/subdir1/SomeTestSpec.*"
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
        resultsFor().assertTestPathsExecuted(":SomeTestSpec.rbt - subfoo")

        where:
        filterPattern << [".*/subdir1/SomeTestSpec.*", ".*/SomeTestSpec.*", ".*/SomeTestSpec.rbt", "/src/test/definitions/subdir1/SomeTestSpec.rbt"]
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
                            includeTestsMatching ".*/SomeTestSpec.*"
                            excludeTestsMatching ".*/SomeTestSpecThatShouldntRun.*"
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
        resultsFor().assertTestPathsExecuted(":SomeTestSpec.rbt - foo", ":SomeTestSpec.rbt - bar")
    }

    def "when running class-based and non-class-based tests, filters with slashes only apply to non-class-based tests"() {
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
                            excludeTestsMatching ".*/definitions/SampleTest.*"
                        }
                    }
                }
            }
        """

        file("src/test/java/definitions/SampleTest.java") << """
            package definitions;

            import org.junit.jupiter.api.Test;

            public class SampleTest {
                @Test
                public void foo() {
                    System.out.println("Tested!");
                }
            }
        """

        file("$DEFAULT_DEFINITIONS_LOCATION/SampleTest.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
            </tests>
        """

        when:
        succeeds("test", "--info")

        then:
        outputDoesNotContain("INFO: Executing resource-based test: Test[file=SampleTest.rbt, name=foo]")
        resultsFor().assertTestPathsExecuted(":definitions.SampleTest:foo()")
    }

    def "when running class-based and non-class-based tests, filters without slashes only apply to class-based tests"() {
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
                            excludeTestsMatching "definitions.SampleTest"
                        }
                    }
                }
            }
        """

        file("src/test/java/definitions/SampleTest.java") << """
            package definitions;

            import org.junit.jupiter.api.Test;

            public class SampleTest {
                @Test
                public void foo() {
                    System.out.println("Tested!");
                }
            }
        """

        file("$DEFAULT_DEFINITIONS_LOCATION/SampleTest.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
            </tests>
        """

        when:
        succeeds("test", "--info")

        then:
        outputContains("INFO: Executing resource-based test: Test[file=SampleTest.rbt, name=foo]")
        resultsFor().assertTestPathsNotExecuted(":definitions.SampleTest:foo()")
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
        succeeds("test", "--tests", "src/test/definitions/sub/SomeOtherTestSpec.rbt")

        then:
        resultsFor()
            .assertTestPathsExecuted(":SomeOtherTestSpec.rbt - other")
            .assertTestPathsNotExecuted(":SomeTestSpec.rbt - foo")
            .assertTestPathsNotExecuted(":SomeTestSpec.rbt - bar")
    }

    def "dv distribution fails if no currently connected agent matches all requirements"() {
        given:
        javaSimpleJunitTestClass()

        settingsFile << """
            buildscript {
                repositories {
                    gradlePluginPortal()
                }

                dependencies {
                    classpath("com.gradle:develocity-gradle-plugin:4.2.2")
                }
            }

            apply(plugin: com.gradle.develocity.agent.gradle.DevelocityPlugin)

            develocity {
                buildScan {
                    termsOfUseUrl = 'https://gradle.com/help/legal-terms-of-use'
                    termsOfUseAgree = 'yes'
                }
            }

            rootProject.name = 'develocity-no-agent-match'
        """

        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13.2'
                testRuntimeOnly 'org.junit.vintage:junit-vintage-engine:5.13.4'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.13.4'
            }

            test {
                useJUnitPlatform()

                develocity {
                    testDistribution {
                        enabled = true
                        allowUntrustedServer = true
                        maxLocalExecutors = 0
                        requirements = ['os=nonExistingOs']
                    }
                }
            }
        """

        when:
        fails("test", "-S")

        then:
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Remote executors cannot be used as no Develocity server is configured. Try setting maxLocalExecutors > 0.")

        then: 'since the test task itself fails and not the tests, we do not expect a test report'
        !file("build/reports/tests/test/index.html").exists()
    }

    def "executer fails during execution"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            class BadExecuter implements org.gradle.api.internal.tasks.testing.TestExecuter {
                @Override
                public void execute(org.gradle.api.internal.tasks.testing.TestExecutionSpec spec, org.gradle.api.internal.tasks.testing.TestResultProcessor resultProcessor) {
                    throw new RuntimeException("Bad executer always fails")
                }

                @Override
                public void stopNow() {

                }
            }

            testing.suites.test {
                useJUnitJupiter()

                targets.all {
                    testTask.configure {
                        setTestExecuter(new BadExecuter())
                    }
                }
            }
        """

        writeTestClasses()

        when:
        fails("test")

        then:
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Bad executer always fails")

        then: 'since the test task itself fails and not the tests, we do not expect a test report'
        !file("build/reports/tests/test/index.html").exists()
    }

    private void javaSimpleJunitTestClass(String packageName = 'example',
                                         String className = 'ExampleTest',
                                         String testName = 'aTest',
                                         boolean sleepsSuccessful = true,
                                         boolean sleepsFailed = true) {
        file('src/test/java/example/ExampleTest.java') << """
            package $packageName;
            public class ${className} {
                @org.junit.Test
                public void ${testName}Successful() {
                    ${sleepsSuccessful ? 'sleep();' : ''}
                    System.out.println("I feel this test ($testName) will succeed !");
                    System.err.println("Let's see !");
                }
                @org.junit.Test
                @org.junit.Ignore
                public void ${testName}Skipped() {
                    // test is skipped
                }
                @org.junit.Test
                public void ${testName}Failed() {
                    ${sleepsFailed ? 'sleep();' : ''}
                    System.out.println("I feel this test ($testName) will fail !");
                    System.err.println("You're probably right");
                    org.junit.Assert.fail("${testName} is failing !");
                }
                private void sleep(){
                    try {
                        Thread.sleep((long)(1+Math.random())*1000);
                    } catch (InterruptedException e) {
                        org.junit.Assert.fail();
                    }
                }
            }
        """.stripIndent(true)
    }
}
