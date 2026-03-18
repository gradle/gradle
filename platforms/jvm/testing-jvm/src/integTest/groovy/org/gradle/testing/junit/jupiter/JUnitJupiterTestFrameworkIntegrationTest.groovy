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

import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.testing.AbstractTestFrameworkIntegrationTest
import org.gradle.testing.fixture.JUnitCoverage

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION

class JUnitJupiterTestFrameworkIntegrationTest extends AbstractTestFrameworkIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${JUnitCoverage.LATEST_JUPITER_VERSION}'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            test {
                useJUnitPlatform()
            }
        """
    }

    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.JUNIT_JUPITER
    }

    @Override
    void createPassingFailingTest() {
        file('src/main/java/AppException.java').writelns(
            "public class AppException extends Exception { }"
        )

        file('src/test/java/SomeTest.java') << """
            public class SomeTest {
                @org.junit.jupiter.api.Test
                public void ${failingTestMethodName}() {
                    System.err.println("some error output");
                    org.junit.jupiter.api.Assertions.fail(\"test failure message\");
                }
                @org.junit.jupiter.api.Test
                public void ${passingTestMethodName}() { }
            }
        """
        file('src/test/java/SomeOtherTest.java') << """
            public class SomeOtherTest {
                @org.junit.jupiter.api.Test
                public void ${passingTestMethodName}() { }
            }
        """
    }

    @Override
    void createEmptyProject() {
        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """
    }

    @Override
    void renameTests() {
        def newTest = file("src/test/java/NewTest.java")
        file('src/test/java/SomeOtherTest.java').renameTo(newTest)
        newTest.text = newTest.text.replaceAll("SomeOtherTest", "NewTest")
    }

    @Override
    String getTestTaskName() {
        return "test"
    }

    @Override
    String getPassingTestMethodName() {
        return "pass"
    }

    @Override
    String getFailingTestMethodName() {
        return "fail"
    }
}
