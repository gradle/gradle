/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing.junitplatform

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.hamcrest.Matchers

import static org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter.LATEST_JUPITER_VERSION

class JUnitPlatformUserGuideIntegrationTest extends JUnitPlatformIntegrationSpec {
    def 'can display test case in @DisplayName'() {
        given:
        file('src/test/java/org/gradle/DisplayNameDemo.java') << '''
package org.gradle; 
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("A special test case")
class DisplayNameDemo {

    @Test
    @DisplayName("Custom test name containing spaces")
    void testWithDisplayNameContainingSpaces() {
    }
}
'''
        file('src/test/java/org/gradle/DisplayNameDemo2.java') << ''' 
package org.gradle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("A special test case")
class DisplayNameDemo2 {

    @Test
    @DisplayName("╯°□°）╯")
    void testWithDisplayNameContainingSpecialCharacters() {
    }
}
'''
        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted('org.gradle.DisplayNameDemo', 'org.gradle.DisplayNameDemo2')
            .testClass('org.gradle.DisplayNameDemo').assertTestCount(2, 0, 0)
            .assertTestPassed('Custom test name containing spaces')
            .assertTestPassed('╯°□°）╯')
    }

    def 'can change test instance lifecycle with #method'() {
        given:
        buildFile << """
test {
    jvmArgs '${jvmArg}'
}
"""
        file('src/test/java/org/gradle/LifecycleTest.java') << """ 
package org.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

${annotation}
public class LifecycleTest {
    private static int counter = 0;
    
    public LifecycleTest() {
        if(counter == 0) {
            counter += 1;
        } else {
            throw new IllegalStateException("I can only be instantiated once!");
        }
    }
    
    @Test
    public void test1() {
        System.out.println(this);
    }
    @Test
    public void test2() {
        System.out.println(this);
    }
}
"""
        expect:
        succeeds('test')

        where:
        method        | jvmArg                                                     | annotation
        'JVM args'    | '-Djunit.jupiter.testinstance.lifecycle.default=per_class' | ''
        'annotations' | ''                                                         | '@TestInstance(Lifecycle.PER_CLASS)'
    }

    def 'can perform nested tests'() {
        file('src/test/java/org/gradle/TestingAStackDemo.java') << '''
package org.gradle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EmptyStackException;
import java.util.Stack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            assertThrows(EmptyStackException.class, () -> stack.pop());
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
            void isNotEmpty() {
                assertFalse(stack.isEmpty());
            }
        }
    }
}
'''
        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClass('org.gradle.TestingAStackDemo').assertTestCount(4, 0, 0)
            .assertTestPassed('is instantiated with new Stack()')
            .assertTestPassed('is empty')
            .assertTestPassed('throws EmptyStackException when popped')
            .assertTestPassed('it is no longer empty')
    }

    def 'can support dependency injection'() {
        given:
        file('src/test/java/org/gradle/TestInfoDemo.java') << '''
package org.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@DisplayName("TestInfo Demo")
class TestInfoDemo {

    TestInfoDemo(TestInfo testInfo) {
        assertEquals("TestInfo Demo", testInfo.getDisplayName());
    }

    @BeforeEach
    void init(TestInfo testInfo) {
        String displayName = testInfo.getDisplayName();
        assertTrue(displayName.equals("TEST 1") || displayName.equals("test2()"));
    }

    @Test
    @DisplayName("TEST 1")
    @Tag("my-tag")
    void test1(TestInfo testInfo) {
        assertEquals("TEST 1", testInfo.getDisplayName());
        assertTrue(testInfo.getTags().contains("my-tag"));
    }

    @Test
    void test2() {
    }
}
'''
        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClass('org.gradle.TestInfoDemo').assertTestCount(2, 0, 0)
            .assertTestPassed('test2')
            .assertTestPassed('TEST 1')
    }

