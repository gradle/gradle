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
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.hamcrest.CoreMatchers
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE_JUPITER

/**
 * General tests for the JVM testing infrastructure that don't deserve their own test class.
 */
@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE_JUPITER })
class TestingIntegrationTest extends JUnitMultiVersionIntegrationSpec {

    @Issue("https://issues.gradle.org/browse/GRADLE-1948")
    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "test interrupting its own thread does not kill test execution"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testCompile "junit:junit:4.12" }
        """

        and:
        file("src/test/java/SomeTest.java") << """
            import org.junit.*;

            public class SomeTest {
                @Test public void foo() {
                    Thread.currentThread().interrupt();
                }
            }
        """

        when:
        run "test"

        then:
        ":test" in nonSkippedTasks
    }

    def "fails cleanly even if an exception is thrown that doesn't serialize cleanly"() {
        given:
        file('src/test/java/ExceptionTest.java') << """
            import org.junit.*;
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
        """
        file('build.gradle') << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testCompile 'junit:junit:4.12' }
        """

        when:
        runAndFail "test"

        then:
        failureHasCause "There were failing tests"

        and:
        def results = new DefaultTestExecutionResult(file("."))
        results.assertTestClassesExecuted("ExceptionTest")
        results.testClass("ExceptionTest").assertTestFailed("testThrow", CoreMatchers.equalTo('ExceptionTest$BadlyBehavedException: Broken writeObject()'))
    }

    def "fails cleanly even if an exception is thrown that doesn't de-serialize cleanly"() {
        given:

        file('src/test/java/ExceptionTest.java') << """
            import org.junit.*;
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
        """
        file('build.gradle') << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """

        when:
        // an exception was thrown so we should fail here
        runAndFail "test"

        then:
        failureHasCause "There were failing tests"

        and:
        def results = new DefaultTestExecutionResult(file("."))
        results.assertTestClassesExecuted("ExceptionTest")
        results.testClass("ExceptionTest").assertTestFailed("testThrow", CoreMatchers.equalTo('ExceptionTest$BadlyBehavedException: Broken readObject()'))
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
            dependencies { testCompile "junit:junit:4.12" }
            test.workingDir = "${testWorkingDir.toURI()}"
        """

        and:
        file("src/test/java/SomeTest.java") << """
            import org.junit.*;

            public class SomeTest {
                @Test public void foo() {
                    System.out.println(new java.io.File(".").getAbsolutePath());
                }
            }
        """

        expect:
        succeeds "test"
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2313")
    @Unroll
    "can clean test after extracting class file with #framework"() {
        when:
        ignoreWhenJUnitPlatform()
        buildFile << """
            apply plugin: "java"
            ${mavenCentralRepository()}
            dependencies { testCompile "$dependency" }
            test { $framework() }
        """
        and:
        file("src/test/java/SomeTest.java") << """
            public class SomeTest extends $superClass {
            }
        """
        then:
        succeeds "clean", "test"

        and:
        file("build/tmp/test").exists() // ensure we extracted classes

        where:
        framework   | dependency                | superClass
        "useJUnit"  | "junit:junit:4.12"        | "org.junit.runner.Result"
        "useTestNG" | "org.testng:testng:6.3.1" | "org.testng.Converter"
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2527")
    def "test class detection works for custom test tasks"() {
        given:
        ignoreWhenJupiter()
        buildFile << """
                apply plugin:'java'
                ${mavenCentralRepository()}

                sourceSets{
	                othertests{
		                java.srcDir file('src/othertests/java')
	                    resources.srcDir file('src/othertests/resources')
	                }
                }

                dependencies{
	                othertestsCompile "junit:junit:4.12"
                }

                task othertestsTest(type:Test){
	                useJUnit()
	                classpath = sourceSets.othertests.runtimeClasspath
	                testClassesDirs = sourceSets.othertests.output.classesDirs
	            }
            """

        and:
        file("src/othertests/java/AbstractTestClass.java") << """
                import junit.framework.TestCase;
                public abstract class AbstractTestClass extends TestCase {
                }
            """

        file("src/othertests/java/TestCaseExtendsAbstractClass.java") << """
                import junit.framework.Assert;
                public class TestCaseExtendsAbstractClass extends AbstractTestClass{
                    public void testTrue() {
                        Assert.assertTrue(true);
                    }
                }
            """

        when:
        run "othertestsTest"
        then:
        def result = new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'othertestsTest')
        result.assertTestClassesExecuted("TestCaseExtendsAbstractClass")
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
        buildScript """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            configurations { first {}; last {} }
            dependencies {
                // guarantee ordering
                first 'com.google.guava:guava:15.0'
                last 'com.google.collections:google-collections:1.0'
                compile configurations.first + configurations.last

                testCompile 'junit:junit:4.12'
            }
        """

        and:
        file("src/test/java/TestCase.java") << """
            import org.junit.Test;
            public class TestCase {
                @Test
                public void test() throws Exception {
                    getClass().getClassLoader().loadClass("com.google.common.collect.ImmutableCollection\$EmptyImmutableCollection");
                }
            }
        """

        then:
        fails "test"

        and:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("TestCase").with {
            assertTestFailed("test", CoreMatchers.containsString("java.lang.VerifyError"))
            assertTestFailed("test", CoreMatchers.containsString("\$EmptyImmutableCollection"))
        }
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3157")
    @Requires(TestPrecondition.JDK8_OR_LATER)
    def "test class detection works when '-parameters' compiler option is used (JEP 118)"() {
        when:
        buildScript """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testCompile 'junit:junit:4.12'
            }
            tasks.withType(JavaCompile) {
                options.with {
                    compilerArgs << '-parameters'
                }
            }
        """

        and:
        file("src/test/java/TestHelper.java") << """
            public class TestHelper {
                public void helperMethod(String foo, int bar) {
                    // this method shouldn't cause failure due to API version check
                    // in org.objectweb.asm.MethodVisitor#visitParameter
                }
            }
        """

        and:
        file("src/test/java/TestCase.java") << """
            import org.junit.Test;
            import static org.junit.Assert.assertTrue;
            public class TestCase {
                @Test
                public void test() {
                    assertTrue(Double.valueOf(System.getProperty("java.specification.version")) >= 1.8);
                }
            }
        """

        then:
        run "test"

        and:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("TestCase").with {
            assertTestCount(1, 0, 0)
        }
    }

    def "tests are re-executed when set of candidate classes change"() {
        given:
        buildFile << """
            apply plugin:'java'
            ${mavenCentralRepository()}
            dependencies {
                testCompile 'junit:junit:4.12'
            }
            test {
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """

        and:
        file("src/test/java/FirstTest.java") << """
            import org.junit.*;
            public class FirstTest {
                @Test public void test() {}
            }
        """

        file("src/test/java/SecondTest.java") << """
            import org.junit.*;
            public class SecondTest {
                @Test public void test() {}
            }
        """

        when:
        run "test"
        then:
        nonSkippedTasks.contains ":test"
        output.contains("FirstTest > test PASSED")
        output.contains("SecondTest > test PASSED")

        when:
        run "test"
        then:
        skippedTasks.contains ":test"

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
        nonSkippedTasks.contains ":test"
        output.contains("FirstTest > test PASSED")
        !output.contains("SecondTest > test PASSED")
    }

    @Issue("https://github.com/gradle/gradle/issues/2661")
    def "test logging can be configured on turkish locale"() {
        given:
        buildFile << """
            apply plugin:'java'
            test {
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """
        when:
        executer
            .requireDaemon()
            .requireIsolatedDaemons()
            .withBuildJvmOpts("-Duser.language=tr", "-Duser.country=TR")
            .withTasks("help")
            .run()

        then:
        noExceptionThrown()
    }

    @Issue("https://github.com/gradle/gradle/issues/5305")
    def "test can install an irreplaceable SecurityManager"() {
        given:
        executer.withStackTraceChecksDisabled()
        buildFile << """
            apply plugin:'java'
            ${mavenCentralRepository()}
            dependencies { testCompile 'junit:junit:4.12' }
        """

        and:
        file('src/test/java/SecurityManagerInstallationTest.java') << """
            import org.junit.Test;
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
        """

        when:
        succeeds "test"

        then:
        outputContains "Unable to reset SecurityManager"
    }
}
