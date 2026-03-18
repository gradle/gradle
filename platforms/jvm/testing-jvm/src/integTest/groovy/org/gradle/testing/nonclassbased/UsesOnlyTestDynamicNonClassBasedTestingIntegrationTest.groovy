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

package org.gradle.testing.nonclassbased

class UsesOnlyTestDynamicNonClassBasedTestingIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest {
    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.USES_ONLY_TEST_RESOURCE_BASED_DYNAMIC]
    }

    def "test-only dynamic resource-based test engine fails with a reasonable error message"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")
                    }
                }
            }
        """

        file("${DEFAULT_DEFINITIONS_LOCATION}/SomeTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
            </tests>
        """

        when:
        fails("test")

        then:
        failureHasCause("Test process encountered an unexpected problem.")
        failureHasCause(~/Could not complete execution for Gradle Test Executor \d+\./)
        failureHasCause("Closest started ancestor 'Test SomeTestSpec.rbt - foo' is not a container. " +
                "This likely means the JUnit Platform TestEngine 'uses-only-test-dynamic-rbt-engine' tried to start a test under a non-container parent.")
    }
}
