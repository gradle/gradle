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

package org.gradle.testing.junitplatform

import org.gradle.integtests.fixtures.DefaultTestExecutionResult

class JUnitPlatformFilteringIntegrationTest extends JUnitPlatformIntegrationSpec {

    def 'can filter nested tests'() {
        given:
        file('src/test/java/org/gradle/NestedTest.java') << '''
            package org.gradle;
            import static org.junit.jupiter.api.Assertions.*;

            import java.util.EmptyStackException;
            import java.util.Stack;

            import org.junit.jupiter.api.*;

            class NestedTest {
                @Test
                void outerTest() {
                }

                @Nested
                class Inner {
                    @Test
                    void innerTest() {
                    }
                }
            }
        '''
        buildFile << '''
            test {
                filter {
                    includeTestsMatching "*innerTest*"
                }
            }
        '''
        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted('org.gradle.NestedTest$Inner')
            .testClass('org.gradle.NestedTest$Inner').assertTestCount(1, 0, 0)
            .assertTestPassed('innerTest()')
    }

    def 'can use nested class as test pattern'() {
        given:
        file('src/test/java/EnclosingClass.java') << '''
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.Nested;
            import static org.junit.jupiter.api.Assertions.assertEquals;

            class EnclosingClass {
                @Nested
                class NestedClass {
                    @Test
                    void nestedTest() {
                    }
                    @Test
                    void anotherTest() {
                    }
                }
                @Nested
                class AnotherNestedClass {
                    @Test
                    void foo() {
                    }
                }
                @Test
                void foo() {
                }
            }
        '''
        when:
        succeeds('test', '--tests', 'EnclosingClass$NestedClass.nestedTest')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted('EnclosingClass$NestedClass')
            .testClass('EnclosingClass$NestedClass')
            .assertTestCount(1, 0, 0)
            .assertTestPassed('nestedTest')
    }

    def 'can filter tests from a superclass'() {
        given:
        file('src/test/java/SuperClass.java') << '''
            import org.junit.jupiter.api.Test;

            abstract class SuperClass {
                @Test
                void superTest() {
                }
            }
        '''
        file('src/test/java/SubClass.java') << '''
            import org.junit.jupiter.api.Test;

            class SubClass extends SuperClass {
                @Test
                void subTest() {
                }
            }
        '''

        when:
        succeeds('test', '--tests', 'SubClass.superTest')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted('SubClass')
            .testClass('SubClass')
            .assertTestCount(1, 0, 0)
            .assertTestPassed('superTest')
    }

}
