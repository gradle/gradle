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
import org.gradle.testing.fixture.AbstractJUnitMultiVersionIntegrationTest
import org.hamcrest.Matcher

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.startsWith

abstract class AbstractJUnitTestFailureIntegrationTest extends AbstractJUnitMultiVersionIntegrationTest {
    abstract void writeBrokenRunnerOrExtension(String className)
    abstract void writeClassUsingBrokenRunnerOrExtension(String className, String runnerOrExtensionName)
    abstract String getInitializationErrorTestName()
    abstract String getAssertionFailureClassName()
    abstract String getBeforeClassErrorTestName()
    abstract String getAfterClassErrorTestName()
    abstract Matcher<? super String>[] getBrokenBeforeAndAfterMatchers()

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
        result.testClass('org.gradle.BrokenConstructor').assertTestFailed('ok', equalTo(failureAssertionError('failed')))
        result.testClass('org.gradle.BrokenException').assertTestFailed('broken', startsWith('Could not determine failure message for exception of type org.gradle.BrokenException$BrokenRuntimeException: java.lang.UnsupportedOperationException'))
        result.testClass('org.gradle.CustomException').assertTestFailed('custom', startsWith('Exception with a custom toString implementation'))
        result.testClass('org.gradle.Unloadable').assertTestFailed('ok', equalTo(failureAssertionError('failed')))
        result.testClass('org.gradle.Unloadable').assertTestFailed('ok2', startsWith('java.lang.NoClassDefFoundError'))
        result.testClass('org.gradle.UnserializableException').assertTestFailed('unserialized', equalTo('org.gradle.UnserializableException$UnserializableRuntimeException: whatever'))
    }

    String failureAssertionError(String message) {
        return "${assertionFailureClassName}: ${message}".toString()
    }
}
