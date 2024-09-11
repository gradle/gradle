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

package org.gradle.integtests.tooling.r70

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.tooling.TestLauncher
import spock.lang.Timeout

@Timeout(120)
@TargetGradleVersion(">=7.0")
/**
 * @see org.gradle.integtests.tooling.r88.TestDisplayNameJUnit4CrossVersionSpec and
 * @see org.gradle.integtests.tooling.r88.TestDisplayNameSpockCrossVersionSpec
 */
class TestDisplayNameJUnit5CrossVersionSpec extends TestLauncherSpec {
    @Override
    void addDefaultTests() {
    }

    @Override
    String simpleJavaProject() {
        """
        allprojects{
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
            }

            test {
                useJUnitPlatform()
            }
        }
        """
    }

    @TargetGradleVersion(">=8.8")
    def "reports display names of class and method"() {
        file("src/test/java/org/example/SimpleTests.java") << """package org.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a class display name")
public class SimpleTests {

    @Test
    @DisplayName("and a test display name")
    void test() {
    }
}
"""
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':test', ['org.example.SimpleTests'])
        }

        then:
        jvmTestEvents {
            task(":test") {
                suite("Gradle Test Run :test") {
                    suite("Gradle Test Executor") {
                        testClass("org.example.SimpleTests") {
                            operationDisplayName "a class display name"
                            testDisplayName "a class display name"
                            test("test()") {
                                operationDisplayName "and a test display name"
                                testDisplayName "and a test display name"
                            }
                        }
                    }
                }
            }
        }
    }

    @TargetGradleVersion(">=8.8")
    def "reports display names of nested test classes"() {
        file("src/test/java/org/example/TestingAStackDemo.java") << """package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EmptyStackException;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("A stack")
class TestingAStackDemo {

    Stack<Object> stack;

    @Test
    @DisplayName("is instantiated with new Stack()")
    void isInstantiatedWithNew() {
        new Stack<>();
    }

    @Nested
    @DisplayName("when new")
    class WhenNew {

        @BeforeEach
        void createNewStack() {
            stack = new Stack<>();
        }

        @Test
        @DisplayName("is empty")
        void isEmpty() {
            assertTrue(stack.isEmpty());
        }

        @Test
        @DisplayName("throws EmptyStackException when popped")
        void throwsExceptionWhenPopped() {
            assertThrows(EmptyStackException.class, stack::pop);
        }

        @Test
        @DisplayName("throws EmptyStackException when peeked")
        void throwsExceptionWhenPeeked() {
            assertThrows(EmptyStackException.class, stack::peek);
        }

        @Nested
        @DisplayName("after pushing an element")
        class AfterPushing {

            String anElement = "an element";

            @BeforeEach
            void pushAnElement() {
                stack.push(anElement);
            }

            @Test
            @DisplayName("it is no longer empty")
            void isNotEmpty() throws InterruptedException {
                Thread.sleep(5_000);
                assertFalse(stack.isEmpty());
            }

            @Test
            @DisplayName("returns the element when popped and is empty")
            void returnElementWhenPopped() {
                assertEquals(anElement, stack.pop());
                assertTrue(stack.isEmpty());
            }

            @Test
            @DisplayName("returns the element when peeked but remains not empty")
            void returnElementWhenPeeked() {
                assertEquals(anElement, stack.peek());
                assertFalse(stack.isEmpty());
            }
        }
    }
}
"""

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':test', ['org.example.TestingAStackDemo*'])
        }

        then:
        jvmTestEvents {
            task(":test") {
                suite("Gradle Test Run :test") {
                    suite("Gradle Test Executor") {
                        testClass("org.example.TestingAStackDemo") {
                            operationDisplayName "A stack"
                            testDisplayName "A stack"
                            test("isInstantiatedWithNew()") {
                                operationDisplayName "is instantiated with new Stack()"
                                testDisplayName "is instantiated with new Stack()"
                            }
                            testClass("org.example.TestingAStackDemo\$WhenNew") {
                                operationDisplayName "when new"
                                testDisplayName "when new"
                                test("isEmpty()") {
                                    operationDisplayName "is empty"
                                    testDisplayName "is empty"
                                }
                                test("throwsExceptionWhenPeeked()") {
                                    operationDisplayName "throws EmptyStackException when peeked"
                                    testDisplayName "throws EmptyStackException when peeked"
                                }
                                test("throwsExceptionWhenPopped()") {
                                    operationDisplayName "throws EmptyStackException when popped"
                                    testDisplayName "throws EmptyStackException when popped"
                                }
                                testClass("org.example.TestingAStackDemo\$WhenNew\$AfterPushing") {
                                    operationDisplayName "after pushing an element"
                                    testDisplayName "after pushing an element"
                                    test("isNotEmpty()") {
                                        operationDisplayName "it is no longer empty"
                                        testDisplayName "it is no longer empty"
                                    }
                                    test("returnElementWhenPeeked()") {
                                        operationDisplayName "returns the element when peeked but remains not empty"
                                        testDisplayName "returns the element when peeked but remains not empty"
                                    }
                                    test("returnElementWhenPopped()") {
                                        operationDisplayName "returns the element when popped and is empty"
                                        testDisplayName "returns the element when popped and is empty"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @TargetGradleVersion(">=8.8")
    def "reports display names of parameterized tests"() {
        file("src/test/java/org/example/ParameterizedTests.java") << """package org.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Parameterized test")
public class ParameterizedTests {

    @ParameterizedTest
    @DisplayName("1st test")
    @ValueSource(strings = {"foo", "bar"})
    void test1(String param) {
        assertEquals(3, param.length());
    }

    @ParameterizedTest(name = "{index} ==> the test for ''{0}''")
    @DisplayName("2nd test")
    @ValueSource(strings = {"foo", "bar"})
    void test2(String param) {
        assertEquals(3, param.length());
    }
}
"""

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':test', ['org.example.ParameterizedTest*'])
        }

        then:
        jvmTestEvents {
            task(":test") {
                suite("Gradle Test Run :test") {
                    suite("Gradle Test Executor") {
                        testClass("org.example.ParameterizedTests") {
                            operationDisplayName "Parameterized test"
                            testDisplayName "Parameterized test"
                            testMethodSuite("test1(String)") {
                                operationDisplayName "1st test"
                                testDisplayName "1st test"
                                test("test1(String)[1]") {
                                    operationDisplayName "[1] foo"
                                    testDisplayName "[1] foo"
                                }
                                test("test1(String)[2]") {
                                    operationDisplayName "[2] bar"
                                    testDisplayName "[2] bar"
                                }
                            }
                            testMethodSuite("test2(String)") {
                                operationDisplayName "2nd test"
                                testDisplayName "2nd test"
                                test("test2(String)[1]") {
                                    operationDisplayName "1 ==> the test for 'foo'"
                                    testDisplayName "1 ==> the test for 'foo'"
                                }
                                test("test2(String)[2]") {
                                    operationDisplayName "2 ==> the test for 'bar'"
                                    testDisplayName "2 ==> the test for 'bar'"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @TargetGradleVersion(">=8.8")
    def "reports display names for dynamic tests"() {
        file("src/test/java/org/example/DynamicTests.java") << """package org.example;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import org.junit.jupiter.api.DisplayName;

public class DynamicTests {

    @TestFactory
    Stream<DynamicNode> testFactory() {
        return Stream.of(dynamicContainer("some container", Stream.of(
                dynamicContainer("some nested container", Stream.of(
                        dynamicTest("foo", () -> {
                            assertTrue(true);
                        }),
                        dynamicTest("bar", () -> {
                            assertTrue(true);
                        })
                ))
        )));
    }

    @TestFactory
    @DisplayName("another test factory")
    Stream<DynamicNode> anotherTestFactory() {
        return Stream.of(dynamicTest("foo", () -> {
            assertTrue(true);
        }));
    }
}
"""

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':test', ['org.example.DynamicTests*'])
        }

        then:
        jvmTestEvents {
            task(":test") {
                suite("Gradle Test Run :test") {
                    suite("Gradle Test Executor") {
                        testClass("org.example.DynamicTests") {
                            testDisplayName "DynamicTests"
                            testMethodSuite("testFactory()") {
                                operationDisplayName "testFactory()"
                                testDisplayName "testFactory()"
                                testMethodSuite("testFactory()[1]") {
                                    operationDisplayName "some container"
                                    testDisplayName "some container"
                                    testMethodSuite("testFactory()[1][1]") {
                                        operationDisplayName "some nested container"
                                        testDisplayName "some nested container"
                                        test("testFactory()[1][1][1]") {
                                            operationDisplayName "foo"
                                            testDisplayName "foo"
                                        }
                                        test("testFactory()[1][1][2]") {
                                            operationDisplayName "bar"
                                            testDisplayName "bar"
                                        }
                                    }
                                }
                            }
                            testMethodSuite("anotherTestFactory()") {
                                operationDisplayName "another test factory"
                                testDisplayName "another test factory"
                                test("anotherTestFactory()[1]") {
                                    operationDisplayName "foo"
                                    testDisplayName "foo"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @TargetGradleVersion(">=8.8")
    def "reports transformed display names with DisplayNameGeneration"() {
        file("src/test/java/org/example/ComplexTests.java") << """package org.example;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("some_name for_tests")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ComplexTests {

    @Test
    public void test() {
    }

    @Test
    public void simple_test() {
    }

    @Test
    @DisplayName("pretty pretty_test")
    public void ugly_test() {
    }

    @ParameterizedTest
    @CsvSource({"10, 'first'", "20, 'second'"})
    public void parametrized_test(int value, String name) {
        System.out.println(name + " " + value);
    }

    @ParameterizedTest
    @DisplayName("pretty parametrized test")
    @CsvSource({"30, 'third'", "40, 'fourth'"})
    public void ugly_parametrized_test(int value, String name) {
        System.out.println(name + " " + value);
    }
}
"""

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':test', ['org.example.ComplexTests*'])
        }

        then:
        jvmTestEvents {
            task(":test") {
                suite("Gradle Test Run :test") {
                    suite("Gradle Test Executor") {
                        testClass("org.example.ComplexTests") {
                            operationDisplayName "some_name for_tests"
                            testDisplayName "some_name for_tests"

                            test("test()") {
                                operationDisplayName "test"
                                testDisplayName "test"
                            }
                            test("simple_test()") {
                                operationDisplayName "simple test"
                                testDisplayName "simple test"
                            }
                            test("ugly_test()") {
                                operationDisplayName "pretty pretty_test"
                                testDisplayName "pretty pretty_test"
                            }
                            testMethodSuite("parametrized_test(int, String)") {
                                operationDisplayName "parametrized test (int, String)"
                                testDisplayName "parametrized test (int, String)"
                                test("parametrized_test(int, String)[1]") {
                                    operationDisplayName "[1] 10, first"
                                    testDisplayName "[1] 10, first"
                                }
                                test("parametrized_test(int, String)[2]") {
                                    operationDisplayName "[2] 20, second"
                                    testDisplayName "[2] 20, second"
                                }
                            }
                            testMethodSuite("ugly_parametrized_test(int, String)") {
                                operationDisplayName "pretty parametrized test"
                                testDisplayName "pretty parametrized test"
                                test("ugly_parametrized_test(int, String)[1]") {
                                    operationDisplayName "[1] 30, third"
                                    testDisplayName "[1] 30, third"
                                }
                                test("ugly_parametrized_test(int, String)[2]") {
                                    operationDisplayName "[2] 40, fourth"
                                    testDisplayName "[2] 40, fourth"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
