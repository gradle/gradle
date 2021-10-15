/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.component.BuildableJavaComponent
import org.gradle.api.internal.component.ComponentRegistry
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaPluginIntegrationTest extends AbstractIntegrationSpec {

    def appliesBasePluginsAndAddsConventionObject() {
        given:
        buildFile << """
            apply plugin: 'java'

            task expect {

                def component = project.services.get(${ComponentRegistry.canonicalName}).mainComponent
                assert component instanceof ${BuildableJavaComponent.canonicalName}
                assert component.runtimeClasspath != null
                assert component.compileDependencies == project.configurations.compileClasspath

                def buildTasks = component.buildTasks as List
                doLast {
                    assert buildTasks == [ JavaBasePlugin.BUILD_TASK_NAME ]
                }
            }
        """
        expect:
        succeeds "expect"
    }

    def "exposes outgoing variant for results of each test suite target's test task"() {
        buildFile << '''
            plugins {
                id 'java'
            }

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        /* no-op */
                    }
                }
            }
        '''

        when:
        succeeds':outgoingVariants', '-is'

        then:
        outputContains '''    - Variant : testResultDataElements
       - Attributes
          - org.gradle.category            = documentation
          - org.gradle.dependency.bundling = external
          - org.gradle.docstype            = test-result-data
          - org.gradle.jvm.version         = 17
          - org.gradle.libraryelements     = jar
          - org.gradle.testsuitetype       = test
          - org.gradle.usage               = java-runtime
       - Artifacts
          - build/test-results/test/binary\n'''

        outputContains '''    - Variant : integTestResultDataElements
       - Attributes
          - org.gradle.category            = documentation
          - org.gradle.dependency.bundling = external
          - org.gradle.docstype            = test-result-data
          - org.gradle.jvm.version         = 17
          - org.gradle.libraryelements     = jar
          - org.gradle.testsuitetype       = integTest
          - org.gradle.usage               = java-runtime
       - Artifacts
          - build/test-results/integTest/binary\n'''

    }
}
