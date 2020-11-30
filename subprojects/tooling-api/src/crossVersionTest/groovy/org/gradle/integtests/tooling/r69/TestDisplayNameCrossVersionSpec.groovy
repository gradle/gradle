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
            launcher.withTaskAndTestClasses(':test',['org.example.SimpleTests'])
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
            launcher.withTaskAndTestClasses(':test',['org.example.TestingAStackDemo*'])
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
}
