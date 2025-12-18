/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.junit

import org.gradle.api.tasks.testing.TestResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.gradle.util.internal.VersionNumber
import org.hamcrest.Matcher
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith

abstract class AbstractJUnitTestFailureIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    abstract void writeBrokenRunnerOrExtension(String className)
    abstract void writeClassUsingBrokenRunnerOrExtension(String className, String runnerOrExtensionName)
    abstract String getInitializationErrorTestPath()
    abstract String getAssertionFailureClassName()
    abstract String getBeforeClassErrorTestName()
    abstract String getAfterClassErrorTestName()
    abstract Matcher<? super String>[] getBrokenBeforeAndAfterMatchers()
    abstract boolean hasStableInitializationErrors()

    def "reports and breaks build when tests fail"() {
        given:
        file('src/test/java/org/gradle/BrokenAfter.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class BrokenAfter {
                ${afterTestAnnotation}
                public void broken() {
                    fail("failed");
                }

                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/BrokenAfterClass.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class BrokenAfterClass {
                ${afterClassAnnotation}
                public static void broken() {
                    fail("failed");
                }

                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/BrokenBefore.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class BrokenBefore {
                ${beforeTestAnnotation}
                public void broken() {
                    fail("failed");
                }

                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/BrokenBeforeClass.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class BrokenBeforeClass {
                ${beforeClassAnnotation}
                public static void broken() {
                    fail("failed");
                }

                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/BrokenBeforeAndAfter.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class BrokenBeforeAndAfter {
                ${beforeTestAnnotation}
                public void brokenBefore() {
                    fail("before failed");
                }

                ${afterTestAnnotation}
                public void brokenAfter() {
                    fail("after failed");
                }

                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/BrokenConstructor.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class BrokenConstructor {
                public BrokenConstructor() {
                    fail("failed");
                }

                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/BrokenException.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class BrokenException {
                @Test
                public void broken() {
                    // Exception's toString() throws an exception
                    throw new BrokenRuntimeException();
                }

                private static class BrokenRuntimeException extends RuntimeException {
                    @Override
                    public String toString() {
                        throw new UnsupportedOperationException();
                    }
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/BrokenTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class BrokenTest {
                @Test
                public void failure() {
                    fail("failed");
                }

                @Test
                public void broken() {
                    throw new IllegalStateException("html: <> cdata: ]]>");
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/CustomException.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class CustomException {
                @Test
                public void custom() {
                    throw new CustomException.CustomRuntimeException();
                }

                private static class CustomRuntimeException extends RuntimeException {
                    @Override
                    public String toString() {
                        return "Exception with a custom toString implementation";
                    }
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/Unloadable.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class Unloadable {
                static {
                    fail("failed");
                }

                @Test
                public void ok() {
                }

                @Test
                public void ok2() {
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/UnserializableException.java') << """
            package org.gradle;

            ${testFrameworkImports}

            import java.io.IOException;
            import java.io.ObjectOutputStream;

            public class UnserializableException {

                @Test
                public void unserialized() {
                    throw new UnserializableRuntimeException("whatever", null);
                }

                static class UnserializableRuntimeException extends RuntimeException {
                    UnserializableRuntimeException(String message, Throwable cause) {
                        super(message, cause);
                    }

                    private void writeObject(ObjectOutputStream outstr) throws IOException {
                        outstr.writeObject(new Object());
                    }
                }
            }
        """.stripIndent()
        writeBrokenRunnerOrExtension('BrokenRunnerOrExtension')
        writeClassUsingBrokenRunnerOrExtension('ClassWithBrokenRunnerOrExtension', 'BrokenRunnerOrExtension')
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        when:
        def failure = executer.withTasks('build').runWithFailure()

        then:
        if (VersionNumber.parse(version) > VersionNumber.parse("4.4")) {
            failure.assertTestsFailed("registered by plugin 'org.gradle.jvm-test-suite'")
            def results = resultsFor(testDirectory)
            results.assertAtLeastTestPathsExecuted(
                'org.gradle.ClassWithBrokenRunnerOrExtension',
                'org.gradle.CustomException',
                'org.gradle.BrokenTest',
                'org.gradle.BrokenBefore',
                'org.gradle.BrokenAfter',
                'org.gradle.BrokenBeforeClass',
                'org.gradle.BrokenAfterClass',
                'org.gradle.BrokenBeforeAndAfter',
                'org.gradle.BrokenConstructor',
                'org.gradle.BrokenException',
                'org.gradle.Unloadable',
                'org.gradle.UnserializableException')

            results.testPathPreNormalized(initializationErrorTestPath).onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(startsWith('java.lang.UnsupportedOperationException: broken'))
            results.testPath('org.gradle.BrokenTest').onlyRoot()
                .assertChildCount(2, 2)
            results.testPath('org.gradle.BrokenTest', 'failure').onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(containsString(failureAssertionError('failed')))
            results.testPath('org.gradle.BrokenTest', 'broken').onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(containsString('java.lang.IllegalStateException: html: <> cdata: ]]>'))
            results.testPathPreNormalized(":org.gradle.BrokenBeforeClass:$beforeClassErrorTestName").onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(containsString(failureAssertionError('failed')))
            results.testPathPreNormalized(":org.gradle.BrokenAfterClass:$afterClassErrorTestName").onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(containsString(failureAssertionError('failed')))
            results.testPath('org.gradle.BrokenBefore', 'ok').onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(containsString(failureAssertionError('failed')))
            results.testPath('org.gradle.BrokenAfter', 'ok').onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(containsString(failureAssertionError('failed')))
            results.testPath('org.gradle.BrokenBeforeAndAfter', 'ok').onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
            brokenBeforeAndAfterMatchers.each { matcher ->
                results.testPath('org.gradle.BrokenBeforeAndAfter', 'ok').onlyRoot().assertFailureMessages(matcher)
            }
            results.testPath('org.gradle.BrokenException', 'broken').onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(startsWith('org.gradle.api.GradleException: Could not determine failure stacktrace for exception of type org.gradle.BrokenException$BrokenRuntimeException: java.lang.UnsupportedOperationException'))
            results.testPath('org.gradle.CustomException', 'custom').onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(startsWith('Exception with a custom toString implementation'))
            results.testPath('org.gradle.UnserializableException', 'unserialized').onlyRoot()
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(startsWith('org.gradle.UnserializableException$UnserializableRuntimeException: whatever'))
            if (hasStableInitializationErrors()) {
                results.testPath('org.gradle.Unloadable', 'ok').onlyRoot()
                    .assertHasResult(TestResult.ResultType.FAILURE)
                    .assertFailureMessages(startsWith(failureAssertionError('failed')))
                results.testPath('org.gradle.Unloadable', 'ok2').onlyRoot()
                    .assertHasResult(TestResult.ResultType.FAILURE)
                    .assertFailureMessages(startsWith('java.lang.NoClassDefFoundError'))
                results.testPath('org.gradle.BrokenConstructor', 'ok').onlyRoot()
                    .assertHasResult(TestResult.ResultType.FAILURE)
                    .assertFailureMessages(startsWith(failureAssertionError('failed')))
            }
        } else {
            // In JUnit 4.0 to 4.4, a test class with an initialization error results in a test process failure; not a test execution failure,
            // so we cannot assert on test results. From 4.5 onwards, we get proper test execution failures.
            failure.assertHasDescription("Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').")
            failure.assertThatCause(startsWith("Could not execute test class 'org.gradle.Unloadable'."))
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/23602")
    def "handles unserializable exception thrown from test"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """

        file('src/test/java/PoisonTest.java') << """
            ${testFrameworkImports}

            public class PoisonTest {
                @Test public void passingTest() { }

                @Test public void testWithUnserializableException() {
                    if (true) {
                        throw new UnserializableException();
                    }
                }

                @Test public void normalFailingTest() {
                    assert false;
                }

                private static class WriteReplacer implements java.io.Serializable {
                    private Object readResolve() {
                        return new RuntimeException();
                    }
                }

                private static class UnserializableException extends RuntimeException {
                    private Object writeReplace() {
                        return new WriteReplacer();
                    }
                }
            }
        """

        when:
        fails("test")

        then:
        def results = resultsFor(testDirectory)
        results.testPath("PoisonTest", "passingTest").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
        results.testPath("PoisonTest", "testWithUnserializableException").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString("TestFailureSerializationException: An exception of type PoisonTest\$UnserializableException was thrown by the test, but Gradle was unable to recreate the exception in the build process"))
        results.testPath("PoisonTest", "normalFailingTest").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString("AssertionError"))
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
        def results = resultsFor(file("."))
        results.testPath("ExceptionTest", "testThrow").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString('ExceptionTest$BadlyBehavedException: Broken writeObject()'))
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
        def results = resultsFor(file("."))
        results.testPath("ExceptionTest", "testThrow").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString('ExceptionTest$BadlyBehavedException: Broken readObject()'))
    }

    @Requires([UnitTestPreconditions.Jdk14OrLater, IntegTestPreconditions.NotEmbeddedExecutor])
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
        def result = resultsFor()
        result.testPath(":UsefulNPETest:testUsefulNPE").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(startsWith('java.lang.NullPointerException: Cannot invoke "Object.toString()" because "o" is null'))
        result.testPath(":UsefulNPETest:testDeepUsefulNPE").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(startsWith('java.lang.RuntimeException: java.lang.NullPointerException: Cannot invoke "Object.toString()" because "param" is null'))
        result.testPath(":UsefulNPETest:testFailingGetMessage").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(startsWith('org.gradle.api.GradleException: Could not determine failure stacktrace for exception of type UsefulNPETest$1: java.lang.RuntimeException'))
    }

    String failureAssertionError(String message) {
        return "${assertionFailureClassName}: ${message}".toString()
    }
}
