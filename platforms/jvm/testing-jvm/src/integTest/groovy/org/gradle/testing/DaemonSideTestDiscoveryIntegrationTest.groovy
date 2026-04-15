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
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DaemonSideTestDiscoveryIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    @Override
    TestFramework getTestFramework() {
        return TestFramework.JUNIT_JUPITER
    }

    def "can run tests with daemon-side discovery"() {
        given:
        buildFile << javaLibWithDaemonDiscoveryJupiterTests()

        file('src/test/java/org/example/NotATest.java') << """
            package org.example;
            public class NotATest {
            }
        """.stripIndent()

        file('src/test/java/org/example/DontRunMe.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            public class DontRunMe {
                @Test
                public void nope() {
                    throw new RuntimeException("I should not be run!");
                }
            }
        """.stripIndent()

        file('src/test/java/org/example/Ok.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            public class Ok {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()

        file('src/test/java/org/example/Ok2.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            public class Ok2 {
                @Test
                public void ok2() {
                }
            }
        """.stripIndent()

        when:
        run("test", "--tests=Ok*")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.testPath("org.example.Ok").onlyRoot().assertChildCount(1, 0)
        testResult.testPath("org.example.Ok2").onlyRoot().assertChildCount(1, 0)
    }

    def "fails with JUnit 4 when daemon-side discovery is enabled"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }

            test {
                useJUnit()
                useDaemonSideTestDiscovery = true
            }
        """.stripIndent()

        file('src/test/java/org/example/Junit4Test.java') << """
            package org.example;
            import org.junit.Test;
            public class Junit4Test {
                @Test
                public void passes() {
                }
            }
        """.stripIndent()

        when:
        fails("test")

        then:
        failure.assertHasCause("Daemon-side test discovery is only supported with JUnit Platform, but task ':test' is not configured to use it.")
        errorOutput.contains("Configure JUnit Platform: testing.suites.test { useJUnitJupiter() }")
        errorOutput.contains("Disable daemon-side test discovery: testing.suites.test { useDaemonSideTestDiscovery = false }")
    }

    def "applies tag filters during daemon-side discovery"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()
            }

            test {
                useDaemonSideTestDiscovery = true
                useJUnitPlatform {
                    includeTags 'fast'
                }
            }
        """.stripIndent()

        file('src/test/java/org/example/FastTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            @Tag("fast")
            public class FastTest {
                @Test
                public void quick() {
                }
            }
        """.stripIndent()

        file('src/test/java/org/example/SlowTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            @Tag("slow")
            public class SlowTest {
                @Test
                public void slow() {
                    throw new RuntimeException("Should not be run!");
                }
            }
        """.stripIndent()

        when:
        run("test")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.testPath("org.example.FastTest").onlyRoot().assertChildCount(1, 0)
    }

    def "filters tests by name in the daemon"() {
        given:
        buildFile << javaLibWithDaemonDiscoveryJupiterTests()

        file('src/test/java/org/example/IncludedTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            public class IncludedTest {
                @Test
                public void included() {
                    System.out.println("INCLUDED_RAN");
                }
            }
        """.stripIndent()

        file('src/test/java/org/example/ExcludedTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            public class ExcludedTest {
                @Test
                public void excluded() {
                    System.out.println("EXCLUDED_RAN");
                    throw new RuntimeException("Should not be run!");
                }
            }
        """.stripIndent()

        when:
        run("test", "--tests", "org.example.IncludedTest")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.testPath("org.example.IncludedTest").onlyRoot().assertChildCount(1, 0)
    }

    def "runs previously failed tests first"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()
            }

            test {
                useDaemonSideTestDiscovery = true
                systemProperty('index.of.test.to.fail', findProperty('failIndex') ?: '0')
            }
        """.stripIndent()

        [1, 2, 3].each { i ->
            file("src/test/java/org/example/Test${i}.java") << """
                package org.example;
                import org.junit.jupiter.api.*;
                public class Test${i} {
                    @Test
                    public void run() {
                        System.out.println("Test index ${i}");
                        if ("${i}".equals(System.getProperty("index.of.test.to.fail"))) {
                            throw new RuntimeException("forced failure");
                        }
                    }
                }
            """.stripIndent()
        }

        when: 'first run fails test 2'
        fails("test", "-PfailIndex=2", "--info")

        then:
        resultsFor("tests/test", testFramework)
            .testPath("org.example.Test2", "run()").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)

        when: 'second run should execute test 2 first'
        fails("test", "-PfailIndex=2", "--info")

        then:
        failedTestRunFirst(2)
    }

    private void failedTestRunFirst(int failedIndex) {
        for (String line in output.readLines()) {
            if (line.contains("Test index ${failedIndex}")) {
                return
            } else if (line.contains("Test index")) {
                assert false: "Expected Test index ${failedIndex} to run first, but saw: ${line}"
            }
        }
        assert false: "Never saw Test index ${failedIndex} in output"
    }

    def "discovers parameterized tests"() {
        given:
        buildFile << javaLibWithDaemonDiscoveryJupiterTests()

        file('src/test/java/org/example/ParameterizedTests.java') << """
            package org.example;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            public class ParameterizedTests {
                @ParameterizedTest
                @ValueSource(strings = {"one", "two", "three"})
                void paramTest(String value) {
                    assertTrue(value.length() > 0);
                }
            }
        """.stripIndent()

        when:
        run("test")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.testPath("org.example.ParameterizedTests").onlyRoot().assertChildCount(1, 0)
    }

    def "discovers nested test classes"() {
        given:
        buildFile << javaLibWithDaemonDiscoveryJupiterTests()

        file('src/test/java/org/example/OuterTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;

            public class OuterTest {
                @Test
                void outerMethod() {
                }

                @Nested
                class InnerTest {
                    @Test
                    void innerMethod() {
                    }
                }
            }
        """.stripIndent()

        when:
        run("test")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.testPath("org.example.OuterTest").onlyRoot().assertChildCount(2, 0)
    }

    def "handles disabled tests"() {
        given:
        buildFile << javaLibWithDaemonDiscoveryJupiterTests()

        file('src/test/java/org/example/MixedTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;

            public class MixedTest {
                @Test
                void enabled() {
                }

                @Disabled("not ready")
                @Test
                void disabled() {
                    throw new RuntimeException("Should not run!");
                }
            }
        """.stripIndent()

        when:
        run("test")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.testPath("org.example.MixedTest").onlyRoot()
            .assertChildrenExecuted("enabled()")
            .assertChildrenSkipped("disabled()")
    }

    def "succeeds with empty test suite when failOnNoDiscoveredTests is false"() {
        given:
        buildFile << javaLibWithDaemonDiscoveryJupiterTests()
        buildFile << """
            test {
                failOnNoDiscoveredTests = false
            }
        """.stripIndent()

        file('src/test/java/org/example/NotATest.java') << """
            package org.example;
            public class NotATest {
            }
        """.stripIndent()

        when:
        run("test")

        then:
        noExceptionThrown()
    }

    def "discovers JUnit 4 tests via vintage engine"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()
            }

            dependencies {
                testCompileOnly 'junit:junit:4.13.2'
                testRuntimeOnly 'org.junit.vintage:junit-vintage-engine:5.11.4'
            }

            test {
                useDaemonSideTestDiscovery = true
            }
        """.stripIndent()

        file('src/test/java/org/example/VintageTest.java') << """
            package org.example;
            import org.junit.Test;
            import static org.junit.Assert.assertTrue;

            public class VintageTest {
                @Test
                public void legacyTest() {
                    assertTrue(true);
                }
            }
        """.stripIndent()

        file('src/test/java/org/example/JupiterTest.java') << """
            package org.example;
            import org.junit.jupiter.api.Test;

            public class JupiterTest {
                @Test
                void modernTest() {
                }
            }
        """.stripIndent()

        when:
        run("test")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.testPath("org.example.JupiterTest").onlyRoot().assertChildCount(1, 0)
        testResult.testPath("org.example.VintageTest").onlyRoot().assertChildCount(1, 0)
    }

    def "fails with TestNG when daemon-side discovery is enabled"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'org.testng:testng:7.5'
            }

            test {
                useTestNG()
                useDaemonSideTestDiscovery = true
            }
        """.stripIndent()

        file('src/test/java/org/example/TestNGTest.java') << """
            package org.example;
            import org.testng.annotations.Test;

            public class TestNGTest {
                @Test
                public void passes() {
                }
            }
        """.stripIndent()

        when:
        fails("test")

        then:
        failure.assertHasCause("Daemon-side test discovery is only supported with JUnit Platform, but task ':test' is not configured to use it.")
        errorOutput.contains("Configure JUnit Platform: testing.suites.test { useJUnitJupiter() }")
        errorOutput.contains("Disable daemon-side test discovery: testing.suites.test { useDaemonSideTestDiscovery = false }")
    }

    def "filters individual methods within a matching class"() {
        given:
        buildFile << javaLibWithDaemonDiscoveryJupiterTests()

        file('src/test/java/org/example/MixedTest.java') << """
            package org.example;
            import org.junit.jupiter.api.*;

            public class MixedTest {
                @Test
                public void included() {
                }

                @Test
                public void excluded() {
                    throw new RuntimeException("Should not be run!");
                }
            }
        """.stripIndent()

        when:
        run("test", "--tests", "org.example.MixedTest.included")

        then:
        GenericTestExecutionResult testResult = resultsFor()
        testResult.testPath("org.example.MixedTest").onlyRoot()
            .assertChildrenExecuted("included()")
            .assertChildCount(1, 0)
    }

    private String javaLibWithDaemonDiscoveryJupiterTests() {
        return """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()
            }

            test {
                useDaemonSideTestDiscovery = true
            }
        """.stripIndent()
    }
}