    def 'can use custom Extension'() {
        given:
        file('src/main/java/org/gradle/MyExtension.java') << '''
package org.gradle;
import org.junit.jupiter.api.extension.*;

public class MyExtension implements TestInstancePostProcessor {
        @Override
        public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
                System.out.println("Instance are created!");
        }
}        
'''
        file('src/test/java/org/gradle/ExtensionTest.java') << '''
package org.gradle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.*;

@ExtendWith(MyExtension.class)
public class ExtensionTest {
    @Test public void test1() { }
    @Test public void test2() { }
}
'''
        when:
        succeeds('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('org.gradle.ExtensionTest').assertTestCount(2, 0, 0)
            .assertTestCaseStdout('test1', Matchers.containsString('Instance are created!'))
            .assertTestCaseStdout('test2', Matchers.containsString('Instance are created!'))
    }

    def 'can test interface default method'() {
        file('src/test/java/org/gradle/TestInterfaceDynamicTestsDemo.java') << '''
package org.gradle;

import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

interface TestInterfaceDynamicTestsDemo {

    @TestFactory
    default Collection<DynamicTest> dynamicTestsFromCollection() {
        return Arrays.asList(
            DynamicTest.dynamicTest("1st dynamic test in test interface", () -> assertTrue(true)),
            DynamicTest.dynamicTest("2nd dynamic test in test interface", () -> assertEquals(4, 2 * 2))
        );
    }
}
'''
        when:
        succeeds('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('org.gradle.TestInterfaceDynamicTestsDemo').assertTestCount(2, 0, 0)
            .assertTestPassed('1st dynamic test in test interface')
            .assertTestPassed('2nd dynamic test in test interface')
    }

    def 'can support parameterized tests'() {
        given:
        buildFile << """
dependencies {
    testCompile 'org.junit.jupiter:junit-jupiter-params:${LATEST_JUPITER_VERSION}'
}
"""
        file('src/test/java/org/gradle/Test.java') << '''
package org.gradle;

import org.junit.jupiter.params.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test {
    @ParameterizedTest
    @ValueSource(strings = { "a", "b", "c" })
    void ok(String s) {
        assertTrue(s.length() == 1);
    }
}
'''
        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClass('org.gradle.Test').assertTestCount(3, 0, 0)
            .assertTestPassed('[1] a')
            .assertTestPassed('[2] b')
            .assertTestPassed('[3] c')
    }

    def 'can use test template'() {
        given:
        file('src/test/java/org/gradle/TestTemplate.java') << '''
package org.gradle;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.extension.*;

public class TestTemplate {
    @TestTemplate
    @ExtendWith(MyTestTemplateInvocationContextProvider.class)
    void testTemplate(String parameter) {
        assertEquals(3, parameter.length());
    }
    
    private static class MyTestTemplateInvocationContextProvider implements TestTemplateInvocationContextProvider {
        @Override
        public boolean supportsTestTemplate(ExtensionContext context) {
            return true;
        }

        @Override
        public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
            return Stream.of(invocationContext("foo"), invocationContext("bar"));
        }

        private TestTemplateInvocationContext invocationContext(String parameter) {
            return new TestTemplateInvocationContext() {
                @Override
                public String getDisplayName(int invocationIndex) {
                    return parameter;
                }

                @Override
                public List<Extension> getAdditionalExtensions() {
                    return Collections.singletonList(new ParameterResolver() {
                        @Override
                        public boolean supportsParameter(ParameterContext parameterContext,
                                ExtensionContext extensionContext) {
                            return parameterContext.getParameter().getType().equals(String.class);
                        }

                        @Override
                        public Object resolveParameter(ParameterContext parameterContext,
                                ExtensionContext extensionContext) {
                            return parameter;
                        }
                    });
                }
            };
        }
    }
}
'''
        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClass('org.gradle.Test').assertTestCount(2, 0, 0)
            .assertTestPassed('foo')
            .assertTestPassed('bar')
    }

    def 'can support dynamic tests'() {
        given:
        file('src/test/java/org/gradle/DynamicTestsDemo.java') << '''
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.junit.jupiter.api.*;

class DynamicTestsDemo {
    // This will result in a JUnitException!
    @TestFactory
    List<String> dynamicTestsWithInvalidReturnType() {
        return Arrays.asList("Hello");
    }

    @TestFactory
    Collection<DynamicTest> dynamicTestsFromCollection() {
        return Arrays.asList(
            dynamicTest("1st dynamic test", () -> assertTrue(true)),
            dynamicTest("2nd dynamic test", () -> assertEquals(4, 2 * 2))
        );
    }
}
'''

    }
}
