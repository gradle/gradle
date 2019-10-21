/*
 * Copyright 2019 the original author or authors.
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

class JUnitPlatformLoggingIntegrationTest extends JUnitPlatformIntegrationSpec  {

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
        file("src/test/java/pkg/TopLevelClass.java")  << """
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
        file("src/test/java/pkg/TopLevelClass.java")  << """
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

}
