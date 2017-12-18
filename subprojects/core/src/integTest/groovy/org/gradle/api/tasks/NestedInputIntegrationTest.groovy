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
    def "nested #type.simpleName input adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }
            
            class NestedBeanWithInput {
                @Input${kind}
                ${type.name} input
            }
            
            class GeneratorTask extends DefaultTask {
                @Output${kind}
                ${type.name} output = newOutput${kind}()
                
                @TaskAction
                void doStuff() {
                    output${generatorAction}
                }
            }
            
            task generator(type: GeneratorTask) {
                output.set(project.layout.buildDirectory.${kind == 'Directory' ? 'dir' : 'file'}('output'))
            }
            
            task consumer(type: TaskWithNestedProperty) {
                bean = new NestedBeanWithInput(input: newInput${kind}())
                bean.input.set(generator.output)
            }
        """

        when:
        run 'consumer'

        then:
        executedAndNotSkipped(':generator', ':consumer')

        where:
        kind        | type                 | generatorAction
        'File'      | RegularFileProperty  | '.getAsFile().get().text = "Hello"'
        'Directory' | DirectoryProperty    | '''.file('output.txt').get().getAsFile().text = "Hello"'''
    }

    def "nested FileCollection input adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }
            
            class NestedBeanWithInput {
                @InputFiles
                FileCollection input
            }
            
            class GeneratorTask extends DefaultTask {
                @OutputFile
                RegularFileProperty outputFile = newOutputFile()
                
                @TaskAction
                void doStuff() {
                    outputFile.getAsFile().get().text = "Hello"
                }
            }
            
            task generator(type: GeneratorTask) {
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
    def "nested input using output file property of different task adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }
            
            class NestedBeanWithInput {
                @InputFile
                RegularFileProperty file
            }
            
            class GeneratorTask extends DefaultTask {
                @OutputFile
                RegularFileProperty outputFile = newOutputFile()
                
                @TaskAction
                void doStuff() {
                    outputFile.getAsFile().get().text = "Hello"
                }
            }
            
            task generator(type: GeneratorTask) {
                outputFile.set(project.layout.buildDirectory.file('output'))
            }
            
            task consumer(type: TaskWithNestedProperty) {
                bean = new NestedBeanWithInput(file: generator.outputFile)
            }
        """

        when:
        run 'consumer'

        then:
        // FIXME: Should have been executed
        notExecuted(':generator')
        // FIXME: Should have been executed
        skipped(':consumer')
    }

    @Unroll
    def "re-configuring #change in a nested bean during execution time is detected"() {
        def firstInputFile = 'firstInput.txt'
        def firstOutputFile = 'build/firstOutput.txt'
        def secondInputFile = 'secondInput.txt'
        def secondOutputFile = 'build/secondOutput.txt'

        buildFile << """
            class TaskWithNestedProperty extends DefaultTask {
                @Nested
                Object bean
                
                @OutputFile
                RegularFileProperty outputFile = newOutputFile()
                
                @TaskAction
                void writeInputToFile() {
                    outputFile.getAsFile().get().text = bean
                    bean.doStuff()
                }
            }
            
            class NestedBeanWithInput {
                @Input
                String firstInput
                                 
                @InputFile
                File firstInputFile

                @OutputFile
                File firstOutputFile
                
                String toString() {
                    firstInput
                }              

                void doStuff() {
                    firstOutputFile.text = firstInputFile.text
                }
            }
            
            class NestedBeanWithOtherInput {
                @Input
                String secondInput
                
                @InputFile
                File secondInputFile

                @OutputFile
                File secondOutputFile
                
                String toString() {
                    secondInput
                }

                void doStuff() {
                    secondOutputFile.text = secondInputFile.text
                }
            }
            
            task taskWithNestedProperty(type: TaskWithNestedProperty) {
                def firstString = project.findProperty('firstInput')
                firstInput = new NestedBeanWithInput(firstInput: firstString, firstOutputFile: file("${firstOutputFile}"), firstInputFile: file("${firstInputFile}"))
                outputFile.set(project.layout.buildDirectory.file('output.txt'))
            }
            
            task configureTask {
                def secondString = project.findProperty('secondInput')
                doLast {
                    taskWithNestedProperty.bean = new NestedBeanWithOtherInput(secondInput: secondString, secondOutputFile: file("${secondOutputFile}"), secondInputFile: file("${secondInputFile}"))
                }
            }
            
            taskWithNestedProperty.dependsOn(configureTask)
        """

        def task = ':taskWithNestedProperty'
        file(firstInputFile).text = "first input file"
        file(secondInputFile).text = "second input file"

        def inputProperties = [
                first: 'first',
                second: 'second'
        ]
        def inputFiles = [
                first: file(firstInputFile),
                second: file(secondInputFile)
        ]
        def outputFiles = [
                first: file(firstOutputFile),
                second: file(secondOutputFile)
        ]

        def changes = [
                inputProperty: { String property ->
                    inputProperties[property] = inputProperties[property] + ' changed'
                },
                inputFile: { String property ->
                    inputFiles[property] << ' changed'
                },
                outputFile: { String property ->
                    outputFiles[property] << ' changed'
                }
        ]

        def runTask = { ->
            run task, '-PfirstInput=' + inputProperties.first, '-PsecondInput=' + inputProperties.second
        }

        when:
        runTask()

        then:
        executedAndNotSkipped(task)

        when:
        changes[change]('first')
        runTask()

        then:
        skipped(task)

        when:
        changes[change]('second')
        runTask()

        then:
        executedAndNotSkipped(task)

        where:
        change << ['inputProperty', 'inputFile', 'outputFile']
    }

}
