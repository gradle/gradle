/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.testsuites

import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework
import org.gradle.api.plugins.jvm.internal.DefaultJvmTestSuite
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import spock.lang.Issue

class TestSuitesIntegrationTest extends AbstractIntegrationSpec {
    def "new test suites adds appropriate test tasks"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }
            testing {
                suites {
                    eagerTest(JvmTestSuite)
                    register("registerTest", JvmTestSuite)
                }
            }
        """
        expect:
        succeeds("eagerTest")
        succeeds("registerTest")
    }

    def "built-in test suite does not have any testing framework set at the test suite level"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            task checkConfiguration {
                dependsOn test
                doLast {
                    assert test.testFramework instanceof ${JUnitTestFramework.canonicalName}
                    assert configurations.testRuntimeClasspath.files.empty
                }
            }
        """
        expect:
        succeeds("checkConfiguration")
    }

    def "configuring test framework on built-in test suite is honored in task and dependencies with JUnit"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                    }
                }
            }

            task checkConfiguration {
                dependsOn test
                doLast {
                    assert test.testFramework instanceof ${JUnitTestFramework.canonicalName}
                    assert configurations.testRuntimeClasspath.files.size() == 2
                    assert configurations.testRuntimeClasspath.files.any { it.name == "junit-${DefaultJvmTestSuite.Frameworks.JUNIT4.getDefaultVersion()}.jar" }
                }
            }
        """
        expect:
        succeeds("checkConfiguration")
    }

    def "configuring test framework on built-in test suite is honored in task and dependencies with JUnit and explicit version"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit("4.12")
                    }
                }
            }

            task checkConfiguration {
                dependsOn test
                doLast {
                    assert test.testFramework instanceof ${JUnitTestFramework.canonicalName}
                    assert configurations.testRuntimeClasspath.files.size() == 2
                    assert configurations.testRuntimeClasspath.files.any { it.name == "junit-4.12.jar" }
                }
            }
        """
        expect:
        succeeds("checkConfiguration")
    }

    def "configuring test framework on built-in test suite is honored in task and dependencies with JUnit Jupiter"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }

            task checkConfiguration {
                dependsOn test
                doLast {
                    assert test.testFramework instanceof ${JUnitPlatformTestFramework.canonicalName}
                    assert configurations.testRuntimeClasspath.files.size() == 8
                    assert configurations.testRuntimeClasspath.files.any { it.name == "junit-jupiter-${DefaultJvmTestSuite.Frameworks.JUNIT_JUPITER.getDefaultVersion()}.jar" }
                }
            }
        """
        expect:
        succeeds("checkConfiguration")
    }

    def "configuring test framework on built-in test suite is honored in task and dependencies with JUnit Jupiter with explicit version"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnitJupiter("5.7.2")
                    }
                }
            }

            task checkConfiguration {
                dependsOn test
                doLast {
                    assert test.testFramework instanceof ${JUnitPlatformTestFramework.canonicalName}
                    assert configurations.testRuntimeClasspath.files.size() == 8
                    assert configurations.testRuntimeClasspath.files.any { it.name == "junit-jupiter-5.7.2.jar" }
                }
            }
        """
        expect:
        succeeds("checkConfiguration")
    }

    def "conventional test framework on custom test suite is JUnit Jupiter"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    integTest(JvmTestSuite)
                }
            }

            task checkConfiguration {
                dependsOn integTest
                doLast {
                    assert integTest.testFramework instanceof ${JUnitPlatformTestFramework.canonicalName}
                    assert configurations.integTestRuntimeClasspath.files.size() == 8
                    assert configurations.integTestRuntimeClasspath.files.any { it.name == "junit-jupiter-${DefaultJvmTestSuite.Frameworks.JUNIT_JUPITER.getDefaultVersion()}.jar" }
                }
            }
        """
        expect:
        succeeds("checkConfiguration")
    }

    def "configuring test framework on custom test suite is honored in task and dependencies with #testingFrameworkDeclaration"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        ${testingFrameworkDeclaration}
                    }
                }
            }

            task checkConfiguration {
                dependsOn integTest
                doLast {
                    assert integTest.testFramework instanceof ${testingFrameworkType.canonicalName}
                    assert configurations.integTestRuntimeClasspath.files.any { it.name == "${testingFrameworkDep}" }
                }
            }
        """
        expect:
        succeeds("checkConfiguration")

        where: // When testing a custom version, this should be a different version that the default
        testingFrameworkDeclaration  | testingFrameworkType       | testingFrameworkDep
        'useJUnit()'                 | JUnitTestFramework         | "junit-${DefaultJvmTestSuite.Frameworks.JUNIT4.getDefaultVersion()}.jar"
        'useJUnit("4.12")'           | JUnitTestFramework         | "junit-4.12.jar"
        'useJUnitJupiter()'          | JUnitPlatformTestFramework | "junit-jupiter-${DefaultJvmTestSuite.Frameworks.JUNIT_JUPITER.getDefaultVersion()}.jar"
        'useJUnitJupiter("5.7.1")'   | JUnitPlatformTestFramework | "junit-jupiter-5.7.1.jar"
        'useSpock()'                 | JUnitPlatformTestFramework | "spock-core-${DefaultJvmTestSuite.Frameworks.SPOCK.getDefaultVersion()}.jar"
        'useSpock("2.1-groovy-3.0")' | JUnitPlatformTestFramework | "spock-core-2.1-groovy-3.0.jar" // Not possible to test a different version from the default yet, since this is the first groovy 3.0 targeted release
        'useKotlinTest()'            | JUnitTestFramework         | "kotlin-test-junit-${DefaultJvmTestSuite.Frameworks.KOTLIN_TEST.getDefaultVersion()}.jar"
        'useKotlinTest("1.5.30")'    | JUnitTestFramework         | "kotlin-test-junit-1.5.30.jar"
        'useTestNG()'                | TestNGTestFramework        | "testng-${DefaultJvmTestSuite.Frameworks.TESTNG.getDefaultVersion()}.jar"
        'useTestNG("7.3.0")'         | TestNGTestFramework        | "testng-7.3.0.jar"
    }

    def "can override previously configured test framework on a test suite"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        useJUnit()
                        useJUnitJupiter()
                    }
                }
            }

            task checkConfigurationIsJupiter {
                dependsOn integTest
                doLast {
                    assert integTest.testFramework instanceof ${JUnitPlatformTestFramework.canonicalName}
                    assert configurations.integTestRuntimeClasspath.files.size() == 8
                    assert configurations.integTestRuntimeClasspath.files.any { it.name == "junit-jupiter-${DefaultJvmTestSuite.Frameworks.JUNIT_JUPITER.getDefaultVersion()}.jar" }
                }
            }
            task checkConfigurationIsJUnit {
                dependsOn integTest
                doLast {
                    assert test.testFramework instanceof ${JUnitTestFramework.canonicalName}
                    assert configurations.integTestRuntimeClasspath.files.size() == 2
                    assert configurations.integTestRuntimeClasspath.files.any { it.name == "junit-4.13.jar" }
                }
            }
        """
        expect:
        succeeds("checkConfigurationIsJupiter")

        buildFile << """
            testing {
                suites {
                    integTest {
                        useJUnit()
                    }
                }
            }
        """
        // Now we're using JUnit again
        succeeds("checkConfigurationIsJUnit")
    }

    def "task configuration overrules test suite configuration"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        // uses junit jupiter by default
                        targets {
                            all {
                                testTask.configure {
                                    useJUnit()
                                }
                            }
                        }
                    }
                }
            }

            task checkConfiguration {
                dependsOn integTest
                doLast {
                    // task is configured to use JUnit4
                    assert integTest.testFramework instanceof ${JUnitTestFramework.canonicalName}

                    // but test suite still adds JUnit Jupiter
                    assert configurations.integTestRuntimeClasspath.files.size() == 8
                    assert configurations.integTestRuntimeClasspath.files.any { it.name == "junit-jupiter-${DefaultJvmTestSuite.Frameworks.JUNIT_JUPITER.getDefaultVersion()}.jar" }
                }
            }
        """
        expect:
        succeeds("checkConfiguration")
    }

    def "task configuration overrules test suite configuration with test suite set test framework"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        useJUnit()
                        targets {
                            all {
                                testTask.configure {
                                    useJUnitPlatform()
                                }
                            }
                        }
                    }
                }
            }

            task checkConfiguration {
                dependsOn integTest
                doLast {
                    // task is configured to use JUnit Jupiter
                    assert integTest.testFramework instanceof ${JUnitPlatformTestFramework.canonicalName}

                    // but test suite still adds JUnit4
                    assert configurations.integTestRuntimeClasspath.files.size() == 2
                    assert configurations.integTestRuntimeClasspath.files.any { it.name == "junit-4.13.jar" }
                }
            }
        """
        expect:
        succeeds("checkConfiguration")
    }

    // TODO: Test Framework Selection - Revert this to may NOT in Gradle 8
    def "test framework MAY be changed once options have been used with test suites"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        useJUnit()
                        targets.all {
                            testTask.configure {
                                options {
                                    excludeCategories "com.example.Exclude"
                                }
                            }
                        }
                    }
                }
            }

            integrationTest {
                useTestNG()
            }

            check.dependsOn testing.suites
        """

        executer.expectDeprecationWarning("Accessing test options prior to setting test framework has been deprecated. This is scheduled to be removed in Gradle 8.0.")

        expect:
        succeeds("check")
    }

    // This checks for backwards compatibility with builds that may rely on this
    def "can change the test framework multiple times before execution when not using test suites"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }
            ${mavenCentralRepository()}
            dependencies { testImplementation "junit:junit:4.13" }

            test {
                useJUnit()
                options {
                    assert it instanceof ${JUnitOptions.canonicalName}
                }
                useJUnitPlatform()
                options {
                    assert it instanceof ${JUnitPlatformOptions.canonicalName}
                }
                useJUnit()
            }
        """

        and:
        file("src/test/java/SomeTest.java") << """
            import org.junit.*;

            public class SomeTest {
                @Test public void foo() {
                }
            }
        """

        executer.expectDeprecationWarning("Accessing test options prior to setting test framework has been deprecated. This is scheduled to be removed in Gradle 8.0.")
        executer.expectDeprecationWarning("Accessing test options prior to setting test framework has been deprecated. This is scheduled to be removed in Gradle 8.0.")

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("SomeTest")
    }

    // This is not the behavior we want in the long term because this makes build configuration sensitive to the order
    // that tasks are realized.
    // useTestNG() is ignored here because we finalize the test framework on the task as soon as we configure options
    // The test framework options should be pushed up into the test suite target/test suite and passed down into the
    // Test task
    def "build succeeds when test framework is changed to another kind when realizing task and configuring options"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        useJUnit()
                        targets.all {
                            // explicitly realize the task now to cause this configuration to run now
                            testTask.get().configure {
                                options {
                                    excludeCategories "com.example.Exclude"
                                }
                            }
                        }
                    }
                }
            }

            testing {
                suites {
                    integrationTest {
                        // This is ignored
                        useTestNG()
                    }
                }
            }

            check.dependsOn testing.suites
        """

        expect:
        succeeds("check")
    }

    @Issue("https://github.com/gradle/gradle/issues/18622")
    def "custom Test tasks eagerly realized prior to Java and Test Suite plugin application do not fail to be configured when combined with test suites"() {
        buildFile << """
            tasks.withType(Test) {
                // realize all test tasks
            }
            tasks.register("mytest", Test)
            apply plugin: 'java'

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                    }
                }
            }
"""
        file('src/test/java/example/UnitTest.java') << '''
            package example;

            import org.junit.Assert;
            import org.junit.Test;

            public class UnitTest {
                @Test
                public void unitTest() {
                    Assert.assertTrue(true);
                }
            }
        '''
        expect:
        succeeds("mytest")
        def unitTestResults = new JUnitXmlTestExecutionResult(testDirectory, 'build/test-results/mytest')
        unitTestResults.assertTestClassesExecuted('example.UnitTest')
    }

    @Issue("https://github.com/gradle/gradle/issues/18622")
    def "custom Test tasks still function if java plugin is never applied to create sourcesets"() {
        buildFile << """
            tasks.withType(Test) {
                // realize all test tasks
            }

            def customClassesDir = file('src/custom/java')
            tasks.register("mytest", Test) {
                // Must ensure a base dir is set here
                testClassesDirs = files(customClassesDir)
            }

            task assertNoTestClasses {
                inputs.files mytest.testClassesDirs

                doLast {
                    assert inputs.files.contains(customClassesDir)
                }
            }
        """
        expect:
        succeeds("mytest", "assertNoTestClasses")
    }

    def "multiple getTestingFramework() calls on a test suite return same instance"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            def first = testing.suites.test.getVersionedTestingFramework()
            def second = testing.suites.test.getVersionedTestingFramework()

            tasks.register('assertSameFrameworkInstance') {
                doLast {
                    assert first === second
                }
            }""".stripIndent()

        expect:
        succeeds("assertSameFrameworkInstance")
    }

    def "multiple getTestingFramework() calls on a test suite return same instance even when calling useJUnit"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            def first = testing.suites.test.getVersionedTestingFramework()

            testing {
                suites {
                    test {
                        useJUnit()
                    }
                }
            }

            def second = testing.suites.test.getVersionedTestingFramework()

            tasks.register('assertSameFrameworkInstance') {
                doLast {
                    assert first === second
                }
            }""".stripIndent()

        expect:
        succeeds("assertSameFrameworkInstance")
    }

    def "multiple getTestingFramework() calls on a test suite return same instance even after toggling testing framework"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            def first = testing.suites.test.getVersionedTestingFramework()

            testing.suites.test.useJUnit()
            testing.suites.test.useTestNG()
            testing.suites.test.useJUnit()

            def second = testing.suites.test.getVersionedTestingFramework()

            tasks.register('assertSameFrameworkInstance') {
                doLast {
                    assert first === second
                }
            }""".stripIndent()

        expect:
        succeeds("assertSameFrameworkInstance")
    }

    def "the default test suite does NOT use JUnit 4 by default"() {
        given: "a build which uses the default test suite and doesn't specify a testing framework"
        file("build.gradle") << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        // Empty
                    }
                }
            }
        """

        and: "containing a test which uses Junit 4"
        file("src/test/java/org/test/MyTest.java") << """
            package org.test;

            import org.junit.Test;
            import org.junit.Assert;

            public class MyTest {
                @Test
                public void testSomething() {
                    Assert.assertEquals(1, MyFixture.calculateSomething());
                }
            }
        """

        expect: "does NOT compile due to a missing dependency"
        fails("test")
        failure.assertHasErrorOutput("Compilation failed; see the compiler error output for details.")
        failure.assertHasErrorOutput("error: package org.junit does not exist")
    }
}
