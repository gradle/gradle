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

import org.gradle.api.tasks.testing.TestResult
import org.gradle.testing.junit.platform.JUnitPlatformIntegrationSpec
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION

@Issue("https://github.com/gradle/gradle/issues/35833")
class JUnitJupiterDryRunReportIntegrationTest extends JUnitPlatformIntegrationSpec {
    @Override
    String getJupiterVersion() {
        return LATEST_JUPITER_VERSION
    }

    def setup() {
        buildFile << """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter-params:${LATEST_JUPITER_VERSION}'
            }
        """
    }

    def "@ParameterizedTest methods are reported as skipped under --test-dry-run"() {
        given:
        file('src/test/java/org/gradle/ParamTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            public class ParamTest {
                @ParameterizedTest
                @ValueSource(ints = {1, 2, 3})
                public void parameterized(int x) {
                    throw new RuntimeException("dry-run must not execute parameterized invocations");
                }
            }
        '''.stripIndent()

        when:
        succeeds("test", "--test-dry-run")

        then:
        def result = resultsFor()
        result.testPath("org.gradle.ParamTest").onlyRoot()
            .assertChildCount(1, 0)
            .assertChildrenSkipped("parameterized(int)")
        result.testPath(":org.gradle.ParamTest:parameterized(int)").onlyRoot()
            .assertHasResult(TestResult.ResultType.SKIPPED)
    }

    def "@ParameterizedTest inside @Nested classes are reported as skipped under --test-dry-run"() {
        given:
        file('src/test/java/org/gradle/OuterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            public class OuterTest {
                @Nested
                public class Inner {
                    @ParameterizedTest
                    @ValueSource(ints = {1, 2})
                    public void inner(int x) {
                        throw new RuntimeException("dry-run must not execute nested parameterized");
                    }
                }
            }
        '''.stripIndent()

        when:
        succeeds("test", "--test-dry-run")

        then:
        def result = resultsFor()
        result.testPath(":org.gradle.OuterTest:Inner:inner(int)").onlyRoot()
            .assertHasResult(TestResult.ResultType.SKIPPED)
    }

    def "@TestFactory methods are reported as skipped under --test-dry-run"() {
        given:
        file('src/test/java/org/gradle/FactoryTest.java') << '''
            package org.gradle;

            import java.util.stream.Stream;
            import org.junit.jupiter.api.DynamicTest;
            import org.junit.jupiter.api.TestFactory;

            public class FactoryTest {
                @TestFactory
                public Stream<DynamicTest> dynamic() {
                    throw new RuntimeException("dry-run must not invoke factory");
                }
            }
        '''.stripIndent()

        when:
        succeeds("test", "--test-dry-run")

        then:
        def result = resultsFor()
        result.testPath("org.gradle.FactoryTest").onlyRoot()
            .assertChildCount(1, 0)
            .assertChildrenSkipped("dynamic()")
    }

    def "regular @Test methods remain reported as skipped under --test-dry-run"() {
        given:
        file('src/test/java/org/gradle/PlainTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;

            public class PlainTest {
                @Test
                public void plain() {
                    throw new RuntimeException("dry-run must not execute");
                }
            }
        '''.stripIndent()

        when:
        succeeds("test", "--test-dry-run")

        then:
        def result = resultsFor()
        result.testPath("org.gradle.PlainTest").onlyRoot()
            .assertChildCount(1, 0)
            .assertChildrenSkipped("plain()")
    }
}
