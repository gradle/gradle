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

import static org.gradle.util.Matchers.matchesRegexp

/**
 * Tests that exercise and demonstrate a broken Non-Class-Based Testing Engine that fails during discovery.
 */
class FailsDiscoveryNonClassBasedTestingIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest {
    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.FAILS_DISCOVERY_RESOURCE_BASED]
    }

    def "engine failing during discovery is handled gracefully"() {
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

        writeTestDefinitions()

        when:
        fails("test", "-S")

        then:
        failure.assertThatCause(matchesRegexp(/Could not complete execution for Gradle Test Executor \d+\./))
        failure.assertHasErrorOutput("Caused by: java.lang.RuntimeException: Test discovery failed")
    }

    def "engine failing during discovery is not started if no test def dirs specified"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}
            }
        """

        writeTestDefinitions()

        when:
        succeeds("test")

        then:
        result.assertTaskSkipped(":test")
    }
}
