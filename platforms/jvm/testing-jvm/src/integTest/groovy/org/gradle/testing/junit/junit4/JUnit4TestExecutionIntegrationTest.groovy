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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.testing.junit.AbstractJUnitTestExecutionIntegrationTest

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4
import static org.hamcrest.CoreMatchers.containsString

@TargetCoverage({ JUNIT_4 })
class JUnit4TestExecutionIntegrationTest extends AbstractJUnitTestExecutionIntegrationTest implements JUnit4MultiVersionTest {
    @Override
    String getJUnitVersionAssertion() {
        return "assertEquals(\"${version}\", new org.junit.runner.JUnitCore().getVersion());"
    }

    @Override
    TestClassExecutionResult assertFailedToExecute(TestExecutionResult testResult, String testClassName) {
        return testResult.testClass(testClassName)
            .assertTestFailed("initializationError", containsString('ClassFormatError'))
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
