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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ProviderIntegrationTest extends AbstractIntegrationSpec {

    private static File defaultOutputFile
    private static File customOutputFile
    private final static String OUTPUT_FILE_CONTENT = 'Hello World!'

    def setup() {
        defaultOutputFile = file('build/output.txt')
        customOutputFile = file('build/custom.txt')
    }

    def "can create and use provider in task"() {
        given:
        buildFile << customTaskType()
        buildFile << """
            task myTask(type: MyTask)
        """

        when:
        succeeds('myTask')

        then:
        !defaultOutputFile.exists()

        when:
        buildFile << """
             myTask {
                enabled = calculate { true }
                outputFiles = calculate { files("$customOutputFile") }
            }
        """
        succeeds('myTask')

        then:
        !defaultOutputFile.exists()
        customOutputFile.isFile()
        customOutputFile.text == OUTPUT_FILE_CONTENT
    }

    def "can lazily map extension property value to task property"() {
        given:
        buildFile << """
            apply plugin: MyPlugin
            
            pluginConfig {
                enabled = true
                outputFiles = files('$customOutputFile')
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    def extension = project.extensions.create('pluginConfig', MyExtension)
                    
                    project.tasks.create('myTask', MyTask) {
                        enabled = project.calculate { extension.enabled }
                        outputFiles = project.calculate { extension.outputFiles }
                    }
                }
            }

            class MyExtension {
                Boolean enabled
                FileCollection outputFiles
            }
        """
        buildFile << customTaskType()

        when:
        succeeds('myTask')

        then:
        !defaultOutputFile.exists()
        customOutputFile.isFile()
        customOutputFile.text == OUTPUT_FILE_CONTENT
    }

    def "can use provider to define a task dependency"() {
        given:
        buildFile << """
            task producer {
                ext.destFile = file('test.txt')
                outputs.file destFile

                doLast {
                    destFile << '$OUTPUT_FILE_CONTENT'
                }
            }

            def targetFile = calculate { producer.destFile }
            
            targetFile.builtBy producer

            task consumer {
                dependsOn targetFile

                doLast {
                    println targetFile.getValue().text
                }
            }
        """

        when:
        succeeds('consumer')

        then:
        executedTasks.containsAll(':producer', ':consumer')
        outputContains(OUTPUT_FILE_CONTENT)
    }

    static String customTaskType() {
        """
            class MyTask extends DefaultTask {
                private Provider<Boolean> enabled = project.calculate { false }
                private Provider<FileCollection> outputFiles = project.calculate { project.files('$defaultOutputFile') }
                
                @Input
                boolean getEnabled() {
                    enabled.getValue()
                }
                
                void setEnabled(Provider<Boolean> enabled) {
                    this.enabled = enabled
                }
                
                @OutputFiles
                FileCollection getOutputFiles() {
                    outputFiles.getValue()
                }

                void setOutputFiles(Provider<FileCollection> outputFiles) {
                    this.outputFiles = outputFiles
                }

                @TaskAction
                void resolveValue() {
                    if (getEnabled()) {
                        outputFiles.getValue().each {
                            it.text = '$OUTPUT_FILE_CONTENT'
                        }
                    }
                }
            }
        """
    }
}
