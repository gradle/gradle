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

package org.gradle.testing.junit.jupiter

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.AbstractTestFilteringIntegrationTest
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER

@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterFilteringIntegrationTest extends AbstractTestFilteringIntegrationTest implements JUnitJupiterMultiVersionTest {
    @Issue("https://github.com/gradle/gradle/issues/19808")
    def "nested classes are executed when filtering by class name"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
                testImplementation 'org.junit.jupiter:junit-jupiter:${version}'
            }
            test {
                ${configureTestFramework} {
                    useJUnitPlatform()
                }
                ${maybeConfigureFilters(withConfiguredFilters)}
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
        fails "test", "--tests", commandLineFilter

        then:
        result.assertTaskExecuted(":test")
        def testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted(expectedTests as String[])
        testResult.testClass('SampleTest$NestedTestClass$SubNestedTestClass').assertTestCount(4, 4, 0)

        where:
        commandLineFilter                                   | withConfiguredFilters | expectedTests
        "SampleTest"                                        | false                 | ['SampleTest', 'SampleTest$NestedTestClass', 'SampleTest$NestedTestClass$SubNestedTestClass']
        "SampleTest"                                        | true                  | ['SampleTest', 'SampleTest$NestedTestClass', 'SampleTest$NestedTestClass$SubNestedTestClass']
        "SampleTest\$NestedTestClass"                       | false                 | ['SampleTest$NestedTestClass', 'SampleTest$NestedTestClass$SubNestedTestClass']
        "SampleTest\$NestedTestClass"                       | true                  | ['SampleTest$NestedTestClass', 'SampleTest$NestedTestClass$SubNestedTestClass']
        "SampleTest\$NestedTestClass\$SubNestedTestClass"   | false                 | ['SampleTest$NestedTestClass$SubNestedTestClass']
        "SampleTest\$NestedTestClass\$SubNestedTestClass"   | true                  | ['SampleTest$NestedTestClass$SubNestedTestClass']
    }

    String maybeConfigureFilters(boolean withConfiguredFilters) {
        return withConfiguredFilters ? """
            filter {
                excludeTestsMatching "*Something*"
            }
        """ : ""
    }
}
