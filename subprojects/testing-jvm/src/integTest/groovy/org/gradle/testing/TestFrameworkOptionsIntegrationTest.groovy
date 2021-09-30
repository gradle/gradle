/*
 * Copyright 2021 the original author or authors.
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


import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult

class TestFrameworkOptionsIntegrationTest extends AbstractIntegrationSpec {
    def "can change the test framework multiple times before execution"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }
            ${mavenCentralRepository()}
            dependencies { testImplementation "junit:junit:4.13" }

            test {
                useJUnit()
                options {
                    assert it instanceof ${JUnitOptions.canonicalName}
                }
                useJUnitPlatform()
                options {
                    assert it instanceof ${JUnitPlatformOptions.canonicalName}
                }
                useJUnit()
            }
        """

        and:
        file("src/test/java/SomeTest.java") << """
            import org.junit.*;

            public class SomeTest {
                @Test public void foo() {
                }
            }
        """

        when:
        run "test"

        then:
        executedAndNotSkipped(":test")
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("SomeTest")
    }
}
