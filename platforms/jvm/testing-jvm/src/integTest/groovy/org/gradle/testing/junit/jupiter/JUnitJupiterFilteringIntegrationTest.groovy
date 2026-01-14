/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testing.junit.jupiter

import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.AbstractTestFilteringIntegrationTest
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER

@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterFilteringIntegrationTest extends AbstractTestFilteringIntegrationTest implements JUnitJupiterMultiVersionTest {
    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.JUNIT_JUPITER
    }

    @Issue("https://github.com/gradle/gradle/issues/19808")
    def "nested classes are executed when filtering by class name on the command line"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${MultiVersionIntegrationSpec.version}'
            }
            test {
                ${maybeConfigureFilters(withConfiguredFilters)}
            }
        """
\
        file("src/test/java/SampleTest.java") << """
            import static org.junit.jupiter.api.Assertions.fail;

            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            public class SampleTest {
                @Test
                public void regularTest() {
                    fail();
                }

                @Nested
                public class NestedTestClass {
                    @Nested
                    public class SubNestedTestClass {
                        @Test
                        public void subNestedTest() {
                            fail();
                        }

                        @ParameterizedTest
                        @ValueSource(strings = { "racecar", "radar", "able was I ere I saw elba" })
                        public void palindromes(String candidate) {
                            fail();
                        }
                    }

                    @Test
                    public void nestedTest() {
                      fail();
                    }
                }
            }
        """
        file("src/test/java/TestSomethingTest.java") << """
            import static org.junit.jupiter.api.Assertions.fail;

            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            public class TestSomethingTest {
                @Test
                public void regularTest() {
                    fail();
                }

                @Nested
                public class NestedTestClass {
                    @Test
                    public void nestedTest() {
                      fail();
                    }
                }
            }
        """

        when:
        fails "test", "--tests", commandLineFilter

        then:
        def testResult = resultsFor(testDirectory, "tests/test", testFramework)
        testResult.assertAtLeastTestPathsExecutedPreNormalized(expectedTests as String[])
        assertExpectedTestCounts(testResult, expectedTests)

        where:
        commandLineFilter                                 | withConfiguredFilters | expectedTests
        'SampleTest'                                      | false                 | [':SampleTest', ':SampleTest:SampleTest$NestedTestClass', ':SampleTest:SampleTest$NestedTestClass:SampleTest$NestedTestClass$SubNestedTestClass']
        'SampleTest'                                      | true                  | [':SampleTest', ':SampleTest:SampleTest$NestedTestClass', ':SampleTest:SampleTest$NestedTestClass:SampleTest$NestedTestClass$SubNestedTestClass']
        'SampleTest$NestedTestClass'                      | false                 | [':SampleTest:SampleTest$NestedTestClass', ':SampleTest:SampleTest$NestedTestClass:SampleTest$NestedTestClass$SubNestedTestClass']
        'SampleTest$NestedTestClass'                      | true                  | [':SampleTest:SampleTest$NestedTestClass', ':SampleTest:SampleTest$NestedTestClass:SampleTest$NestedTestClass$SubNestedTestClass']
        'SampleTest$NestedTestClass$SubNestedTestClass'   | false                 | [':SampleTest:SampleTest$NestedTestClass:SampleTest$NestedTestClass$SubNestedTestClass']
        'SampleTest$NestedTestClass$SubNestedTestClass'   | true                  | [':SampleTest:SampleTest$NestedTestClass:SampleTest$NestedTestClass$SubNestedTestClass']
    }

    @Issue("https://github.com/gradle/gradle/issues/31304")
    def "nested classes are not executed when excluded by class name in test configuration"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${MultiVersionIntegrationSpec.version}'
            }
            test {
                filter.excludeTest("${excludeFilter}", null)
            }
        """

        file("src/test/java/SampleTest.java") << """
            import static org.junit.jupiter.api.Assertions.fail;

            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            public class SampleTest {
                @Test
                public void regularTest() {
                    fail();
                }

                @Nested
                public class NestedTestClass {
                    @Nested
                    public class SubNestedTestClass {
                        @Test
                        public void subNestedTest() {
                            fail();
                        }

                        @ParameterizedTest
                        @ValueSource(strings = { "racecar", "radar", "able was I ere I saw elba" })
                        public void palindromes(String candidate) {
                            fail();
                        }
                    }

                    @Test
                    public void nestedTest() {
                      fail();
                    }
                }
            }
        """

        when:
        fails "test"

        then:
        result.assertTaskExecuted(":test")
        def testResult = resultsFor(testDirectory, "tests/test", testFramework)
        assertExpectedTestCounts(testResult, expectedTests)

        where:
        excludeFilter                                       | expectedTests
        'SampleTest'                                        | [':SampleTest:SampleTest$NestedTestClass', ':SampleTest:SampleTest$NestedTestClass:SampleTest$NestedTestClass$SubNestedTestClass']
        'SampleTest\\$NestedTestClass'                      | [':SampleTest', ':SampleTest$NestedTestClass:SampleTest$NestedTestClass$SubNestedTestClass']
        'SampleTest\\$NestedTestClass\\$SubNestedTestClass' | [':SampleTest', ':SampleTest:SampleTest$NestedTestClass']
        'SampleTest*'                                       | []
    }

    void assertExpectedTestCounts(GenericTestExecutionResult testExecutionResult, List<String> expectedTests) {
        if (expectedTests.contains('SampleTest')) {
            testExecutionResult.testPath('SampleTest').onlyRoot().assertChildCount(1, 0)
        }

        if (expectedTests.contains('SampleTest$NestedTestClass')) {
            testExecutionResult.testPath('SampleTest$NestedTestClass').onlyRoot().assertChildCount(1, 0)
        }

        if (expectedTests.contains('SampleTest$NestedTestClass$SubNestedTestClass')) {
            testExecutionResult.testPath('SampleTest$NestedTestClass$SubNestedTestClass').onlyRoot().assertChildCount(4, 0)
        }
    }

    String maybeConfigureFilters(boolean withConfiguredFilters) {
        return withConfiguredFilters ? """
            filter {
                excludeTestsMatching "*Something*"
            }
        """ : ""
    }
}
