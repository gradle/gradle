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


import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.AbstractTestTaskIntegrationTest
import org.gradle.testing.fixture.JUnitCoverage
import spock.lang.Issue

@TargetCoverage({ JUnitCoverage.JUNIT_JUPITER })
class JUnitJupiterTestTaskIntegrationTest extends AbstractTestTaskIntegrationTest implements JUnitJupiterMultiVersionTest {
    @Override
    String getStandaloneTestClass() {
        return testClass('MyTest')
    }

    @Override
    String testClass(String className) {
        return """
            import org.junit.jupiter.api.*;

            public class $className {
               @Test
               @Tag("MyTest\$Fast")
               public void fastTest() {
                  System.out.println(System.getProperty("java.version"));
                  Assertions.assertEquals(1,1);
               }

               @Test
               @Tag("MyTest\$Slow")
               public void slowTest() {
                  System.out.println(System.getProperty("java.version"));
                  Assertions.assertEquals(1,1);
               }
            }
        """.stripIndent()
    }

    @Issue("https://github.com/gradle/gradle/issues/36996")
    def "test task can write report for deeply nested test classes"() {
        given:
        def packageName = "org.gradle.testing.junit.jupiter.deeply.nested"
        def className = packageName + ".FooBarQuxAndMoreWithExceedinglyLongPhrasesTest"
        file("src/test/java/${className.replace('.', '/')}.java") << """
            package ${packageName};

            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;

            public class FooBarQuxAndMoreWithExceedinglyLongPhrasesTest {
                @Nested
                public class ThisIsAVeryLongClassName {
                    @Nested
                    public class ThisIsAVeryLongClassName1 {
                        @Nested
                        public class ThisIsAVeryLongClassName2 {
                            @Nested
                            public class ThisIsAVeryLongClassName3 {
                                @Nested
                                public class ThisIsAVeryLongClassName4 {
                                    @Nested
                                    public class ThisIsAVeryLongClassName5 {
                                        @Nested
                                        public class ThisIsAVeryLongClassName6 {
                                            @Test
                                            public void thisIsAlsoARatherLongMethodName() {
                                                assertEquals(1, 1);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        when:
        succeeds 'test'

        then:
        def results = resultsFor()
        def classes = [
            className,
            "ThisIsAVeryLongClassName",
            "ThisIsAVeryLongClassName1",
            "ThisIsAVeryLongClassName2",
            "ThisIsAVeryLongClassName3",
            "ThisIsAVeryLongClassName4",
            "ThisIsAVeryLongClassName5",
            "ThisIsAVeryLongClassName6"
        ]
        List<String> segments = new ArrayList<>()
        for (int i = 0; i < classes.size(); i++) {
            segments.add(classes.subList(0, i + 1).join('$'))
        }
        results.assertTestPathsExecuted(
            ':' + segments.join(':') + ":thisIsAlsoARatherLongMethodName()"
        )
    }

    def "test task cannot write report for extremely deeply nested tests"() {
        given:
        def packageName = "org.gradle.testing.junit.jupiter.deeply.nested"
        def className = packageName + ".DeepDynamicTest"
        file("src/test/java/${className.replace('.', '/')}.java") << """
            package ${packageName};

            import org.junit.jupiter.api.DynamicNode;
            import org.junit.jupiter.api.DynamicContainer;
            import org.junit.jupiter.api.DynamicTest;
            import org.junit.jupiter.api.TestFactory;

            import java.util.stream.Stream;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            public class DeepDynamicTest {
                @TestFactory
                Stream<DynamicNode> deeplyNestedDynamicTests() {
                    DynamicNode innermost = DynamicTest.dynamicTest("thisIsTheInnermostTest", () -> assertEquals(1, 1));
                    DynamicNode current = innermost;
                    for (int i = 149; i >= 0; i--) {
                        final DynamicNode child = current;
                        current = DynamicContainer.dynamicContainer("Test container " + i, Stream.of(child));
                    }
                    return Stream.of(current);
                }
            }
        """

        when:
        fails 'test'

        then:
        def baseReportPath = file("build/reports/tests/test").absolutePath
        failureHasCause("Could not generate test report to '${baseReportPath}'.")
        failureCauseContains(
            "Cannot shrink report path below required limit. Path that could not be shrunk (relative to the report directory): "
                // Validate a small sample of the tests, but not the full error.
                + className + "/deeplyNestedDynamicTests()/deeplyNestedDynamicTests()[1]/"
        )
        failure.assertHasResolution("Use a shorter report directory path.")
        failure.assertHasResolution("Reduce nesting in your tests.")
        failure.assertHasResolution("Disable the HTML report for this task.")
    }
}
