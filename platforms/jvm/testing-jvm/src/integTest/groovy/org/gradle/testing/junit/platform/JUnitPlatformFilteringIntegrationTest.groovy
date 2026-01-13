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

package org.gradle.testing.junit.platform

import static org.gradle.testing.fixture.JUnitCoverage.getLATEST_JUPITER_VERSION

class JUnitPlatformFilteringIntegrationTest extends JUnitPlatformIntegrationSpec {
    @Override
    String getJupiterVersion() {
        return LATEST_JUPITER_VERSION
    }

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
        def results = resultsFor(testDirectory)
        results.testPath("org.gradle.NestedTest").onlyRoot()
            .assertChildCount(1, 0)
        results.testPathPreNormalized(':org.gradle.NestedTest:org.gradle.NestedTest$Inner').onlyRoot()
            .assertOnlyChildrenExecuted("innerTest()")
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
        def results = resultsFor(testDirectory)
        results.testPath("EnclosingClass").onlyRoot()
            .assertChildCount(1, 0)
        results.testPathPreNormalized(':EnclosingClass:EnclosingClass$NestedClass').onlyRoot()
            .assertChildCount(1, 0)
            .assertChildrenExecuted("nestedTest()")
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
        def results = resultsFor(testDirectory)
        results.testPath("SubClass").onlyRoot()
            .assertChildCount(1, 0)
            .assertOnlyChildrenExecuted("superTest()")
    }
}
