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

package org.gradle.api.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.ToBeImplemented
import spock.lang.Unroll

class NestedInputIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    def "Nested #type.simpleName input adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }
            
            class NestedBeanWithInput {
                @Input${fileType}
                ${type.name} input
            }
            
            class TaskGeneratingFile extends DefaultTask {
                @Output${fileType}
                ${type.name} outputFile = newOutput${fileType}()
                
                @TaskAction
                void doStuff() {
                    if (outputFile instanceof DirectoryProperty) {
                        outputFile.file('output.txt').get().getAsFile().text = "Hello"
                    } else {
                        outputFile.getAsFile().get().text = "Hello"
                    }
                }
            }
            
            task generator(type: TaskGeneratingFile) {
                outputFile.set(project.layout.buildDirectory.${fileType == 'Directory' ? 'dir' : 'file'}('output'))
            }
            
            task consumer(type: TaskWithNestedProperty) {
                bean = new NestedBeanWithInput(input: newInput${fileType}())
                bean.input.set(generator.outputFile)
            }
        """

        when:
        run 'consumer'

        then:
        executedAndNotSkipped(':generator', ':consumer')

        where:
        fileType    | type
        'File'      | RegularFileProperty
        'Directory' | DirectoryProperty
    }

    def "Nested FileCollection input adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }
            
            class NestedBeanWithInput {
                @InputFiles
                FileCollection input
            }
            
            class TaskGeneratingFile extends DefaultTask {
                @OutputFile
                RegularFileProperty outputFile = newOutputFile()
                
                @TaskAction
                void doStuff() {
                    outputFile.getAsFile().get().text = "Hello"
                }
            }
            
            task generator(type: TaskGeneratingFile) {
                outputFile.set(project.layout.buildDirectory.file('output'))
            }
            
            task consumer(type: TaskWithNestedProperty) {
                bean = new NestedBeanWithInput(input: files(generator.outputFile))
            }
        """

        when:
        run 'consumer'

        then:
        executedAndNotSkipped(':generator', ':consumer')
    }

    @ToBeImplemented
    def "Nested input using output file property of different task adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }
            
            class NestedBeanWithInput {
                @InputFile
                RegularFileProperty file
            }
            
            class TaskGeneratingFile extends DefaultTask {
                @OutputFile
                RegularFileProperty outputFile = newOutputFile()
                
                @TaskAction
                void doStuff() {
                    outputFile.getAsFile().get().text = "Hello"
                }
            }
            
            task generator(type: TaskGeneratingFile) {
                outputFile.set(project.layout.buildDirectory.file('output'))
            }
            
            task consumer(type: TaskWithNestedProperty) {
                bean = new NestedBeanWithInput(file: generator.outputFile)
            }
        """

        when:
        run 'consumer'

        then:
        // Should have been executed
        notExecuted(':generator')
        // Should have been executed
        skipped(':consumer')
    }

    def "Changing nested inputs during execution time is detected"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask {
                @Nested
                Object bean
                
                @OutputFile
                RegularFileProperty outputFile = newOutputFile()
                
                @TaskAction
                void writeInputToFile() {
                    outputFile.getAsFile().get().text = bean
                }
            }
            
            class NestedBeanWithInput {
                @Input
                String firstInput
                
                String toString() {
                    firstInput
                }
            }
            
            class NestedBeanWithOtherInput {
                @Input
                String secondInput
                
                String toString() {
                    secondInput
                }
            }
            
            task taskWithNestedProperty(type: TaskWithNestedProperty) {
                firstInput = new NestedBeanWithInput(firstInput: project.findProperty('firstInput'))
                outputFile.set(project.layout.buildDirectory.file('output.txt'))
            }
            
            task configureTask {
                doLast {
                    taskWithNestedProperty.bean = new NestedBeanWithOtherInput(secondInput: project.findProperty('secondInput'))
                }
            }
            
            taskWithNestedProperty.dependsOn(configureTask)
        """

        def task = ':taskWithNestedProperty'
        when:
        run task, '-PfirstInput=first', '-PsecondInput=second'

        then:
        executedAndNotSkipped(task)
        def outputFile = file('build/output.txt')
        outputFile.text == 'second'

        when:
        run task, '-PfirstInput=different', '-PsecondInput=second'

        then:
        skipped(task)
        outputFile.text == 'second'

        when:
        run task, '-PfirstInput=different', '-PsecondInput=secondSecond'

        then:
        executedAndNotSkipped(task)
        outputFile.text == 'secondSecond'
    }

}
