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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import spock.lang.Issue

class TestSuitesIntegrationTest extends AbstractIntegrationSpec {
    def "new test suites adds appropriate test tasks"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

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

            ${mavenCentralRepository()}

            tasks.test {
                doLast {
                    assert testFramework instanceof ${JUnitTestFramework.canonicalName}
                    assert classpath.empty
                }
            }
        """

        expect:
        succeeds("test")
    }

    def "configuring test framework on built-in test suite is honored in task and dependencies with JUnit"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnit()
                    }
                }
            }

            tasks.test {
                doLast {
                    assert testFramework instanceof ${JUnitTestFramework.canonicalName}
                    assert classpath.size() == 2
                    assert classpath.any { it.name == "junit-${DefaultJvmTestSuite.TestingFramework.JUNIT4.getDefaultVersion()}.jar" }
                }
            }
        """

        expect:
        succeeds("test")
    }

    def "configuring test framework on built-in test suite is honored in task and dependencies with JUnit and explicit version"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnit("4.12")
                    }
                }
            }

            tasks.test {
                doLast {
                    assert testFramework instanceof ${JUnitTestFramework.canonicalName}
                    assert classpath.size() == 2
                    assert classpath.any { it.name == "junit-4.12.jar" }
                }
            }
        """

        expect:
        succeeds("test")
    }

    def "configuring test framework on built-in test suite using a Provider is honored in task and dependencies with JUnit and explicit version"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            Provider<String> junitVersion = project.provider(() -> '4.12')

            testing {
                suites {
                    test {
                        useJUnit(junitVersion)
                    }
                }
            }

            tasks.test {
                doLast {
                    assert testFramework instanceof ${JUnitTestFramework.canonicalName}
                    assert classpath.size() == 2
                    assert classpath.any { it.name == "junit-4.12.jar" }
                }
            }
        """

        expect:
        succeeds("test")
    }

    def "configuring test framework on built-in test suite is honored in task and dependencies with JUnit Jupiter"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }

            tasks.test {
                doLast {
                    assert test.testFramework instanceof ${JUnitPlatformTestFramework.canonicalName}
                    assert classpath.size() == 8
                    assert classpath.any { it.name =~ /junit-platform-launcher-.*.jar/ }
                    assert classpath.any { it.name == "junit-jupiter-${DefaultJvmTestSuite.TestingFramework.JUNIT_JUPITER.getDefaultVersion()}.jar" }
                }
            }
        """

        expect:
        succeeds("test")
    }

    def "configuring test framework on built-in test suite is honored in task and dependencies with JUnit Jupiter with explicit version"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter("5.7.2")
                    }
                }
            }

            tasks.test {
                doLast {
                    assert testFramework instanceof ${JUnitPlatformTestFramework.canonicalName}
                    assert classpath.size() == 9
                    assert classpath.any { it.name == "junit-jupiter-5.7.2.jar" }
                }
            }
        """

        expect:
        succeeds("test")
    }

    def "configuring test framework on built-in test suite using a Provider is honored in task and dependencies with JUnit Jupiter with explicit version"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            Provider<String> junitVersion = project.provider(() -> '5.7.2')

            testing {
                suites {
                    test {
                        useJUnitJupiter(junitVersion)
                    }
                }
            }

            tasks.test {
                doLast {
                    assert testFramework instanceof ${JUnitPlatformTestFramework.canonicalName}
                    assert classpath.size() == 9
                    assert classpath.any { it.name == "junit-jupiter-5.7.2.jar" }
                }
            }
        """

        expect:
        succeeds("test")
    }

    def "conventional test framework on custom test suite is JUnit Jupiter"() {
        buildFile << """
            plugins {
                id 'java'
            }
            ${mavenCentralRepository()}
            testing {
                suites {
                    integTest(JvmTestSuite)
                }
            }
            tasks.integTest {
                doLast {
                    assert testFramework instanceof ${JUnitPlatformTestFramework.canonicalName}
                    assert classpath.size() == 8
                    assert classpath.any { it.name =~ /junit-platform-launcher-.*.jar/ }
                    assert classpath.any { it.name == "junit-jupiter-${DefaultJvmTestSuite.TestingFramework.JUNIT_JUPITER.getDefaultVersion()}.jar" }
                }
            }
        """

        expect:
        succeeds("integTest")
        succeeds("test")
    }

    def "configuring test framework on custom test suite is honored in task and dependencies with #testingFrameworkDeclaration"() {
        buildFile << """
            plugins {
                id 'java'
            }
            ${mavenCentralRepository()}
            testing {
                suites {
                    integTest(JvmTestSuite) {
                        ${testingFrameworkDeclaration}
                    }
                }
            }
            tasks.integTest {
                doLast {
                    assert testFramework instanceof ${testingFrameworkType.canonicalName}
                    assert classpath.any { it.name == "${testingFrameworkDep}" }
                }
            }
        """

        expect:
        succeeds("integTest")

        where: // When testing a custom version, this should be a different version that the default
        testingFrameworkDeclaration  | testingFrameworkType       | testingFrameworkDep
        'useJUnit()'                 | JUnitTestFramework         | "junit-${DefaultJvmTestSuite.TestingFramework.JUNIT4.getDefaultVersion()}.jar"
        'useJUnit("4.12")'           | JUnitTestFramework         | "junit-4.12.jar"
        'useJUnitJupiter()'          | JUnitPlatformTestFramework | "junit-jupiter-${DefaultJvmTestSuite.TestingFramework.JUNIT_JUPITER.getDefaultVersion()}.jar"
        'useJUnitJupiter("5.7.1")'   | JUnitPlatformTestFramework | "junit-jupiter-5.7.1.jar"
        'useSpock()'                 | JUnitPlatformTestFramework | "spock-core-${DefaultJvmTestSuite.TestingFramework.SPOCK.getDefaultVersion()}.jar"
        'useSpock("2.2-groovy-3.0")' | JUnitPlatformTestFramework | "spock-core-2.2-groovy-3.0.jar"
        'useSpock("2.2-groovy-4.0")' | JUnitPlatformTestFramework | "spock-core-2.2-groovy-4.0.jar"
        'useKotlinTest()'            | JUnitPlatformTestFramework | "kotlin-test-junit5-${DefaultJvmTestSuite.TestingFramework.KOTLIN_TEST.getDefaultVersion()}.jar"
        'useKotlinTest("1.5.30")'    | JUnitPlatformTestFramework | "kotlin-test-junit5-1.5.30.jar"
        'useTestNG()'                | TestNGTestFramework        | "testng-${DefaultJvmTestSuite.TestingFramework.TESTNG.getDefaultVersion()}.jar"
        'useTestNG("7.3.0")'         | TestNGTestFramework        | "testng-7.3.0.jar"
    }

    def "configuring test framework on custom test suite using a Provider is honored in task and dependencies with #testingFrameworkMethod version #testingFrameworkVersion"() {
        buildFile << """
            plugins {
                id 'java'
            }
            ${mavenCentralRepository()}
            Provider<String> frameworkVersion = project.provider(() -> '$testingFrameworkVersion')
            testing {
                suites {
                    integTest(JvmTestSuite) {
                        $testingFrameworkMethod(frameworkVersion)
                    }
                }
            }
            tasks.integTest {
                doLast {
                    assert testFramework instanceof ${testingFrameworkType.canonicalName}
                    assert classpath.any { it.name == "$testingFrameworkDep" }
                }
            }
        """

        expect:
        succeeds("integTest")

        where: // When testing a custom version, this should be a different version that the default
        testingFrameworkMethod       | testingFrameworkVersion      | testingFrameworkType       | testingFrameworkDep
        'useJUnit'                   | '4.12'                       | JUnitTestFramework         | "junit-4.12.jar"
        'useJUnitJupiter'            | '5.7.1'                      | JUnitPlatformTestFramework | "junit-jupiter-5.7.1.jar"
        'useSpock'                   | '2.2-groovy-3.0'             | JUnitPlatformTestFramework | "spock-core-2.2-groovy-3.0.jar"
        'useSpock'                   | '2.2-groovy-4.0'             | JUnitPlatformTestFramework | "spock-core-2.2-groovy-4.0.jar"
        'useKotlinTest'              | '1.5.30'                     | JUnitPlatformTestFramework | "kotlin-test-junit5-1.5.30.jar"
        'useTestNG'                  | '7.3.0'                      | TestNGTestFramework        | "testng-7.3.0.jar"
    }

    def "can override previously configured test framework on a test suite"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        useJUnit()
                        useJUnitJupiter()
                    }
                }
            }

            testing {
                suites {
                    integTest {
                        useJUnit()
                    }
                }
            }

            tasks.integTest {
                doLast {
                    assert testFramework instanceof ${JUnitTestFramework.canonicalName}
                    assert classpath.size() == 2
                    assert classpath.any { it.name == "junit-4.13.2.jar" }
                }
            }
        """

        expect:
        succeeds("integTest")
    }

    def "task configuration overrules test suite configuration"() {
        file('src/integTest/java/FooTest.java') << """
            import org.junit.Test;

            public class FooTest {
                @Test
                public void test() {
                    System.out.println("Hello from FooTest");
                }
            }
        """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        // uses junit jupiter by default, but we'll change it to junit4 on the task
                        dependencies {
                            implementation 'junit:junit:4.13.2'
                        }
                        targets {
                            all {
                                testTask.configure {
                                    useJUnit()
                                    doFirst {
                                        assert testFramework instanceof ${JUnitTestFramework.canonicalName}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        expect:
        succeeds("integTest")

        and:
        result.assertTaskExecuted(":integTest")
    }

    def "task configuration overrules test suite configuration with test suite set test framework"() {
        file("src/integTest/java/FooTest.java") << """
            import org.junit.jupiter.api.Test;

            public class FooTest {
                @Test
                public void test() {
                    System.out.println("Hello from FooTest");
                }
            }
        """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        // set it to junit in the suite, but then we'll change it to junit platform on the task
                        useJUnit()
                        dependencies {
                            implementation 'org.junit.jupiter:junit-jupiter:5.7.1'
                            runtimeOnly 'org.junit.platform:junit-platform-launcher'
                        }
                        targets {
                            all {
                                testTask.configure {
                                    useJUnitPlatform()
                                    doFirst {
                                        assert testFramework instanceof ${JUnitPlatformTestFramework.canonicalName}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        expect:
        succeeds("integTest")

        and:
        result.assertTaskExecuted(":integTest")
    }

    @Issue("https://github.com/gradle/gradle/issues/18622")
    def "custom Test tasks eagerly realized prior to Java and Test Suite plugin application do not fail to be configured when combined with test suites"() {
        buildFile << """
            tasks.withType(Test) {
                // realize all test tasks
            }
            tasks.register("mytest", Test)
            apply plugin: 'java'

            ${mavenCentralRepository()}

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

        when:
        executer.expectDocumentedDeprecationWarning("Relying on the convention for Test.testClassesDirs in custom Test tasks has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_default_classpath")
        executer.expectDocumentedDeprecationWarning("Relying on the convention for Test.classpath in custom Test tasks has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_default_classpath")
        succeeds("mytest")

        then:
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

            def first = testing.suites.test.getTestSuiteTestingFramework()
            def second = testing.suites.test.getTestSuiteTestingFramework()

            tasks.register('assertSameFrameworkInstance') {
                doLast {
                    assert first.getOrNull() === second.getOrNull()
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

    def "eagerly iterating over dependency scope does not break automatic dependencies for test suite"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integTest(JvmTestSuite)
                }
            }

            // mimics behavior from https://github.com/JetBrains/kotlin/commit/4a172286217a1a7d4e7a7f0eb6a0bc53ebf56515
            configurations.integTestImplementation.dependencies.all { }

            testing {
                suites {
                    integTest {
                        useJUnit('4.12')
                    }
                }
            }

            tasks.integTest {
                doLast {
                    assert classpath.any { it.name == "junit-4.12.jar" }
                }
            }
        """

        expect:
        succeeds("integTest")
    }

    @Issue("https://github.com/gradle/gradle/issues/20846")
    def "when tests are NOT run they are NOT configured"() {
        given: "a build which will throw an exception upon configuring test tasks"
        file("build.gradle") << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            tasks.withType(Test).configureEach {
                throw new RuntimeException('Configuring tests failed')
            }
        """

        and: "containing a class to compile"
        file("src/main/java/org/test/App.java") << """
            public class App {
                public String getGreeting() {
                    return "Hello World!";
                }
            }
        """

        and: "containing a test"
        file("src/test/java/org/test/MyTest.java") << """
            package org.test;

            import org.junit.*;

            public class MyTest {
                @Test
                public void testSomething() {
                    App classUnderTest = new App();
                    Assert.assertNotNull(classUnderTest.getGreeting(), "app should have a greeting");
                }
            }
        """

        expect: "compilation does not configure tests"
        succeeds("compileJava")

        and: "running tests fails due to configuring tests"
        fails("test")
        failure.assertHasErrorOutput("Configuring tests failed")
    }

    @Issue("https://github.com/gradle/gradle/issues/20846")
    def "when tests are NOT run they are NOT configured - even when adding an implementation dep"() {
        given: "a build which will throw an exception upon configuring test tasks"
        file("build.gradle") << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'com.google.guava:guava:30.1.1-jre'
            }

            tasks.withType(Test).configureEach {
                throw new RuntimeException('Configuring tests failed')
            }
        """

        and: "containing a class to compile"
        file("src/main/java/org/test/App.java") << """
            public class App {
                public String getGreeting() {
                    return "Hello World!";
                }
            }
        """

        and: "containing a test"
        file("src/test/java/org/test/MyTest.java") << """
            package org.test;

            import org.junit.*;

            public class MyTest {
                @Test
                public void testSomething() {
                    App classUnderTest = new App();
                    Assert.assertNotNull(classUnderTest.getGreeting(), "app should have a greeting");
                }
            }
        """

        expect: "compilation does NOT configure tests"
        succeeds("compileJava")

        and: "running tests fails due to configuring tests"
        fails("test")
        failure.assertHasErrorOutput("Configuring tests failed")
    }

    @Issue("https://github.com/gradle/gradle/issues/20846")
    def "when tests are NOT run they are NOT configured - even when adding an implementation dep to the integration test suite"() {
        given: "a build which will throw an exception upon configuring test tasks"
        file("build.gradle") << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            tasks.withType(Test).configureEach {
                throw new RuntimeException('Configuring tests failed')
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        useJUnit()
                    }
                }
            }

            dependencies {
                integrationTestImplementation 'com.google.guava:guava:30.1.1-jre'
            }
        """

        and: "containing a class to compile"
        file("src/main/java/org/test/App.java") << """
            public class App {
                public String getGreeting() {
                    return "Hello World!";
                }
            }
        """

        and: "containing an integration test"
        file("src/integrationTest/java/org/test/MyTest.java") << """
            package org.test;

            import org.junit.*;

            public class MyTest {
                @Test
                public void testSomething() {
                    App classUnderTest = new App();
                    Assert.assertNotNull(classUnderTest.getGreeting(), "app should have a greeting");
                }
            }
        """

        expect: "compilation does NOT configure tests"
        succeeds("compileJava")

        and: "running integration tests fails due to configuring tests"
        fails("integrationTest")
        failure.assertHasErrorOutput("Configuring tests failed")
    }

    @Issue("https://github.com/gradle/gradle/issues/19065")
    def "test suites can add platforms using a #platformType with #format via coordinates"() {
        given: "a project defining a platform"
        file('platform/build.gradle') << """
            plugins {
                id 'java-platform'
            }

            group = "org.example.gradle"

            dependencies {
                constraints {
                    api 'org.assertj:assertj-core:3.22.0'
                }
            }
        """

        and: "an application project with a test suite using the platform"
        file('app/build.gradle') << """
            plugins {
                 id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()

                        dependencies {
                            implementation($expression)
                            implementation 'org.assertj:assertj-core'
                        }
                    }
                }
            }
        """
        file('app/src/test/java/org/example/app/ExampleTest.java') << """
            package org.example.app;

            import org.junit.jupiter.api.Test;
            import static org.assertj.core.api.Assertions.assertThat;

            public class ExampleTest {
                @Test public void testOK() {
                    assertThat(1 + 1).isEqualTo(2);
                }
            }
        """

        settingsFile << """
            dependencyResolutionManagement {
                includeBuild("platform")
            }

            rootProject.name = 'example-of-platform-in-test-suites'

            include("app")
        """

        expect: "should be able to reference the platform without failing"
        succeeds ':app:test'
        def unitTestResults = new JUnitXmlTestExecutionResult(testDirectory, 'app/build/test-results/test')
        unitTestResults.assertTestClassesExecuted('org.example.app.ExampleTest')

        where:
        format                              | platformType  | expression
        'single GAV string'                 | 'platform'            | "platform('org.example.gradle:platform')"
        'module method'                     | 'platform'            | "platform(module('org.example.gradle', 'platform', null))"
        'referencing project.dependencies'  | 'platform'            | "project.dependencies.platform('org.example.gradle:platform')"
        'single GAV string'                 | 'enforcedPlatform'    | "enforcedPlatform('org.example.gradle:platform')"
        'module method'                     | 'enforcedPlatform'    | "enforcedPlatform(module('org.example.gradle', 'platform', null))"
        'referencing project.dependencies'  | 'enforcedPlatform'    | "project.dependencies.enforcedPlatform('org.example.gradle:platform')"
    }

    @Issue("https://github.com/gradle/gradle/issues/19065")
    def "test suites can add project dependencies via coordinates"() {
        given: "a project used as a dependency"
        file('dep/build.gradle') << """
            plugins {
                id 'java-library'
            }

            group = "org.example.gradle"
        """
        file('dep/src/main/java/org/example/dep/Dep.java') << """
            package org.example.dep;
            public class Dep {}
        """

        and: "an application project with a test suite using a project dependency"
        file('app/build.gradle') << """
            plugins {
                 id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()

                        dependencies {
                            implementation('org.example.gradle:dep')
                        }
                    }
                }
            }
        """
        file('app/src/test/java/org/example/app/ExampleTest.java') << """
            package org.example.app;

            import org.junit.jupiter.api.Test;
            import org.example.dep.Dep;

            public class ExampleTest {
                @Test public void testOK() {
                    new Dep();
                }
            }
        """

        settingsFile << """
            dependencyResolutionManagement {
                includeBuild("dep")
            }

            rootProject.name = 'example-of-project-reference-in-test-suites'

            include("app")
        """

        expect: "should be able to reference the project without failing"
        succeeds ':app:test'
        def unitTestResults = new JUnitXmlTestExecutionResult(testDirectory, 'app/build/test-results/test')
        unitTestResults.assertTestClassesExecuted('org.example.app.ExampleTest')
    }

    @Issue("https://github.com/gradle/gradle/issues/19065")
    def "test suites can add self project dependency via coordinates"() {
        given: "an application project with a custom test suite with a dependency on the project"
        file('app/src/main/java/org/example/dep/Dep.java') << """
            package org.example.dep;
            public class Dep {}
        """
        file('app/build.gradle') << """
            plugins {
                 id 'java'
            }

            ${mavenCentralRepository()}

            group = "org.example.gradle"
            version = "1.0"

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        useJUnitJupiter()

                        dependencies {
                            implementation('org.example.gradle:app:1.0')
                        }
                    }
                }
            }

            tasks.named('compileIntegrationTestJava') {
                dependsOn(tasks.named('jar'))
            }
        """
        file('app/src/integrationTest/java/org/example/app/ExampleTest.java') << """
            package org.example.app;

            import org.junit.jupiter.api.Test;
            import org.example.dep.Dep;

            public class ExampleTest {
                @Test public void testOK() {
                    new Dep();
                }
            }
        """

        settingsFile << """
            rootProject.name = 'example-of-project-reference-in-test-suites'

            include("app")
        """
        executer.noDeprecationChecks()

        expect: "should be able to reference the project without failing"
        succeeds ':app:assemble', ':app:integrationTest'
        def unitTestResults = new JUnitXmlTestExecutionResult(testDirectory, 'app/build/test-results/integrationTest')
        unitTestResults.assertTestClassesExecuted('org.example.app.ExampleTest')
    }
}
