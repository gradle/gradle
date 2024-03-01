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

import spock.lang.Issue

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
}
