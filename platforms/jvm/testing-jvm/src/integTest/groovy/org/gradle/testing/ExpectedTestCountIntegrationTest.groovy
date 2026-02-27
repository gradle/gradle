/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.tasks.testing.Test
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Integration tests for the {@link Test#getExpectedTestCount()} feature of the Test task.
 */
class ExpectedTestCountIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }
        """
    }

    def "test task succeeds when expected test count matches actual count"() {
        given:
        file("src/test/java/SimpleTest.java") << """
            import org.junit.Test;

            public class SimpleTest {
                @Test
                public void test1() {
                }

                @Test
                public void test2() {
                }
            }
        """

        buildFile << """
            test {
                expectedTestCount = 2
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
    }

    def "test task emits warning or throws exception when expected test count does not match actual count with #scenario"() {
        given:
        file("src/test/java/SimpleTest.java") << """
            import org.junit.Test;

            public class SimpleTest {
                @Test
                public void test1() {
                }

                @Test
                public void test2() {
                }
            }
        """

        buildFile << """
            test {
                expectedTestCount = 5
                failOnUnexpectedTestCount = $failOnMismatch
            }
        """

        when:
        failOnMismatch ? fails("test") : succeeds("test")

        then:
        executedAndNotSkipped(":test")
        if (failOnMismatch) {
            failure.assertHasDescription("Execution failed for task ':test'.")
            failure.assertHasCause("Task :test expected 5 test(s) but executed 2 test(s).")
        } else {
            outputContains("Task :test expected 5 test(s) but executed 2 test(s).")
        }

        where:
        scenario           | failOnMismatch
        "warning only"     | false
        "exception thrown" | true
    }

    def "test task succeeds when expected test count is not set"() {
        given:
        file("src/test/java/SimpleTest.java") << """
            import org.junit.Test;

            public class SimpleTest {
                @Test
                public void test1() {
                }

                @Test
                public void test2() {
                }
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
    }

    def "test task emits warning or throws exception when expected test count is zero but tests exist with #scenario"() {
        given:
        file("src/test/java/SimpleTest.java") << """
            import org.junit.Test;

            public class SimpleTest {
                @Test
                public void test1() {
                }
            }
        """

        buildFile << """
            test {
                expectedTestCount = 0
                failOnUnexpectedTestCount = $failOnMismatch
            }
        """

        when:
        failOnMismatch ? fails("test") : succeeds("test")

        then:
        executedAndNotSkipped(":test")
        if (failOnMismatch) {
            failure.assertHasDescription("Execution failed for task ':test'.")
            failure.assertHasCause("Task :test expected 0 test(s) but executed 1 test(s).")
        } else {
            outputContains("Task :test expected 0 test(s) but executed 1 test(s).")
        }

        where:
        scenario           | failOnMismatch
        "warning only"     | false
        "exception thrown" | true
    }

    def "test task succeeds with multiple test classes when expected count matches"() {
        given:
        file("src/test/java/SimpleTest1.java") << """
            import org.junit.Test;

            public class SimpleTest1 {
                @Test
                public void test1() {
                }
            }
        """

        file("src/test/java/SimpleTest2.java") << """
            import org.junit.Test;

            public class SimpleTest2 {
                @Test
                public void test2() {
                }

                @Test
                public void test3() {
                }
            }
        """

        buildFile << """
            test {
                expectedTestCount = 3
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
    }

    def "test task works with test suite"() {
        given:
        file("src/test/java/Test1.java") << """
            import org.junit.Test;

            public class Test1 {
                @Test
                public void test1() {
                }
            }
        """

        file("src/test/java/Test2.java") << """
            import org.junit.Test;

            public class Test2 {
                @Test
                public void test2() {
                }
            }
        """

        file("src/test/java/AllTests.java") << """
            import org.junit.runner.RunWith;
            import org.junit.runners.Suite;

            @RunWith(Suite.class)
            @Suite.SuiteClasses({Test1.class, Test2.class})
            public class AllTests {
            }
        """

        buildFile << """
            test {
                filter {
                    includeTestsMatching "AllTests"
                }
                expectedTestCount = 2
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
    }

    def "test task works with parameterized test"() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }

            test {
                expectedTestCount = 3
            }
        """

        file("src/test/java/ParameterizedTest.java") << """
            import org.junit.Test;
            import org.junit.runner.RunWith;
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameters;
            import java.util.Arrays;
            import java.util.Collection;

            @RunWith(Parameterized.class)
            public class ParameterizedTest {
                private int value;

                public ParameterizedTest(int value) {
                    this.value = value;
                }

                @Parameters
                public static Collection<Object[]> data() {
                    return Arrays.asList(new Object[][] { {1}, {2}, {3} });
                }

                @Test
                public void testValue() {
                    // Test passes
                }
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
    }

    def "test task works with test filtering by pattern"() {
        given:
        file("src/test/java/IncludedTest.java") << """
            import org.junit.Test;

            public class IncludedTest {
                @Test
                public void test1() {
                }

                @Test
                public void test2() {
                }
            }
        """

        file("src/test/java/ExcludedTest.java") << """
            import org.junit.Test;

            public class ExcludedTest {
                @Test
                public void test3() {
                }
            }
        """

        buildFile << """
            test {
                filter {
                    includeTestsMatching "IncludedTest"
                }
                expectedTestCount = 2
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
    }

    def "test task works with test filtering by method name"() {
        given:
        file("src/test/java/FilteredTest.java") << """
            import org.junit.Test;

            public class FilteredTest {
                @Test
                public void includedTest() {
                }

                @Test
                public void anotherIncludedTest() {
                }

                @Test
                public void excludedTest() {
                }
            }
        """

        buildFile << """
            test {
                filter {
                    includeTestsMatching "FilteredTest.included*"
                }
                expectedTestCount = 2
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
    }

    def "expectedTestCount validation is skipped when --tests command line argument is used"() {
        given:
        file("src/test/java/Test1.java") << """
            import org.junit.Test;

            public class Test1 {
                @Test
                public void test1() {
                }

                @Test
                public void test2() {
                }
            }
        """

        file("src/test/java/Test2.java") << """
            import org.junit.Test;

            public class Test2 {
                @Test
                public void test3() {
                }
            }
        """

        buildFile << """
            test {
                expectedTestCount = 3
                failOnUnexpectedTestCount = true
            }
        """

        when:
        // Using --tests to filter to only Test1, which has 2 tests
        // Expected count is 3, but only 2 tests will run
        // This should succeed because --tests filtering bypasses expectedTestCount validation
        succeeds("test", "--tests", "Test1")

        then:
        executedAndNotSkipped(":test")
        // Verify no warning or error was emitted about mismatched test count
        !output.contains("expected 3 test(s) but executed 2 test(s)")
    }

    def "expectedTestCount validation is skipped when --tests filters to specific test method"() {
        given:
        file("src/test/java/MyTest.java") << """
            import org.junit.Test;

            public class MyTest {
                @Test
                public void test1() {
                }

                @Test
                public void test2() {
                }

                @Test
                public void test3() {
                }
            }
        """

        buildFile << """
            test {
                expectedTestCount = 3
                failOnUnexpectedTestCount = true
            }
        """

        when:
        // Using --tests to filter to only one test method
        // Expected count is 3, but only 1 test will run
        // This should succeed because --tests filtering bypasses expectedTestCount validation
        succeeds("test", "--tests", "MyTest.test1")

        then:
        executedAndNotSkipped(":test")
        // Verify no warning or error was emitted about mismatched test count
        !output.contains("expected 3 test(s) but executed 1 test(s)")
    }
}
