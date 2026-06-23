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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.AbstractTestFilteringIntegrationTest
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER

@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterFilteringIntegrationTest extends AbstractTestFilteringIntegrationTest implements JUnitJupiterMultiVersionTest {
    @Issue("https://github.com/gradle/gradle/issues/19808")
    def "nested classes are executed when filtering by class name on the command line"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${MultiVersionIntegrationSpec.version}'
            }

            ${testClassAndCountListener}

            test {
                ${maybeConfigureFilters(withConfiguredFilters)}
            }
        """

        file("src/test/java/SampleTest.java") << sampleClassWithNestedTests
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
        assertExpectedTestsAndCounts(expectedTestsAndCounts as Map<String, Integer>)

        where:
        commandLineFilter                                 | withConfiguredFilters | expectedTestsAndCounts
        'SampleTest'                                      | false                 | ['SampleTest': 1, 'SampleTest$NestedTestClass': 1, 'SampleTest$NestedTestClass$SubNestedTestClass': 4]
        'SampleTest'                                      | true                  | ['SampleTest': 1, 'SampleTest$NestedTestClass': 1, 'SampleTest$NestedTestClass$SubNestedTestClass': 4]
        'SampleTest$NestedTestClass'                      | false                 | ['SampleTest$NestedTestClass': 1, 'SampleTest$NestedTestClass$SubNestedTestClass': 4]
        'SampleTest$NestedTestClass'                      | true                  | ['SampleTest$NestedTestClass': 1, 'SampleTest$NestedTestClass$SubNestedTestClass': 4]
        'SampleTest$NestedTestClass$SubNestedTestClass'   | false                 | ['SampleTest$NestedTestClass$SubNestedTestClass': 4]
        'SampleTest$NestedTestClass$SubNestedTestClass'   | true                  | ['SampleTest$NestedTestClass$SubNestedTestClass': 4]
    }

    @Issue("https://github.com/gradle/gradle/issues/31304")
    def "nested classes are not executed when excluded by class name in test configuration"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${MultiVersionIntegrationSpec.version}'
            }

            ${testClassAndCountListener}

            test {
                filter.excludeTest("${excludeFilter.replace('$', '\\$')}", null)
            }
        """

        file("src/test/java/SampleTest.java") << sampleClassWithNestedTests

        when:
        fails "test"

        then:
        result.assertTaskExecuted(":test")
        assertExpectedTestsAndCounts(expectedTestsAndCounts as Map<String, Integer>)

        where:
        excludeFilter                                       | expectedTestsAndCounts
        'SampleTest'                                        | ['SampleTest$NestedTestClass': 1, 'SampleTest$NestedTestClass$SubNestedTestClass': 4]
        'SampleTest$NestedTestClass'                        | ['SampleTest': 1, 'SampleTest$NestedTestClass$SubNestedTestClass': 4]
        'SampleTest$NestedTestClass$SubNestedTestClass'     | ['SampleTest': 1, 'SampleTest$NestedTestClass': 1]
        'SampleTest*'                                       | [:]
    }

    private void assertExpectedTestsAndCounts(Map<String, Integer> expectedTestsAndCounts) {
        Map<String, Integer> executedClasses = file("build/executed-classes.txt").readLines().collectEntries { it.split(' ', 2).with { [(it[0]): it[1] as Integer] } }
        def executedClassNames = executedClasses.keySet()
        def expectedClassNames = expectedTestsAndCounts.keySet()
        assert executedClassNames == expectedClassNames
        expectedTestsAndCounts.each { className, expectedCount ->
            assert executedClasses[className] == expectedCount
        }
    }

    private static String getSampleClassWithNestedTests() {
        return """
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
    }

    private static String getTestClassAndCountListener() {
        return """
            abstract class TestClassCounter implements BuildService<org.gradle.api.services.BuildServiceParameters.None> {
                def executedClasses = [:].withDefault { 0 }

                Map<String, Integer> getExecutedClasses() {
                    return executedClasses
                }
            }

            class TestClassListener implements TestListener {
                private Provider<TestClassCounter> testClassCounter

                @Inject
                public TestClassListener(Provider<TestClassCounter> testClassCounter) {
                    this.testClassCounter = testClassCounter
                }

                @Override
                void beforeSuite(TestDescriptor descriptor) { }

                @Override
                void afterSuite(TestDescriptor descriptor, TestResult result) { }

                @Override
                void beforeTest(TestDescriptor descriptor) { }

                @Override
                void afterTest(TestDescriptor descriptor, TestResult result) {
                    if (descriptor.className) {
                        testClassCounter.get().executedClasses[descriptor.className]++
                    }
                }
            }

            def testClassCounterService = project.getGradle().getSharedServices().registerIfAbsent("testClassCounter", TestClassCounter)

            test {
                usesService(testClassCounterService)
                test.addTestListener(new TestClassListener(testClassCounterService))
            }

            def writeTestCounts = tasks.register("writeTestCounts") {
                def outputFile = project.layout.buildDirectory.file("executed-classes.txt")
                usesService(testClassCounterService)
                doLast {
                    outputFile.get().asFile.text = testClassCounterService.get().executedClasses.collect { k, v -> "\${k} \${v}" }.join("\\n")
                }
            }

            test.finalizedBy(writeTestCounts)
        """
    }

    String maybeConfigureFilters(boolean withConfiguredFilters) {
        return withConfiguredFilters ? """
            filter {
                excludeTestsMatching "*Something*"
            }
        """ : ""
    }
}
