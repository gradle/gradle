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

import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestReportGenerator
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.AbstractTestTaskIntegrationTest
import org.gradle.testing.fixture.JUnitCoverage

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER

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

    def "test task works for deeply nested tests"() {
        given:
        // Generate enough @Nested levels so the total readable path exceeds MAX_PATH_LENGTH (1024)
        def depth = 10
        def classNames = (1..depth).collect { "ThisIsATestAtLevel" + it }
        def innerClasses = ""
        for (int i = classNames.size() - 1; i >= 0; i--) {
            if (i == classNames.size() - 1) {
                innerClasses = """
                    @org.junit.jupiter.api.Nested
                    public class ${classNames[i]} {
                        @org.junit.jupiter.api.Test
                        public void test() {
                            org.junit.jupiter.api.Assertions.assertEquals(1, 1);
                        }
                    }
                """
            } else {
                innerClasses = """
                    @org.junit.jupiter.api.Nested
                    public class ${classNames[i]} {
                        ${innerClasses}
                    }
                """
            }
        }

        file("src/test/java/DeepNestedTest.java") << """
            public class DeepNestedTest {
                ${innerClasses}
            }
        """.stripIndent()

        when:
        succeeds 'test'

        then:
        def reportDir = file("build/reports/tests/test/")
        def htmlFiles = reportDir.allDescendants().findAll { it.endsWith(".html") && it != "index.html" }
        htmlFiles.every { it.length() <= GenericHtmlTestReportGenerator.MAX_PATH_LENGTH }
    }
}
