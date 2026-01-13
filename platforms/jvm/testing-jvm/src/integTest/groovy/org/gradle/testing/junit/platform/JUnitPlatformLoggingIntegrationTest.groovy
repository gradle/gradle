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

import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Issue

import static org.hamcrest.Matchers.equalTo

class JUnitPlatformLoggingIntegrationTest extends JUnitPlatformIntegrationSpec {

    @Override
    def setup() {
        buildFile << """
            test {
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """
    }

    def "should log display names if present"() {
        given:
        file("src/test/java/pkg/TopLevelClass.java") << """
            package pkg;
            import org.junit.jupiter.api.DisplayName;
            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;

            @DisplayName("Class level display name")
            public class TopLevelClass {

                @Nested
                @DisplayName("Nested class display name")
                public class NestedClass {

                    @Test
                    @DisplayName("Nested test method display name")
                    public void nestedTestMethod() {
                    }
                }

                @Test
                @DisplayName("Method display name")
                public void testMethod() {
                }
            }
         """

        when:
        run("test")

        then:
        outputContains("Class level display name > Method display name")
        outputContains("Class level display name > Nested class display name > Nested test method display name")
    }

    def "should fall back to plain name if no display names present"() {
        given:
        file("src/test/java/pkg/TopLevelClass.java") << """
            package pkg;

            import org.junit.jupiter.api.DisplayName;
            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;

            public class TopLevelClass {

                @Nested
                public class NestedClass {

                    @Test
                    public void nestedTestMethod() {
                    }
                }

                @Test
                public void testMethod() {
                }
            }
         """

        when:
        run("test")

        then:
        outputContains("TopLevelClass > testMethod()")
        outputContains("TopLevelClass > NestedClass > nestedTestMethod()")
    }

    @Issue("https://github.com/gradle/gradle/issues/5975")
    def "should log display names for dynamically created tests"() {
        given:
        file("src/test/java/org/gradle/JUnitJupiterDynamicTest.java") << """
            package org.gradle;
            import org.junit.jupiter.api.DynamicTest;
            import org.junit.jupiter.api.TestFactory;
            import java.util.stream.IntStream;
            import java.util.stream.Stream;
            import static org.junit.jupiter.api.Assertions.*;
            import static org.junit.jupiter.api.DynamicTest.dynamicTest;
            public class JUnitJupiterDynamicTest {
                @TestFactory
                Stream<DynamicTest> streamOfTests() {
                    return IntStream.of(2, 4, 5)
                        .mapToObj(v -> dynamicTest(v + " is even", () -> assertEquals(0, v % 2)));
                }
            }
        """

        when:
        runAndFail("test")

        then:
        def parentEventPath = "JUnitJupiterDynamicTest > streamOfTests()"
        outputContains("${parentEventPath} > 2 is even PASSED")
        outputContains("${parentEventPath} > 4 is even PASSED")
        outputContains("${parentEventPath} > 5 is even FAILED")
    }

    @Issue("https://github.com/gradle/gradle/issues/36150")
    def "should log display names for dynamically created tests with custom sources"() {
        given:
        file("src/test/java/org/gradle/JUnitJupiterDynamicTest.java") << """
            package org.gradle;

            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.DynamicNode;
            import org.junit.jupiter.api.DynamicTest;
            import org.junit.jupiter.api.TestFactory;
            import org.junit.jupiter.api.function.Executable;

            import java.net.URI;
            import java.nio.file.Paths;
            import java.util.stream.Stream;

            public class JUnitJupiterDynamicTest {
                @TestFactory
                Stream<DynamicTest> testsWithCustomSource() {
                    Executable testBody = () -> {
                        // Hack to get the test path HTML to render, otherwise it would be left in the class.
                        // We should adjust GenericTestExecutionResult to not require this.
                        System.out.println("Executing dynamic test with custom source");
                        Assertions.assertTrue(true);
                    };
                    return Stream.of(
                        DynamicTest.dynamicTest(
                            "directory",
                            Paths.get(".").toUri(),
                            testBody),
                        DynamicTest.dynamicTest(
                            "file",
                            Paths.get("my-file.txt").toUri(),
                            testBody),
                        DynamicTest.dynamicTest(
                            "method",
                            URI.create("method:org.gradle.JUnitJupiterDynamicTest#method()"),
                            testBody),
                        DynamicTest.dynamicTest(
                            "class",
                            URI.create("class:org.gradle.JUnitJupiterDynamicTest?line=36"),
                            testBody),
                        DynamicTest.dynamicTest(
                            "generic",
                            URI.create("https://www.example.com"),
                            testBody),
                        DynamicTest.dynamicTest(
                            "classpath",
                            URI.create("classpath:/resource.txt"),
                            testBody)
                    );
                }

                private static void method() {
                }
            }
        """

        when:
        succeeds("test")

        then:
        def parentEventPath = "JUnitJupiterDynamicTest > testsWithCustomSource()"
        outputContains("${parentEventPath} > directory PASSED")
        outputContains("${parentEventPath} > file PASSED")
        outputContains("${parentEventPath} > method PASSED")
        outputContains("${parentEventPath} > class PASSED")
        outputContains("${parentEventPath} > generic PASSED")
        outputContains("${parentEventPath} > classpath PASSED")

        def testResults = resultsFor('tests/test', GenericTestExecutionResult.TestFramework.CUSTOM)
        testResults.assertTestPathsExecuted(
            ":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[1]",
            ":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[2]",
            ":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[3]",
            ":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[4]",
            ":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[5]",
            ":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[6]",
        )
        testResults.testPath(":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[1]").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertDisplayName(equalTo("directory"))
        testResults.testPath(":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[2]").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertDisplayName(equalTo("file"))
        testResults.testPath(":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[3]").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertDisplayName(equalTo("method"))
        testResults.testPath(":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[4]").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertDisplayName(equalTo("class"))
        testResults.testPath(":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[5]").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertDisplayName(equalTo("generic"))
        testResults.testPath(":org.gradle.JUnitJupiterDynamicTest:testsWithCustomSource():testsWithCustomSource()[6]").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertDisplayName(equalTo("classpath"))
    }
}
