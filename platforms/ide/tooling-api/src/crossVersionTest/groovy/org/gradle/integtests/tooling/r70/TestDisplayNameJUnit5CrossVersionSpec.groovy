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
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.TestLauncher
import spock.lang.Timeout

@Timeout(120)
@ToolingApiVersion('>=6.1')
@TargetGradleVersion(">=7.0")
/**
 * @see org.gradle.integtests.tooling.r87.TestDisplayNameJUnit4CrossVersionSpec and
 * @see org.gradle.integtests.tooling.r87.TestDisplayNameSpockCrossVersionSpec
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
                            displayName "a class display name"
                            test("test()") {
                                displayName "and a test display name"
                            }
                        }
                    }
                }
            }
        }
    }

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
                            displayName "A stack"
                            test("isInstantiatedWithNew()") {
                                displayName "is instantiated with new Stack()"
                            }
                            testClass("org.example.TestingAStackDemo\$WhenNew") {
                                displayName "when new"
                                test("isEmpty()") {
                                    displayName "is empty"
                                }
                                test("throwsExceptionWhenPeeked()") {
                                    displayName "throws EmptyStackException when peeked"
                                }
                                test("throwsExceptionWhenPopped()") {
                                    displayName "throws EmptyStackException when popped"
                                }
                                testClass("org.example.TestingAStackDemo\$WhenNew\$AfterPushing") {
                                    test("isNotEmpty()") {
                                        displayName "it is no longer empty"
                                    }
                                    test("returnElementWhenPeeked()") {
                                        displayName "returns the element when peeked but remains not empty"
                                    }
                                    test("returnElementWhenPopped()") {
                                        displayName "returns the element when popped and is empty"
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
                            displayName "Parameterized test"
                            testMethodSuite("test1(String)") {
                                displayName "1st test"
                                generatedTest("test1(String)[1]") {
                                    displayName "[1] foo"
                                }
                                generatedTest("test1(String)[2]") {
                                    displayName "[2] bar"
                                }
                            }
                            testMethodSuite("test2(String)") {
                                displayName "2nd test"
                                generatedTest("test2(String)[1]") {
                                    displayName "1 ==> the test for 'foo'"
                                }
                                generatedTest("test2(String)[2]") {
                                    displayName "2 ==> the test for 'bar'"
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
                            displayName "DynamicTests"
                            testMethodSuite("testFactory()") {
                                displayName "testFactory()"
                                testMethodSuite("testFactory()[1]") {
                                    displayName "some container"
                                    testMethodSuite("testFactory()[1][1]") {
                                        displayName "some nested container"
                                        generatedTest("testFactory()[1][1][1]") {
                                            displayName "foo"
                                        }
                                        generatedTest("testFactory()[1][1][2]") {
                                            displayName "bar"
                                        }
                                    }
                                }
                            }
                            testMethodSuite("anotherTestFactory()") {
                                displayName "another test factory"
                                generatedTest("anotherTestFactory()[1]") {
                                    displayName "foo"
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
                            displayName "some_name for_tests"

                            test("test()") {
                                displayName "test"
                            }
                            test("simple_test()") {
                                displayName "simple test"
                            }
                            test("ugly_test()") {
                                displayName "pretty pretty_test"
                            }
                            testMethodSuite("parametrized_test(int, String)") {
                                displayName "parametrized test (int, String)"
                                generatedTest("parametrized_test(int, String)[1]") {
                                    displayName "[1] 10, first"
                                }
                                generatedTest("parametrized_test(int, String)[2]") {
                                    displayName "[2] 20, second"
                                }
                            }
                            testMethodSuite("ugly_parametrized_test(int, String)") {
                                displayName "pretty parametrized test"
                                generatedTest("ugly_parametrized_test(int, String)[1]") {
                                    displayName "[1] 30, third"
                                }
                                generatedTest("ugly_parametrized_test(int, String)[2]") {
                                    displayName "[2] 40, fourth"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
