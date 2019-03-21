/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.change.ChangeTypeInternal
import org.gradle.work.Incremental
import spock.lang.Issue
import spock.lang.Unroll

class IncrementalInputsIntegrationTest extends AbstractIncrementalTasksIntegrationTest {

    String getTaskAction() {
        """
            void execute(InputChanges inputChanges) {
                assert !(inputChanges instanceof ExtensionAware)
    
                if (project.hasProperty('forceFail')) {
                    throw new RuntimeException('failed')
                }
    
                incrementalExecution = inputChanges.incremental
    
                inputChanges.getFileChanges(inputDir).each { change ->
                    switch (change.changeType) {
                        case ChangeType.ADDED:
                            addedFiles << change.file
                            break
                        case ChangeType.MODIFIED:
                            modifiedFiles << change.file
                            break
                        case ChangeType.REMOVED:
                            removedFiles << change.file
                            break
                        default:
                            throw new IllegalStateException()
                    }
                }
    
                if (!inputChanges.incremental) {
                    createOutputsNonIncremental()
                }
                
                touchOutputs()
            }
        """
    }

    @Override
    ChangeTypeInternal getRebuildChangeType() {
        return ChangeTypeInternal.ADDED
    }

    @Override
    String getPrimaryInputAnnotation() {
        return "@${Incremental.simpleName}"
    }

    def "incremental task is executed non-incrementally when input file property has been added"() {
        given:
        file('new-input.txt').text = "new input file"
        previousExecution()

        when:
        buildFile << "incremental.inputs.file('new-input.txt')"

        then:
        executesNonIncrementally()
    }

    @Unroll
    @Issue("https://github.com/gradle/gradle/issues/4166")
    def "file in input dir appears in task inputs for #inputAnnotation"() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @${inputAnnotation}
                @Incremental
                abstract DirectoryProperty getInput()
                @OutputFile
                File output
                
                @TaskAction
                void doStuff(InputChanges changes) {
                    def changed = changes.getFileChanges(input)*.file*.name as List
                    assert changed.contains('child')
                    output.text = changed.join('\\n')
                }
            }           
            
            task myTask(type: MyTask) {
                input = mkdir(inputDir)
                output = file("build/output.txt")
            }          
        """
        String myTask = ':myTask'

        when:
        file("inputDir1/child") << "inputFile1"
        run myTask, '-PinputDir=inputDir1'
        then:
        executedAndNotSkipped(myTask)

        when:
        file("inputDir2/child") << "inputFile2"
        run myTask, '-PinputDir=inputDir2'
        then:
        executedAndNotSkipped(myTask)

        where:
        inputAnnotation << [InputFiles.name, InputDirectory.name]
    }

    def "cannot query non-incremental file input parameters"() {
        given:
        buildFile << """
            abstract class WithNonIncrementalInput extends BaseIncrementalTask {
                
                @InputFile
                abstract RegularFileProperty getNonIncrementalInput()
                
                @Override
                void execute(InputChanges inputChanges) {
                    inputChanges.getFileChanges(nonIncrementalInput)
                }
            }
            
            task withNonIncrementalInput(type: WithNonIncrementalInput) {
                inputDir = file("inputs")
                nonIncrementalInput = file("nonIncremental")
            }
        """
        file("nonIncremental").text = "input"

        expect:
        fails("withNonIncrementalInput")
        failure.assertHasCause("Cannot query incremental changes: No property found for value property(interface org.gradle.api.file.RegularFile, fixed(class org.gradle.api.internal.file.DefaultFilePropertyFactory\$FixedFile, ${file( "nonIncremental").absolutePath})). Incremental properties: inputDir.")
    }

    def "changes to non-incremental input parameters cause a rebuild"() {
        given:
        buildFile << """
            abstract class WithNonIncrementalInput extends BaseIncrementalTask {
                
                @InputFile
                File nonIncrementalInput
                
                @Override
                void execute(InputChanges changes) {
                    super.execute(changes)
                    assert !changes.incremental
                }
            }
            
            task withNonIncrementalInput(type: WithNonIncrementalInput) {
                inputDir = file("inputs")
                nonIncrementalInput = file("nonIncremental")
            }
        """
        file("nonIncremental").text = "input"
        run("withNonIncrementalInput")

        when:
        file("inputs/new-input-file.txt") << "new file"
        file("nonIncremental").text = 'changed'
        then:
        succeeds("withNonIncrementalInput")
    }
    
    def "properties annotated with SkipWhenEmpty are incremental"() {
        setupTaskSources("@${SkipWhenEmpty.simpleName}")

        given:
        previousExecution()

        when:
        file('inputs/file1.txt') << "changed content"

        then:
        executesIncrementally(modified: ['file1.txt'])
    }

    def "two incremental inputs cannot have the same value"() {
        buildFile << """

            class MyTask extends DefaultTask {
                @Incremental
                @InputDirectory
                File inputOne
            
                @Incremental
                @InputDirectory
                File inputTwo

                @OutputDirectory
                File outputDirectory
                            
                @TaskAction
                void run(InputChanges changes) {
                    new File(outputDirectory, "one.txt").text = changes.getFileChanges(inputOne)*.file*.name.join("\\n")
                    new File(outputDirectory, "two.txt").text = changes.getFileChanges(inputTwo)*.file*.name.join("\\n")
                }
            }

            task myTask(type: MyTask) {
                inputOne = file("input")
                inputTwo = file("input")
                outputDirectory = file("build/output")
            }
        """
        
        file("input").createDir()

        expect:
        fails("myTask")
        failureHasCause("Multiple entries with same key: ${file('input').absolutePath}=inputTwo and ${file('input').absolutePath}=inputOne")
    }

    def "two incremental file properties can point to the same file"() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Incremental
                @InputDirectory
                abstract DirectoryProperty getInputOne()
            
                @Incremental
                @InputDirectory
                abstract DirectoryProperty getInputTwo()

                @OutputDirectory
                File outputDirectory
                            
                @TaskAction
                void run(InputChanges changes) {
                    new File(outputDirectory, "one.txt").text = changes.getFileChanges(inputOne)*.file*.name.join("\\n")
                    new File(outputDirectory, "two.txt").text = changes.getFileChanges(inputTwo)*.file*.name.join("\\n")
                }
            }

            task myTask(type: MyTask) {
                inputOne = file("input")
                inputTwo = file("input")
                outputDirectory = file("build/output")
            }
        """

        file("input").createDir()

        expect:
        succeeds("myTask")
    }

    def "values are required for incremental inputs"() {
        buildFile << """

            abstract class MyTask extends DefaultTask {
                @Incremental
                @Optional
                @InputDirectory
                ${propertyDefinition}

                @OutputDirectory
                File outputDirectory
                            
                @TaskAction
                void run(InputChanges changes) {
                    new File(outputDirectory, "output.txt").text = "Success"
                }
            }

            task myTask(type: MyTask) {
                outputDirectory = file("build/output")
            }
        """

        file("input").createDir()

        expect:
        fails("myTask")
        failure.assertHasDescription("Execution failed for task ':myTask'.")
        failure.assertHasCause("Must specify a value for incremental input property 'input'.")

        where:
        propertyDefinition << ["abstract DirectoryProperty getInput()", "abstract RegularFileProperty getInput()", "File input"]
    }
}
