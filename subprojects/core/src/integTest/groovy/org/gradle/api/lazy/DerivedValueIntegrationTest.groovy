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

package org.gradle.api.lazy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DerivedValueIntegrationTest extends AbstractIntegrationSpec {

    def "can create and use derived value in task"() {
        given:
        buildFile << customTaskType()
        buildFile << """
            task myTask(type: MyTask)
        """

        when:
        succeeds('myTask')

        then:
        outputContains('Enabled: false')

        when:
        buildFile << """
             myTask.enabled = project.calculate { true }
        """
        succeeds('myTask')

        then:
        outputContains('Enabled: true')
    }

    def "can lazily map extension property value to task property"() {
        given:
        buildFile << """
            apply plugin: MyPlugin
            
            pluginConfig {
                enabled = true
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    def extension = project.extensions.create('pluginConfig', MyExtension)
                    
                    project.tasks.create('myTask', MyTask) {
                        enabled = project.calculate { extension.enabled }
                    }
                }
            }

            class MyExtension {
                String enabled
            }
        """
        buildFile << customTaskType()
    }

    static String customTaskType() {
        """
            class MyTask extends DefaultTask {
                private DerivedValue<Boolean> enabled = DerivedValueFactory.newDerivedValue(false)
                
                @Input
                boolean getEnabled() {
                    enabled.getValue()
                }
                
                void setEnabled(DerivedValue<Boolean> enabled) {
                    this.enabled = enabled
                }
                
                @TaskAction
                void resolveDerivedValue() {
                    logger.quiet "Enabled: \${getEnabled()}"
                }
            }
        """
    }
}
