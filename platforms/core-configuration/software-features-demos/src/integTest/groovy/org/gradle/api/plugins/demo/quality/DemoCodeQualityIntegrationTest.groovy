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

package org.gradle.api.plugins.demo.quality

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.IsEmbeddedExecutor)
class DemoCodeQualityIntegrationTest extends AbstractIntegrationSpec {
    def "can apply the demoSourceQuality feature to Java"() {
        given:
        settingsFile << """
            plugins {
                id 'java-ecosystem'
                id 'demo-quality-ecosystem'
            }
        """
        file('build.gradle.dcl') << """
            javaLibrary {
                sources {
                    javaSources("main") {
                        demoSourceQuality {
                            ignoreFailures = true
                        }
                    }
                    javaSources("integTest") {
                        demoSourceQuality {
                            ignoreFailures = true
                        }
                    }
                }
            }
        """

        when:
        run "tasks"

        then:
        outputContains("checkMainDemoSourceQuality")
        outputContains("checkIntegTestDemoSourceQuality")
        outputDoesNotContain("checkTestDemoSourceQuality")
    }

    def "can apply the demoBytecodeQuality feature to Java"() {
        given:
        settingsFile << """
            plugins {
                id 'java-ecosystem'
                id 'demo-quality-ecosystem'
            }
        """
        file('build.gradle.dcl') << """
            javaLibrary {
                sources {
                    javaSources("main") {
                        demoBytecodeQuality {
                            ignoreFailures = true
                        }
                    }
                    javaSources("integTest") {
                        demoBytecodeQuality {
                            ignoreFailures = true
                        }
                    }
                }
            }
        """

        when:
        run "tasks"

        then:
        outputContains("checkMainDemoBytecodeQuality")
        outputContains("checkIntegTestDemoBytecodeQuality")
        outputDoesNotContain("checkTestDemoBytecodeQuality")
    }


    def "can apply both demoSourceQuality and demoBytecodeQuality features to Groovy"() {
        given:
        settingsFile << """
            plugins {
                id 'groovy-ecosystem'
                id 'demo-quality-ecosystem'
            }
        """
        file('build.gradle.dcl') << """
            groovyLibrary {
                sources {
                    groovySources("main") {
                        demoSourceQuality {
                            ignoreFailures = true
                        }
                        demoBytecodeQuality {
                            ignoreFailures = true
                        }
                    }
                }
            }
        """

        when:
        run "tasks"

        then:
        outputContains("checkMainDemoSourceQuality")
        outputContains("checkMainDemoBytecodeQuality")
    }
}
