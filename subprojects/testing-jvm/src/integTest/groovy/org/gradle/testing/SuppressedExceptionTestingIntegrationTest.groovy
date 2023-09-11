/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
class SuppressedExceptionTestingIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withRepositoryMirrors()
    }

    def "non-deserializable suppressed exceptions are preserved"() {
        buildFile << """
            apply plugin:'java-library'
            ${mavenCentralRepository()}
            testing.suites.test.useJUnitJupiter()
        """

        file('src/test/java/TestCaseWithThrowingBeforeAllAndAfterAllCallbacks.java') << """
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.extension.AfterAllCallback;
            import org.junit.jupiter.api.extension.BeforeAllCallback;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.api.extension.ExtensionContext;

            @ExtendWith(ThrowingBeforeAllCallback.class)
            @ExtendWith(ThrowingAfterAllCallback.class)
            public class TestCaseWithThrowingBeforeAllAndAfterAllCallbacks {

                @Test
                void test() {
                }

            }

            class ThrowingBeforeAllCallback implements BeforeAllCallback {
                @Override
                public void beforeAll(ExtensionContext context) {
                    throw new IllegalStateException("beforeAll callback");
                }
            }

            class ThrowingAfterAllCallback implements AfterAllCallback {
                @Override
                public void afterAll(ExtensionContext context) {
                    throw new CustomException("afterAll callback");
                }
            }

            class CustomException extends IllegalStateException {
                public CustomException(String message) { super(message); }
            }
        """

        when:
        fails 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("TestCaseWithThrowingBeforeAllAndAfterAllCallbacks")
            .testFailed("initializationError", equalTo('java.lang.IllegalStateException: beforeAll callback'))
        result.testClass("TestCaseWithThrowingBeforeAllAndAfterAllCallbacks")
            .testFailed("initializationError", containsString('Suppressed: CustomException: afterAll callback'))
    }

    def "suppressed exceptions are transported even for custom exception types"() {
        buildFile << """
            apply plugin:'java-library'
            ${mavenCentralRepository()}
            testing.suites.test.useJUnitJupiter()
        """

        file('src/test/java/SuppressedExceptionsAccidentallyThrownNotShownByGradleTest.java') << """
            import org.junit.jupiter.api.*;

            public class SuppressedExceptionsAccidentallyThrownNotShownByGradleTest {

                public static class CustomException extends RuntimeException {
                    public CustomException(String message) {
                        super(message);
                    }
                }

                private static String toUpper(String input) {
                    if (input.contains("aa")) {
                        RuntimeException exception = new RuntimeException("This is an exception with suppressed one.");
                        exception.addSuppressed(new RuntimeException("I am suppressed"));
                        throw exception;
                    }
                    if (input.contains("bb")) {
                        RuntimeException exception =
                                new CustomException("This is a CUSTOM exception with suppressed one");
                        exception.addSuppressed(new RuntimeException("I AM SUPPRESSED AND CUSTOM"));
                        throw exception;
                    }

                    return input.toUpperCase();
                }

                @Test
                void failingWithSuppressedExceptionTest() {
                    Assertions.assertEquals("AA", toUpper("aa"));
                }

                @Test
                void failingWithCustomSuppressedExceptionTest() {
                    Assertions.assertEquals("BB", toUpper("bb"));
                }
            }
        """

        when:
        fails 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("SuppressedExceptionsAccidentallyThrownNotShownByGradleTest")
            .testFailed("failingWithSuppressedExceptionTest", equalTo('java.lang.RuntimeException: This is an exception with suppressed one.'))
        result.testClass("SuppressedExceptionsAccidentallyThrownNotShownByGradleTest")
            .testFailed("failingWithSuppressedExceptionTest", containsString('I am suppressed'))
        result.testClass("SuppressedExceptionsAccidentallyThrownNotShownByGradleTest")
            .testFailed("failingWithCustomSuppressedExceptionTest", equalTo('SuppressedExceptionsAccidentallyThrownNotShownByGradleTest$CustomException: This is a CUSTOM exception with suppressed one'))
        result.testClass("SuppressedExceptionsAccidentallyThrownNotShownByGradleTest")
            .testFailed("failingWithCustomSuppressedExceptionTest", containsString('I AM SUPPRESSED AND CUSTOM'))
    }
}
