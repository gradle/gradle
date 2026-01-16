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

package org.gradle.testing

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ClientSideTestDiscoveryIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    @Override
    TestFramework getTestFramework() {
        return TestFramework.JUNIT_JUPITER
    }

    def "can run tests with client-side discovery"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()
            }
        """.stripIndent()

        file('src/test/java/org/example/NotATest.java') << """
            package org.example;
            public class NotATest {
            }
        """.stripIndent()

        file('src/test/java/org/example/DontRunMe.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            public class DontRunMe {
                @Test
                public void nope() {
                    throw new RuntimeException("I should not be run!");
                }
            }
        """.stripIndent()

        file('src/test/java/org/example/Ok.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            public class Ok {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()

        file('src/test/java/org/example/Ok2.java') << """
            package org.example;
            import org.junit.jupiter.api.*;
            public class Ok2 {
                @Test
                public void ok2() {
                }
            }
        """.stripIndent()

        when:
        run("test", "--tests=Ok*")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.testPath("org.example.Ok").onlyRoot().assertChildCount(1, 0)
        testResult.testPath("org.example.Ok2").onlyRoot().assertChildCount(1, 0)
    }
}
