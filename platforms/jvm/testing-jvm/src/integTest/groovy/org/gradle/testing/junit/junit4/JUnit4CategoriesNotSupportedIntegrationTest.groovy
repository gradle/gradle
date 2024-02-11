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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult

import static org.hamcrest.CoreMatchers.startsWith

class JUnit4CategoriesNotSupportedIntegrationTest extends AbstractSampleIntegrationTest {

    def "test task fails if categories not supported"() {
        given:
        file('src/test/java/org/gradle/SomeTest.java') << """
            package org.gradle;

            import org.junit.Test;

            public class SomeTest {
                @Test
                public void ok() {
                }

                public void helpermethod() {
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}

            dependencies {
                testImplementation "junit:junit:4.4"
            }

            test {
                useJUnit {
                    includeCategories 'org.gradle.CategoryA'
                    excludeCategories 'org.gradle.CategoryC'
                }
            }
        """

        when:
        fails('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("org.gradle.SomeTest").assertTestFailed("initializationError", startsWith("org.gradle.api.GradleException: JUnit Categories defined but declared JUnit version does not support Categories."))
    }
}
