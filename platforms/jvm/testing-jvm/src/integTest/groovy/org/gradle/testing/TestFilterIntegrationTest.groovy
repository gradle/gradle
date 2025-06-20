/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.JUnitCoverage

class TestFilterIntegrationTest extends AbstractIntegrationSpec {
    def "can repeat --tests arg to include multiple class"() {
        when: "running tests with a filter that includes YellowTest and BlueTest"
        executer.withTasks("test", "--tests", "com.example.YellowTest.*", "--tests", "com.example.other.BlueTest.*").run()

        then: "both tests are found and executed"
        new DefaultTestExecutionResult(testDirectory).testClass("com.example.YellowTest").assertTestCount(1, 0, 0)
        new DefaultTestExecutionResult(testDirectory).testClass("com.example.other.BlueTest").assertTestCount(1, 0, 0)
    }

    def "can NOT include multiple classes in --tests filter using comma"() {
        when: "running tests with a filter that includes YellowTest and BlueTest"
        def filter = "com.example.YellowTest.*,com.example.other.BlueTest.*"
        failure = executer.withTasks("test", "--tests", filter).runWithFailure()

        then: "no tests are found and the task fails"
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasCause("No tests found for given includes: [$filter](--tests filter)")
    }

    def "can NOT include multiple classes in --tests filter using pipe"() {
        when: "running tests with a filter that includes YellowTest and BlueTest"
        def filter = "com.example.YellowTest.*|com.example.other.BlueTest.*"
        failure = executer.withTasks("test", "--tests", filter).runWithFailure()

        then: "no tests are found and the task fails"
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasCause("No tests found for given includes: [$filter](--tests filter)")
    }

    def "can NOT include multiple classes in --tests filter using pipe and parens"() {
        when: "running tests with a filter that includes YellowTest and BlueTest"
        def filter = "(com.example.YellowTest.*)|(com.example.other.BlueTest.*)"
        failure = executer.withTasks("test", "--tests", filter).runWithFailure()

        then: "no tests are found and the task fails"
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasCause("No tests found for given includes: [$filter](--tests filter)")
    }

    def setup() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${JUnitCoverage.LATEST_JUPITER_VERSION}'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }

            test {
                useJUnitPlatform()
            }
        """

        addTest("YellowTest", "com.example")
        addTest("BlueTest", "com.example.other")
        addTest("PurpleTest", "com.example.another")
    }

    private void addTest(String name, String pkg) {
        javaFile(
            "src/test/java/" + pkg.replace('.', '/') + "/" + name + ".java",
            """
                package ${pkg};

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                public class ${name} {
                    @Test
                    public void test() {
                        assertTrue(true);
                    }
                }
            """
        )
    }
}
