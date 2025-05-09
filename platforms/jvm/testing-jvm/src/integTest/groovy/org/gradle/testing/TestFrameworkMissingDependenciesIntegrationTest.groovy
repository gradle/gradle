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
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.fixture.TestNGCoverage
import org.gradle.util.GradleVersion

/**
 * Tests behavior of different test frameworks when their required
 * runtime dependencies are not included on the test runtime classpath.
 */
class TestFrameworkMissingDependenciesIntegrationTest extends AbstractIntegrationSpec {
    def "junit 4 fails with sensible error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("junit:junit:${JUnitCoverage.LATEST_JUNIT4_VERSION}")
            }

            test.useJUnit()
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.Test
                public void test() {}
            }
        """

        executer.withToolchainDetectionEnabled()
        executer.withStackTraceChecksDisabled()

        when:
        fails('test')

        then: "Test framework startup failure is reported"
        result.assertHasErrorOutput("TestFrameworkStartupFailureException: Could not execute test class 'MyTest'")
        result.assertHasErrorOutput("TestFrameworkNotAvailableException: Failed to load JUnit 4")
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Could not start Gradle Test Executor")

        and: "Resolutions are provided"
        assertSuggestsCheckingTestFrameworkAvailability("JUnit 4")
        assertSuggestsUpgradeGuide()

        and: "No test class results created"
        new DefaultTestExecutionResult(testDirectory).testClassDoesNotExist("MyTest")
    }

    def "junit platform fails with sensible error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("org.junit.jupiter:junit-jupiter-api:${JUnitCoverage.LATEST_JUPITER_VERSION}")
            }

            test.useJUnitPlatform()
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.jupiter.api.Test
                void test() {}
            }
        """

        executer.withStackTraceChecksDisabled()

        when:
        fails('test')

        then: "Test framework startup failure is reported"
        result.assertHasErrorOutput("TestFrameworkStartupFailureException: Could not execute test class 'MyTest'")
        result.assertHasErrorOutput("TestFrameworkNotAvailableException: Failed to load JUnit Platform")
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Could not start Gradle Test Executor")

        and: "Resolutions are provided"
        assertSuggestsCheckingTestFrameworkAvailability("the JUnit Platform")
        assertSuggestsUpgradeGuide()

        and: "No test class results created"
        new DefaultTestExecutionResult(testDirectory).testClassDoesNotExist("MyTest")
    }

    def "testng fails with sensible error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("org.testng:testng:${TestNGCoverage.NEWEST}")
            }

            test.useTestNG()
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.testng.annotations.Test
                public void test() {}
            }
        """

        executer.withStackTraceChecksDisabled()

        when:
        fails('test')

        then: "Test framework startup failure is reported"
        result.assertHasErrorOutput("TestFrameworkStartupFailureException: Could not execute test class 'MyTest'")
        result.assertHasErrorOutput("TestFrameworkNotAvailableException: Failed to load TestNG")
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Could not start Gradle Test Executor")

        and: "Resolutions are provided"
        assertSuggestsCheckingTestFrameworkAvailability("TestNG")
        assertSuggestsUpgradeGuide()

        and: "No test class results created"
        new DefaultTestExecutionResult(testDirectory).testClassDoesNotExist("MyTest")
    }

    def "startup failures of multiple test classes in same test task displays sensible de-duplicated error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("junit:junit:${JUnitCoverage.LATEST_JUNIT4_VERSION}")
            }

            test.useJUnit()
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.Test
                public void test() {}
            }
        """

        file("src/test/java/MyTest2.java") << """
            public class MyTest2 {
                @org.junit.Test
                public void test() {}
            }
        """

        executer.withToolchainDetectionEnabled()
        executer.withStackTraceChecksDisabled()

        when:
        fails('test', '--continue')

        then: "Test framework startup failure is reported"
        result.assertHasErrorOutput("TestFrameworkStartupFailureException: Could not execute test class 'MyTest'")
        result.assertHasErrorOutput("TestFrameworkNotAvailableException: Failed to load JUnit 4")
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Could not start Gradle Test Executor")

        and: "Second class failure is NOT reported"
        !result.error.contains("TestFrameworkStartupFailureException: Could not execute test class 'MyTest2'")
    }

    def "startup failures of multiple test classes across multiple test tasks displays sensible comprehensive error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            sourceSets {
                otherTest
            }

            dependencies {
                testCompileOnly("junit:junit:${JUnitCoverage.LATEST_JUNIT4_VERSION}")
                otherTestCompileOnly("junit:junit:${JUnitCoverage.LATEST_JUNIT4_VERSION}")
            }

            test.useJUnit()

            tasks.register("otherTest", Test) {
                useJUnit()
                classpath = sourceSets.otherTest.runtimeClasspath
                testClassesDirs = sourceSets.otherTest.output.classesDirs
            }
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.Test
                public void test() {}
            }
        """

        file("src/test/java/MyTest2.java") << """
            public class MyTest2 {
                @org.junit.Test
                public void test() {}
            }
        """

        file("src/otherTest/java/MyTest3.java") << """
            public class MyTest3 {
                @org.junit.Test
                public void test() {}
            }
        """

        file("src/otherTest/java/MyTest4.java") << """
            public class MyTest4 {
                @org.junit.Test
                public void test() {}
            }
        """

        executer.withToolchainDetectionEnabled()
        executer.withStackTraceChecksDisabled()

        when:
        fails('test', 'otherTest', '--continue')

        then: "Both tests fail"
        failure.assertHasFailures(2)

        and: "Test framework startup failure is reported..."
        result.assertHasErrorOutput("TestFrameworkStartupFailureException: Could not execute test class 'MyTest'")
        result.assertHasErrorOutput("TestFrameworkNotAvailableException: Failed to load JUnit 4")
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Could not start Gradle Test Executor")

        and: "...for first class in each task"
        result.assertHasErrorOutput("TestFrameworkStartupFailureException: Could not execute test class 'MyTest3'")
        result.assertHasErrorOutput("TestFrameworkNotAvailableException: Failed to load JUnit 4")
        failureDescriptionContains("Execution failed for task ':otherTest'.")
        failureCauseContains("Could not start Gradle Test Executor")
    }

    private assertSuggestsCheckingTestFrameworkAvailability(String testFramework) {
        failure.assertHasResolution("Please ensure that $testFramework is available on the test runtime classpath.")
    }

    private assertSuggestsUpgradeGuide() {
        failure.assertHasResolution("See the upgrade guide for more details: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#test_framework_implementation_dependencies.")
    }
}
