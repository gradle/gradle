/*
 * Copyright 2011 the original author or authors.
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

import org.apache.commons.lang.RandomStringUtils
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.testing.fixture.AbstractJUnitMultiVersionIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.hamcrest.CoreMatchers
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.equalTo

/**
 * General tests for the JVM testing infrastructure that don't deserve their own test class.
 */
abstract class AbstractTestingIntegrationTest extends AbstractJUnitMultiVersionIntegrationTest implements JavaToolchainFixture {
    @Issue("https://issues.gradle.org/browse/GRADLE-1948")
    def "test interrupting its own thread does not kill test execution"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        and:
        file("src/test/java/SomeTest.java") << """
            ${testFrameworkImports}

            public class SomeTest {
                @Test public void foo() {
                    Thread.currentThread().interrupt();
                }
            }
        """.stripIndent()

        when:
        run "test"

        then:
        executedAndNotSkipped(":test")
    }

    def "fails cleanly even if an exception is thrown that doesn't serialize cleanly"() {
        given:
        file('src/test/java/ExceptionTest.java') << """
            ${testFrameworkImports}
            import java.io.*;

            public class ExceptionTest {

                static class BadlyBehavedException extends Exception {
                    BadlyBehavedException() {
                        super("Broken writeObject()");
                    }

                    private void writeObject(ObjectOutputStream os) throws IOException {
                        throw new IOException("Failed strangely");
                    }
                }

                @Test
                public void testThrow() throws Throwable {
                    throw new BadlyBehavedException();
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        when:
        runAndFail "test"

        then:
        failureHasCause "There were failing tests"

        and:
        def results = new DefaultTestExecutionResult(file("."))
        results.assertTestClassesExecuted("ExceptionTest")
        results.testClass("ExceptionTest").assertTestFailed("testThrow", equalTo('ExceptionTest$BadlyBehavedException: Broken writeObject()'))
    }

    def "fails cleanly even if an exception is thrown that doesn't de-serialize cleanly"() {
        given:

        file('src/test/java/ExceptionTest.java') << """
            ${testFrameworkImports}
            import java.io.*;

            public class ExceptionTest {
                static class BadlyBehavedException extends Exception {
                    BadlyBehavedException() {
                        super("Broken readObject()");
                    }

                    private void readObject(ObjectInputStream os) throws IOException {
                        throw new IOException("Failed strangely");
                    }
                }

                @Test
                public void testThrow() throws Throwable {
                    throw new BadlyBehavedException();
                }
            }
        """.stripIndent()
        file('build.gradle') << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        when:
        // an exception was thrown so we should fail here
        runAndFail "test"

        then:
        failureHasCause "There were failing tests"

        and:
        def results = new DefaultTestExecutionResult(file("."))
        results.assertTestClassesExecuted("ExceptionTest")
        results.testClass("ExceptionTest").assertTestFailed("testThrow", equalTo('ExceptionTest$BadlyBehavedException: Broken readObject()'))
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "can use long paths for working directory"() {
        given:
        // windows can handle a path up to 260 characters
        // we create a path that is 260 +1 (offset + "/" + randompath)
        def pathoffset = 260 - testDirectory.getAbsolutePath().length()
        def alphanumeric = RandomStringUtils.randomAlphanumeric(pathoffset)
        def testWorkingDir = testDirectory.createDir("$alphanumeric")

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
            test.workingDir = "${testWorkingDir.toURI()}"
        """.stripIndent()

        and:
        file("src/test/java/SomeTest.java") << """
            ${testFrameworkImports}

            public class SomeTest {
                @Test public void foo() {
                    System.out.println(new java.io.File(".").getAbsolutePath());
                }
            }
        """.stripIndent()

        expect:
        succeeds "test"
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2962")
    def "incompatible user versions of classes that we also use don't affect test execution"() {

        // These dependencies are quite particular.
        // Both jars contain 'com.google.common.collect.ImmutableCollection'
        // 'google-collections' contains '$EmptyImmutableCollection' which extends '$AbstractImmutableCollection' which is also in guava 15.
        // In the google-collections version '$EmptyImmutableCollection' overrides `toArray()`.
        // In guava 15, this method is final.
        // This causes a verifier error when loading $EmptyImmutableCollection (can't override final method).

        // Our test infrastructure loads org.gradle.util.SystemProperties, which depends on $EmptyImmutableCollection from guava 14.
        // The below test is testing that out infrastructure doesn't throw a VerifyError while bootstrapping.
        // This is testing classloader isolation, but this was not the real problem that triggered GRADLE-2962.
        // The problem was that we tried to load the user's $EmptyImmutableCollection in a class loader structure we wouldn't have used anyway,
        // but this caused the infrastructure to fail with an internal error because of the VerifyError.
        // In a nutshell, this tests that we don't even try to load classes that are there, but that we shouldn't see.

        when:
        executer
            .withArgument("-Porg.gradle.java.installations.paths=${AvailableJavaHomes.getAvailableJvms().collect { it.javaHome.absolutePath }.join(",")}")
            .withToolchainDetectionEnabled()
        buildFile << """
            plugins {
                id("java")
            }
            ${javaPluginToolchainVersion(11)}
            ${mavenCentralRepository()}
            configurations { first {}; last {} }
            dependencies {
                // guarantee ordering
                first 'com.google.guava:guava:15.0'
                last 'com.google.collections:google-collections:1.0'
                implementation configurations.first + configurations.last

                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        and:
        file("src/test/java/TestCase.java") << """
            ${testFrameworkImports}
            public class TestCase {
                @Test
                public void test() throws Exception {
                    getClass().getClassLoader().loadClass("com.google.common.collect.ImmutableCollection\$EmptyImmutableCollection");
                }
            }
        """.stripIndent()

        then:
        fails "test"

        and:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("TestCase").with {
            assertTestFailed("test", CoreMatchers.containsString("java.lang.VerifyError"))
            assertTestFailed("test", CoreMatchers.containsString("\$EmptyImmutableCollection"))
        }
    }

    def "tests are re-executed when set of candidate classes change"() {
        given:
        buildFile << """
            apply plugin:'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """.stripIndent()

        and:
        file("src/test/java/FirstTest.java") << """
            ${testFrameworkImports}
            public class FirstTest {
                @Test public void test() {}
            }
        """.stripIndent()

        file("src/test/java/SecondTest.java") << """
            ${testFrameworkImports}
            public class SecondTest {
                @Test public void test() {}
            }
        """.stripIndent()

        when:
        run "test"
        then:
        executedAndNotSkipped ":test"
        output.contains("FirstTest > ${maybeParentheses('test')} PASSED")
        output.contains("SecondTest > ${maybeParentheses('test')} PASSED")

        when:
        run "test"
        then:
        skipped ":test"

        when:
        buildFile << """
        test {
            filter {
                includeTestsMatching "First*"
            }
        }
        """
        then:
        run "test"
        then:
        executedAndNotSkipped ":test"
        output.contains("FirstTest > ${maybeParentheses('test')} PASSED")
        !output.contains("SecondTest > ${maybeParentheses('test')} PASSED")
    }

    @Issue("https://github.com/gradle/gradle/issues/5305")
    def "test can install an irreplaceable SecurityManager"() {
        given:
        executer
            .withStackTraceChecksDisabled()
            .withToolchainDetectionEnabled()
        withInstallations(AvailableJavaHomes.getAvailableJvms())
        buildFile << """
            plugins {
                id("java")
            }
            ${javaPluginToolchainVersion(11)}
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        and:
        file('src/test/java/SecurityManagerInstallationTest.java') << """
            ${testFrameworkImports}
            import java.security.Permission;

            public class SecurityManagerInstallationTest {
                @Test
                public void testSecurityManagerCleanExit() {
                    System.setSecurityManager(new SecurityManager() {
                        @Override
                        public void checkPermission(Permission perm) {
                            if ("setSecurityManager".equals(perm.getName())) {
                                throw new SecurityException("You cannot replace this security manager!");
                            }
                        }
                    });
                }
            }
        """.stripIndent()

        when:
        succeeds "test"

        then:
        outputContains "Unable to reset SecurityManager"
    }

    @IgnoreIf({ GradleContextualExecuter.embedded })
    @Requires(TestPrecondition.JDK14_OR_LATER)
    def "useful NPE messages are transported to the daemon"() {
        buildFile << """
            apply plugin:'java-library'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }

            test {
                ${configureTestFramework}
                jvmArgs("-XX:+ShowCodeDetailsInExceptionMessages")
            }
        """.stripIndent()

        file('src/test/java/UsefulNPETest.java') << """
            ${testFrameworkImports}

            public class UsefulNPETest {
                @Test
                public void testUsefulNPE() {
                    Object o = null;
                    o.toString();
                }

                @Test
                public void testDeepUsefulNPE() {
                    other(null);
                }

                @Test
                public void testFailingGetMessage() {
                    throw new NullPointerException() {
                        public String getMessage() {
                            throw new RuntimeException();
                        }
                    };
                }

                void other(Object param) {
                    try {
                       System.out.println(param.toString());
                    } catch (NullPointerException ex) {
                        throw new RuntimeException(ex);
                    }
                }

            }
        """.stripIndent()

        when:
        fails 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("UsefulNPETest")
            .testFailed("testUsefulNPE", equalTo('java.lang.NullPointerException: Cannot invoke "Object.toString()" because "o" is null'))
        result.testClass("UsefulNPETest")
            .testFailed("testDeepUsefulNPE", equalTo('java.lang.RuntimeException: java.lang.NullPointerException: Cannot invoke "Object.toString()" because "param" is null'))
        result.testClass("UsefulNPETest")
            .testFailed("testFailingGetMessage", equalTo('Could not determine failure message for exception of type UsefulNPETest$1: java.lang.RuntimeException'))
    }
}
