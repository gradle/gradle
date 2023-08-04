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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.hamcrest.Matcher
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.startsWith

abstract class AbstractJUnitTestFailureIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    abstract void writeBrokenRunnerOrExtension(String className)
    abstract void writeClassUsingBrokenRunnerOrExtension(String className, String runnerOrExtensionName)
    abstract String getInitializationErrorTestName()
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
        executer.withTasks('build').runWithFailure().assertTestsFailed()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted(
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
        result.testClass('org.gradle.ClassWithBrokenRunnerOrExtension').assertTestFailed(initializationErrorTestName, equalTo('java.lang.UnsupportedOperationException: broken'))
        result.testClass('org.gradle.BrokenTest')
            .assertTestCount(2, 2, 0)
            .assertTestFailed('failure', equalTo(failureAssertionError('failed')))
            .assertTestFailed('broken', equalTo('java.lang.IllegalStateException: html: <> cdata: ]]>'))
        result.testClass('org.gradle.BrokenBeforeClass').assertTestFailed(beforeClassErrorTestName, equalTo(failureAssertionError('failed')))
        result.testClass('org.gradle.BrokenAfterClass').assertTestFailed(afterClassErrorTestName, equalTo(failureAssertionError('failed')))
        result.testClass('org.gradle.BrokenBefore').assertTestFailed('ok', equalTo(failureAssertionError('failed')))
        result.testClass('org.gradle.BrokenAfter').assertTestFailed('ok', equalTo(failureAssertionError('failed')))
        result.testClass('org.gradle.BrokenBeforeAndAfter').assertTestFailed('ok', brokenBeforeAndAfterMatchers)
        result.testClass('org.gradle.BrokenException').assertTestFailed('broken', startsWith('Could not determine failure message for exception of type org.gradle.BrokenException$BrokenRuntimeException: java.lang.UnsupportedOperationException'))
        result.testClass('org.gradle.CustomException').assertTestFailed('custom', startsWith('Exception with a custom toString implementation'))
        result.testClass('org.gradle.UnserializableException').assertTestFailed('unserialized', equalTo('org.gradle.UnserializableException$UnserializableRuntimeException: whatever'))
        if (hasStableInitializationErrors()) {
            result.testClass('org.gradle.Unloadable').assertTestFailed('ok', equalTo(failureAssertionError('failed')))
            result.testClass('org.gradle.Unloadable').assertTestFailed('ok2', startsWith('java.lang.NoClassDefFoundError'))
            result.testClass('org.gradle.BrokenConstructor').assertTestFailed('ok', equalTo(failureAssertionError('failed')))
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
        with(new DefaultTestExecutionResult(testDirectory).testClass("PoisonTest")) {
            assertTestPassed("passingTest")
            assertTestFailed("testWithUnserializableException", containsString("TestFailureSerializationException: An exception of type PoisonTest\$UnserializableException was thrown by the test, but Gradle was unable to recreate the exception in the build process"))
            assertTestFailed("normalFailingTest", containsString("AssertionError"))
        }
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
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("UsefulNPETest")
            .testFailed("testUsefulNPE", equalTo('java.lang.NullPointerException: Cannot invoke "Object.toString()" because "o" is null'))
        result.testClass("UsefulNPETest")
            .testFailed("testDeepUsefulNPE", equalTo('java.lang.RuntimeException: java.lang.NullPointerException: Cannot invoke "Object.toString()" because "param" is null'))
        result.testClass("UsefulNPETest")
            .testFailed("testFailingGetMessage", equalTo('Could not determine failure message for exception of type UsefulNPETest$1: java.lang.RuntimeException'))
    }

    String failureAssertionError(String message) {
        return "${assertionFailureClassName}: ${message}".toString()
    }
}
