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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TestSuitesPluginIntegrationTest extends AbstractIntegrationSpec {
    def "applies base plugins and adds convention object"() {
        given:
        buildFile << """
            apply plugin: org.gradle.api.plugins.TestSuitePlugin

            testing {
                testSuites {
                    unitTests {
                        targets {
                            getByName("java8").configure { // Needed for Binary Collection, magic in NDOC which makes this work as just the name and closure
                                testTask.configure {
                                    description = "Sample"
                                }
                            }
                        }
                    }
                }
            }

        """
        expect:
        succeeds "tasks"
        outputContains("Sample")

    }
}
