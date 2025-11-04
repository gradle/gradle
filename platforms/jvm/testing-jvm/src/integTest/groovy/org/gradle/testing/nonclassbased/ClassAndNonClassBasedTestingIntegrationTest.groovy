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

class ClassAndNonClassBasedTestingIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest {
    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.RESOURCE_AND_CLASS_BASED]
    }

    def "can use same engine for class and resource-based testing"() {
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
                        testDefinitionDirs.from(project.layout.projectDirectory.file("src/test/definitions"))

                        options {
                            excludeEngines("junit-jupiter")
                        }
                    }
                }
            }
        """

        writeTestClasses()
        writeTestDefinitions()

        when:
        succeeds("test", "--info")

        then:
        classBasedTestsExecuted()
        nonClassBasedTestsExecuted()
    }
}
