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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution

class JavaPluginIntegrationTest extends AbstractIntegrationSpec {
    @ToBeFixedForInstantExecution(because = "Task.getProject() during execution")
    def appliesBasePluginsAndAddsConventionObject() {
        given:
        buildFile << """
            apply plugin: 'java'
            
            task expect {
                doLast {
                    def component = project.services.get(${ComponentRegistry.canonicalName}).mainComponent
                    
                    assert component instanceof ${BuildableJavaComponent.canonicalName}
                    assert component.buildTasks as List == [ JavaBasePlugin.BUILD_TASK_NAME ]
                    assert component.runtimeClasspath != null
                    assert component.compileDependencies == project.configurations.compileClasspath
                }
            }
        """
        expect:
        succeeds "expect"
    }

    def "jar task is created lazily"() {
        buildFile << """
            apply plugin: 'java'

            tasks.named('jar').configure {
                println "jar task created"
            }
            
            task printArtifacts {
                doLast {
                    configurations.runtime.artifacts.files.each { println it }
                }
            }
        """

        when:
        succeeds("printArtifacts")

        then:
        result.groupedOutput.task(':printArtifacts').output.contains("jar task created")
    }
}
