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
    def setup() {
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            group = 'example'
            version = '0.1'

            testing {
                suites {
                    unitTest {
                        dependencies {
                            implementation 'com.google.guava:guava:30.1.1-jre'
                        }

                        targets {
                            java8 {
                                testTask.configure {
                                    description = "Sample"
                                }
                            }
                        }
                    }
                }
            }
        """
    }

    def "applies base plugins and adds convention object"() {
        expect:
        succeeds "tasks", "dependencies"
        outputContains("Sample")
    }

    def "switch to junit from junit platform"() {
        buildFile << """
            testing {
                suites {
                    unitTest {
                        useJUnit()
                    }

                    integTest {

                    }
                }
            }
        """

        expect:
        succeeds "tasks", "dependencies"
    }

    def "int tests depend on unit tests"() {
        buildFile << """
            testing {
                suites {
                    unitTest {
                        useJUnit()
                    }

                    integTest {
                        targets {
                            all {
                                testTask.configure {
                                    dependsOn unitTest
                                }
                            }
                        }
                    }
                }
            }


        """

        expect:
        succeeds "integTest"
    }
}
