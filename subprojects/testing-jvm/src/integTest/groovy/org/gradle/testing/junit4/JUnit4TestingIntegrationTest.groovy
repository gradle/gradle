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

package org.gradle.testing.junit4

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.AbstractTestingIntegrationTest
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST

@TargetCoverage({ JUNIT_4_LATEST })
class JUnit4TestingIntegrationTest extends AbstractTestingIntegrationTest implements JUnit4MultiVersionTest {
    @Issue("https://issues.gradle.org/browse/GRADLE-2313")
    def "can clean test after extracting class file with #framework"() {
        when:
        buildFile << """
            apply plugin: "java"
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()
        and:
        file("src/test/java/SomeTest.java") << """
            public class SomeTest extends org.junit.runner.Result {
            }
        """.stripIndent()
        then:
        succeeds "clean", "test"

        and:
        file("build/tmp/test").exists() // ensure we extracted classes
    }

    def "test thread name is reset after test execution"() {
        when:
        buildFile << """
            apply plugin: "java"
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        and:
        file("src/test/java/SomeTest.java") << threadNameCheckTest("SomeTest")
        file("src/test/java/AnotherTest.java") << threadNameCheckTest("AnotherTest")

        then:
        succeeds "clean", "test"

        and:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("SomeTest").assertTestPassed("checkThreadName")
        result.testClass("AnotherTest").assertTestPassed("checkThreadName")
    }

    private String threadNameCheckTest(String className) {
        return """
            ${testFrameworkImports}

            public class ${className} {
                @Test
                public void checkThreadName() {
                    assertEquals("Test worker", Thread.currentThread().getName());
                    Thread.currentThread().setName(getClass().getSimpleName());
                }
            }
        """.stripIndent()
    }
}
