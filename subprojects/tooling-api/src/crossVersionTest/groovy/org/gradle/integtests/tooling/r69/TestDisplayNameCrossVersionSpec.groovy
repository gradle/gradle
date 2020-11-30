/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.tooling.r69

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.TestLauncher
import spock.lang.Timeout

@Timeout(120)
@ToolingApiVersion('>=6.1')
@TargetGradleVersion(">=6.9")
class TestDisplayNameCrossVersionSpec extends TestLauncherSpec {
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
                testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
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

        assertTaskExecuted(":test")
        assertTestExecuted(className: "org.example.SimpleTests", methodName: null, displayName: "a class display name")
        assertTestExecuted(className: "org.example.SimpleTests", methodName: "test()", displayName: "and a test display name")
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

        assertTaskExecuted(":test")
        assertTestExecuted(className: "org.example.TestingAStackDemo", methodName: null, displayName: "A stack")
        assertTestExecuted(className: "org.example.TestingAStackDemo", methodName: "isInstantiatedWithNew()", displayName: "is instantiated with new Stack()")
        assertTestExecuted(className: "org.example.TestingAStackDemo\$WhenNew", methodName: null, displayName: "when new")
        assertTestExecuted(className: "org.example.TestingAStackDemo\$WhenNew", methodName: "isEmpty()", displayName: "is empty")
        assertTestExecuted(className: "org.example.TestingAStackDemo\$WhenNew", methodName: "throwsExceptionWhenPopped()", displayName: "throws EmptyStackException when popped")
        assertTestExecuted(className: "org.example.TestingAStackDemo\$WhenNew", methodName: "throwsExceptionWhenPeeked()", displayName: "throws EmptyStackException when peeked")
        assertTestExecuted(className: "org.example.TestingAStackDemo\$WhenNew\$AfterPushing", methodName: null, displayName: "after pushing an element")
        assertTestExecuted(className: "org.example.TestingAStackDemo\$WhenNew\$AfterPushing", methodName: "isNotEmpty()", displayName: "it is no longer empty")
        assertTestExecuted(className: "org.example.TestingAStackDemo\$WhenNew\$AfterPushing", methodName: "returnElementWhenPopped()", displayName: "returns the element when popped and is empty")
        assertTestExecuted(className: "org.example.TestingAStackDemo\$WhenNew\$AfterPushing", methodName: "returnElementWhenPeeked()", displayName: "returns the element when peeked but remains not empty")
    }

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

    @ParameterizedTest
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
        assertTaskExecuted(":test")
        assertTestExecuted(className: "org.example.ParameterizedTests", methodName: null, displayName: "Parameterized test")
        assertTestExecuted(className: "org.example.ParameterizedTests", methodName: null, displayName: "1st test")
        assertTestExecuted(className: "org.example.ParameterizedTests", methodName: "test1(String)[1]", displayName: "[1] foo")
        assertTestExecuted(className: "org.example.ParameterizedTests", methodName: "test1(String)[2]", displayName: "[2] bar")
        assertTestExecuted(className: "org.example.ParameterizedTests", methodName: null, displayName: "2nd test")
        assertTestExecuted(className: "org.example.ParameterizedTests", methodName: "test2(String)[1]", displayName: "[1] foo")
        assertTestExecuted(className: "org.example.ParameterizedTests", methodName: "test2(String)[2]", displayName: "[2] bar")
    }

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
        assertTaskExecuted(":test")
        assertTestExecuted(className: "org.example.DynamicTests", methodName: null)
        assertTestExecuted(className: "org.example.DynamicTests", methodName: null, displayName: "testFactory()")
        assertTestExecuted(className: "org.example.DynamicTests", methodName: null, displayName: "some nested container")
        assertTestExecuted(className: "org.example.DynamicTests", methodName: "testFactory()[1][1][1]", displayName: "foo")
        assertTestExecuted(className: "org.example.DynamicTests", methodName: "testFactory()[1][1][2]", displayName: "bar")
        assertTestExecuted(className: "org.example.DynamicTests", methodName: null, displayName: "another test factory")
        assertTestExecuted(className: "org.example.DynamicTests", methodName: "anotherTestFactory()[1]", displayName: "foo")
    }
}
