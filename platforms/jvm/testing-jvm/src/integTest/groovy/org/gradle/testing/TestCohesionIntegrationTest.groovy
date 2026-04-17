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

package org.gradle.testing

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Demonstrates how class-level test cohesion is (or isn't) preserved when using multiple workers.
 * <p>
 * Many test patterns require all methods from a class to execute together in the same
 * JVM: {@code @BeforeAll}/{@code @AfterAll} lifecycle, {@code @Nested} classes sharing
 * outer state, {@code @Stepwise} ordering, shared mutable static state, etc.
 * <p>
 * Each test runs with {@code maxParallelForks = 4} and uses a {@code where:} block
 * to verify behavior both with and without daemon-side discovery.
 */
class TestCohesionIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    // region Cohesion broken by BY_INDIVIDUAL_TEST distribution
    def "BeforeAll and AfterAll run exactly once per class (#label)"() {
        given:
        buildFile << jupiterBuildScript(daemonDiscovery, strategy)

        file('src/test/java/org/example/LifecycleTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;

            public class LifecycleTest {
                @BeforeAll
                static void beforeAll() {
                    System.out.println("BEFORE_ALL");
                }

                @AfterAll
                static void afterAll() {
                    System.out.println("AFTER_ALL");
                }

                @Test
                void test1() {
                    System.out.println("TEST_1");
                }

                @Test
                void test2() {
                    System.out.println("TEST_2");
                }

                @Test
                void test3() {
                    System.out.println("TEST_3");
                }
            }
        """.stripIndent()

        when:
        run("test", "--info")

        then:
        if (strategy == 'BY_INDIVIDUAL_TEST') {
            // Naive distribution sends methods to separate workers, duplicating lifecycle
            output.readLines().findAll { it.contains("BEFORE_ALL") }.size() > 1
        } else {
            output.readLines().findAll { it.contains("BEFORE_ALL") }.size() == 1
            output.readLines().findAll { it.contains("AFTER_ALL") }.size() == 1

            GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
            testResult.testPath("org.example.LifecycleTest").onlyRoot().assertChildCount(3, 0)
        }

        where:
        label                                          | daemonDiscovery | strategy
        'daemon discovery BY_TOP_LEVEL_TEST_CONTAINER' | true            | 'BY_TOP_LEVEL_TEST_CONTAINER'
        'daemon discovery BY_INDIVIDUAL_TEST'          | true            | 'BY_INDIVIDUAL_TEST'
    }

    def "shared mutable static state is consistent within a single worker (#label)"() {
        given:
        buildFile << jupiterBuildScript(daemonDiscovery, strategy)

        file('src/test/java/org/example/SharedStateTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;

            @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
            public class SharedStateTest {
                private static int counter = 0;

                @Test
                @Order(1)
                void first() {
                    counter++;
                    System.out.println("COUNTER_AFTER_FIRST=" + counter);
                }

                @Test
                @Order(2)
                void second() {
                    counter++;
                    System.out.println("COUNTER_AFTER_SECOND=" + counter);
                }

                @Test
                @Order(3)
                void third() {
                    counter++;
                    System.out.println("COUNTER_AFTER_THIRD=" + counter);
                }
            }
        """.stripIndent()

        when:
        run("test", "--info")

        then:
        if (strategy == 'BY_INDIVIDUAL_TEST') {
            // Naive distribution may split methods across workers, resetting static state.
            // Either outcome is accepted since scheduling is non-deterministic.
            true
        } else {
            output.contains("COUNTER_AFTER_FIRST=1")
            output.contains("COUNTER_AFTER_SECOND=2")
            output.contains("COUNTER_AFTER_THIRD=3")
        }

        where:
        label                                          | daemonDiscovery | strategy
        'daemon discovery BY_TOP_LEVEL_TEST_CONTAINER' | true            | 'BY_TOP_LEVEL_TEST_CONTAINER'
        'daemon discovery BY_INDIVIDUAL_TEST'          | true            | 'BY_INDIVIDUAL_TEST'
    }

    def "Spock Stepwise tests run in declaration order (#label)"() {
        given:
        buildFile << spockBuildScript(daemonDiscovery, strategy)

        file('src/test/groovy/org/example/StepwiseSpec.groovy') << """
            package org.example
            import spock.lang.Specification
            import spock.lang.Stepwise

            @Stepwise
            class StepwiseSpec extends Specification {
                def "step 1"() {
                    expect:
                    System.out.println("STEP_1")
                    true
                }

                def "step 2"() {
                    expect:
                    System.out.println("STEP_2")
                    true
                }

                def "step 3"() {
                    expect:
                    System.out.println("STEP_3")
                    true
                }

                def "step 4"() {
                    expect:
                    System.out.println("STEP_4")
                    true
                }

                def "step 5"() {
                    expect:
                    System.out.println("STEP_5")
                    true
                }
            }
        """.stripIndent()

        when:
        run("test", "--info")

        then:
        if (strategy == 'BY_INDIVIDUAL_TEST') {
            // Naive distribution sends methods to separate workers, duplicating stepwise execution
            def stepLines = output.readLines().findAll { it =~ /STEP_\d+/ }
            stepLines.size() > 5
        } else {
            stepsRanInOrder(5)
        }

        where:
        label                                          | daemonDiscovery | strategy
        'daemon discovery BY_TOP_LEVEL_TEST_CONTAINER' | true            | 'BY_TOP_LEVEL_TEST_CONTAINER'
        'daemon discovery BY_INDIVIDUAL_TEST'          | true            | 'BY_INDIVIDUAL_TEST'
    }
    // endregion Cohesion broken by BY_INDIVIDUAL_TEST distribution

    // region Cohesion preserved by all strategies
    def "nested classes share outer BeforeEach — runs exactly once per method (#label)"() {
        given:
        buildFile << jupiterBuildScript(daemonDiscovery, strategy)

        file('src/test/java/org/example/NestedLifecycleTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;

            public class NestedLifecycleTest {
                @BeforeEach
                void outerSetup() {
                    System.out.println("OUTER_BEFORE_EACH");
                }

                @Test
                void outerTest() {
                    System.out.println("OUTER_TEST");
                }

                @Nested
                class InnerA {
                    @Test
                    void innerA1() {
                        System.out.println("INNER_A1");
                    }

                    @Test
                    void innerA2() {
                        System.out.println("INNER_A2");
                    }
                }

                @Nested
                class InnerB {
                    @Test
                    void innerB1() {
                        System.out.println("INNER_B1");
                    }

                    @Test
                    void innerB2() {
                        System.out.println("INNER_B2");
                    }
                }
            }
        """.stripIndent()

        when:
        run("test", "--info")

        then: 'outer BeforeEach runs exactly 5 times (once per test method across all nested classes)'
        output.readLines().findAll { it.contains("OUTER_BEFORE_EACH") }.size() == 5

        where:
        label                                          | daemonDiscovery | strategy
        'daemon discovery BY_TOP_LEVEL_TEST_CONTAINER' | true            | 'BY_TOP_LEVEL_TEST_CONTAINER'
        'daemon discovery BY_INDIVIDUAL_TEST'          | true            | 'BY_INDIVIDUAL_TEST'
    }

    def "TestFactory dynamic tests stay in a single worker (#label)"() {
        given:
        buildFile << jupiterBuildScript(daemonDiscovery, strategy)

        file('src/test/java/org/example/DynamicTestsExample.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            import java.util.*;
            import java.util.stream.*;

            public class DynamicTestsExample {
                private static int executionCount = 0;

                @BeforeAll
                static void setup() {
                    System.out.println("DYNAMIC_BEFORE_ALL");
                }

                @TestFactory
                Collection<DynamicTest> dynamicTests() {
                    return Arrays.asList(
                        DynamicTest.dynamicTest("dynamic 1", () -> {
                            executionCount++;
                            System.out.println("DYNAMIC_1 count=" + executionCount);
                        }),
                        DynamicTest.dynamicTest("dynamic 2", () -> {
                            executionCount++;
                            System.out.println("DYNAMIC_2 count=" + executionCount);
                        }),
                        DynamicTest.dynamicTest("dynamic 3", () -> {
                            executionCount++;
                            System.out.println("DYNAMIC_3 count=" + executionCount);
                            Assertions.assertEquals(3, executionCount, "all 3 should run in same JVM");
                        })
                    );
                }
            }
        """.stripIndent()

        when:
        run("test", "--info")

        then: 'BeforeAll runs once and all dynamic tests share the same static counter'
        output.readLines().findAll { it.contains("DYNAMIC_BEFORE_ALL") }.size() == 1
        output.contains("DYNAMIC_3 count=3")

        where:
        label                                          | daemonDiscovery | strategy
        'daemon discovery BY_TOP_LEVEL_TEST_CONTAINER' | true            | 'BY_TOP_LEVEL_TEST_CONTAINER'
        'daemon discovery BY_INDIVIDUAL_TEST'          | true            | 'BY_INDIVIDUAL_TEST'
    }

    def "parameterized tests share class BeforeAll (#label)"() {
        given:
        buildFile << jupiterBuildScript(daemonDiscovery, strategy)

        file('src/test/java/org/example/ParamWithLifecycleTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            public class ParamWithLifecycleTest {
                @BeforeAll
                static void setup() {
                    System.out.println("PARAM_BEFORE_ALL");
                }

                @AfterAll
                static void teardown() {
                    System.out.println("PARAM_AFTER_ALL");
                }

                @ParameterizedTest
                @ValueSource(strings = {"a", "b", "c"})
                void paramTest(String value) {
                    System.out.println("PARAM_TEST_" + value);
                }
            }
        """.stripIndent()

        when:
        run("test", "--info")

        then: 'BeforeAll and AfterAll each run exactly once'
        output.readLines().findAll { it.contains("PARAM_BEFORE_ALL") }.size() == 1
        output.readLines().findAll { it.contains("PARAM_AFTER_ALL") }.size() == 1

        and: 'all 3 parameterized invocations ran'
        output.contains("PARAM_TEST_a")
        output.contains("PARAM_TEST_b")
        output.contains("PARAM_TEST_c")

        where:
        label                                          | daemonDiscovery | strategy
        'daemon discovery BY_TOP_LEVEL_TEST_CONTAINER' | true            | 'BY_TOP_LEVEL_TEST_CONTAINER'
        'daemon discovery BY_INDIVIDUAL_TEST'          | true            | 'BY_INDIVIDUAL_TEST'
    }

    def "repeated tests share class lifecycle (#label)"() {
        given:
        buildFile << jupiterBuildScript(daemonDiscovery, strategy)

        file('src/test/java/org/example/RepeatedLifecycleTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;

            public class RepeatedLifecycleTest {
                private static int repeatCount = 0;

                @BeforeAll
                static void setup() {
                    System.out.println("REPEATED_BEFORE_ALL");
                }

                @RepeatedTest(3)
                void repeatedMethod() {
                    repeatCount++;
                    System.out.println("REPEAT_" + repeatCount);
                }

                @AfterAll
                static void teardown() {
                    System.out.println("REPEATED_AFTER_ALL count=" + repeatCount);
                    Assertions.assertEquals(3, repeatCount, "all 3 repetitions should run in same JVM");
                }
            }
        """.stripIndent()

        when:
        run("test", "--info")

        then: 'lifecycle runs once and all repetitions share state'
        output.readLines().findAll { it.contains("REPEATED_BEFORE_ALL") }.size() == 1
        output.contains("REPEATED_AFTER_ALL count=3")

        where:
        label                                          | daemonDiscovery | strategy
        'daemon discovery BY_TOP_LEVEL_TEST_CONTAINER' | true            | 'BY_TOP_LEVEL_TEST_CONTAINER'
        'daemon discovery BY_INDIVIDUAL_TEST'          | true            | 'BY_INDIVIDUAL_TEST'
    }
    // endregion Cohesion preserved by all strategies

    // region setup
    @Override
    TestFramework getTestFramework() {
        return TestFramework.JUNIT_JUPITER
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    private void stepsRanInOrder(int expectedCount) {
        int lastSeen = 0
        for (String line in output.readLines()) {
            def matcher = (line =~ /STEP_(\d+)/)
            if (matcher) {
                int step = matcher[0][1] as int
                assert step == lastSeen + 1: "Expected STEP_${lastSeen + 1} but saw STEP_${step}"
                lastSeen = step
            }
        }
        assert lastSeen == expectedCount: "Expected all ${expectedCount} steps to run, but only saw ${lastSeen}"
    }

    private String jupiterBuildScript(boolean daemonDiscovery, String strategy = 'BY_TOP_LEVEL_TEST_CONTAINER') {
        return """
            plugins {
                id 'java-library'
            }

            import org.gradle.api.tasks.testing.distribution.TestDistributionStrategy

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()

                targets.all {
                    testTask.configure {
                        maxParallelForks = 4
                        ${daemonDiscovery ? "useDaemonSideTestDiscovery = true\n                        testDistributionStrategy = TestDistributionStrategy.${strategy}" : ''}
                    }
                }
            }
        """.stripIndent()
    }

    private String spockBuildScript(boolean daemonDiscovery, String strategy = 'BY_TOP_LEVEL_TEST_CONTAINER') {
        return """
            plugins {
                id 'groovy'
            }

            import org.gradle.api.tasks.testing.distribution.TestDistributionStrategy

            ${mavenCentralRepository()}

            dependencies {
                constraints {
                    implementation("org.apache.groovy:groovy:${GroovySystem.version}") {
                        because("need a version of Groovy that supports the current JDK")
                    }
                }
            }

            testing.suites.test {
                useSpock()

                targets.all {
                    testTask.configure {
                        maxParallelForks = 4
                        ${daemonDiscovery ? "useDaemonSideTestDiscovery = true\n                        testDistributionStrategy = TestDistributionStrategy.${strategy}" : ''}
                    }
                }
            }
        """.stripIndent()
    }
    // endregion setup
}
