/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

class TaskPropertyNamingIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://issues.gradle.org/browse/GRADLE-3538")
    def "names of annotated properties are used in property specs"() {
        file("input.txt").createNewFile()
        file("input-nested.txt").createNewFile()
        file("input1.txt").createNewFile()
        file("input2.txt").createNewFile()
        file("inputs").createDir()
        file("inputs/inputA.txt").createNewFile()
        file("inputs/inputB.txt").createNewFile()
        file("inputs1").createDir()
        file("inputs2").createDir()

        buildFile """
            class MyConfig {
                @Input String inputString
                @InputFile File inputFile
                @OutputFiles Set<File> outputFiles
            }

            class MyTask extends DefaultTask {
                @Input String inputString
                @Nested MyConfig nested = new MyConfig()
                @InputFile File inputFile
                @InputDirectory File inputDirectory
                @InputFiles FileCollection inputFiles

                @OutputFile File outputFile
                @OutputFiles FileCollection outputFiles
                @OutputFiles Map<String, File> namedOutputFiles
                @OutputDirectory File outputDirectory
                @OutputDirectories FileCollection outputDirectories
                @OutputDirectories Map<String, File> namedOutputDirectories
            }

            import org.gradle.api.internal.tasks.*
            import org.gradle.api.internal.tasks.properties.*
            import org.gradle.internal.fingerprint.*
            import org.gradle.internal.properties.*
            import org.gradle.internal.properties.bean.*

            import javax.annotation.Nullable

            task myTask(type: MyTask) {
                inputString = "data"

                nested.inputString = "data"
                nested.inputFile = file("input-nested.txt")
                nested.outputFiles = [file("output-nested-1.txt"), file("output-nested-2.txt")]

                inputFile = file("input.txt")
                inputDirectory = file("inputs")
                inputFiles = files("input1.txt", "input2.txt")

                outputFile = file("output.txt")
                outputFiles = files("output1.txt", "output2.txt")
                namedOutputFiles = [one: file("output-one.txt"), two: file("output-two.txt")]
                outputDirectory = file("outputs")
                outputDirectories = files("outputs1", "outputs2")
                namedOutputDirectories = [one: file("outputs-one"), two: file("outputs-two")]

                doLast {
                    def outputFiles = [:]
                    def inputFiles = [:]
                    def layout = services.get(ProjectLayout)
                    TaskPropertyUtils.visitProperties(services.get(PropertyWalker), it, new PropertyVisitor() {
                        @Override
                        void visitInputFileProperty(String propertyName, boolean optional, InputBehavior behavior, DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingSensitivity, @Nullable FileNormalizer fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
                            inputFiles[propertyName] = layout.files(value)
                        }

                        @Override
                        void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
                            outputFiles[propertyName] = layout.files(value)
                        }
                    })
                    inputFiles.each { propertyName, value ->
                        println "Input: \${propertyName} \${value.files*.name.sort()}"
                    }
                    outputs.fileProperties.each { property ->
                        println "Output: \${property.propertyName} \${property.propertyFiles.files*.name.sort()}"
                    }
                }
            }
        """
        when:
        run "myTask"
        then:
        output.contains "Input: inputDirectory [inputs]"
        output.contains "Input: inputFile [input.txt]"
        output.contains "Input: inputFiles [input1.txt, input2.txt]"
        output.contains "Input: nested.inputFile [input-nested.txt]"
        output.contains "Output: namedOutputDirectories.one [outputs-one]"
        output.contains "Output: namedOutputDirectories.two [outputs-two]"
        output.contains "Output: namedOutputFiles.one [output-one.txt]"
        output.contains "Output: namedOutputFiles.two [output-two.txt]"
        output.contains 'Output: nested.outputFiles$1 [output-nested-1.txt]'
        output.contains 'Output: nested.outputFiles$2 [output-nested-2.txt]'
        output.contains 'Output: outputDirectories$1 [outputs1]'
        output.contains 'Output: outputDirectories$2 [outputs2]'
        output.contains "Output: outputDirectory [outputs]"
        output.contains "Output: outputFile [output.txt]"
        output.contains 'Output: outputFiles$1 [output1.txt]'
        output.contains 'Output: outputFiles$2 [output2.txt]'
    }

    @ToBeFixedForConfigurationCache(because = "task references another task")
    def "nested properties are discovered"() {
        buildFile << classesForNestedProperties()
        buildFile << """
            task test(type: TaskWithNestedObjectProperty) {
                input = "someString"
                bean = new NestedProperty(
                    inputDir: file('input'),
                    input: 'someString',
                    outputDir: file("\$buildDir/output"),
                    nestedBean: new AnotherNestedProperty(inputFile: file('inputFile'))
                )
            }
            task printMetadata(type: PrintInputsAndOutputs) {
                task = test
            }
        """
        file('input').createDir()
        file('inputFile').createFile()

        expect:
        succeeds "test", "printMetadata"
        output.contains "Input property 'input'"
        output.contains "Input property 'bean'"

        output.contains "Input property 'bean.input'"
        output.contains "Input property 'bean.nestedBean'"
        output.contains "Input file property 'bean.inputDir'"
        output.contains "Input file property 'bean.nestedBean.inputFile'"
        output.contains "Output file property 'bean.outputDir'"
    }

    @ToBeFixedForConfigurationCache(because = "task references another task")
    def "nested iterable properties have names"() {
        buildFile << printPropertiesTask()
        buildFile """
            class TaskWithNestedBean extends DefaultTask {
                @Nested
                List<Object> beans
            }

            class NestedBean {
                @Input
                input
            }

            class OtherNestedBean {
                @Input
                secondInput
            }

            task test(type: TaskWithNestedBean) {
                beans = [new NestedBean(input: 'someString'), new OtherNestedBean(secondInput: 'otherString')]
            }
            task printMetadata(type: PrintInputsAndOutputs) {
                task = test
            }
        """

        expect:
        succeeds 'test', 'printMetadata'
        output.contains "Input property 'beans.\$0'"
        output.contains "Input property 'beans.\$0.input'"
        output.contains "Input property 'beans.\$1'"
        output.contains "Input property 'beans.\$1.secondInput'"
    }

    @ToBeFixedForConfigurationCache(because = "task references another task")
    def "nested destroyables are discovered"() {
        buildFile << classesForNestedProperties()
        buildFile << """
            class MyDestroyer extends DefaultTask {
                @TaskAction void doStuff() {}
                @Nested
                Object bean
            }
            class DestroyerBean {
                @Destroys File destroyedFile
            }

            task destroy(type: MyDestroyer) {
                bean = new DestroyerBean(
                    destroyedFile: file("\$buildDir/destroyed")
                )
            }
            task printMetadata(type: PrintInputsAndOutputs) {
                task = destroy
            }
        """

        when:
        succeeds "destroy", "printMetadata"

        then:
        output.contains "Input property 'bean'"
        output =~ /Destroys: '.*destroyed'/
    }

    @ToBeFixedForConfigurationCache(because = "task references another task")
    def "nested local state is discovered"() {
        buildFile << classesForNestedProperties()
        buildFile << """
            class TaskWithNestedLocalState extends DefaultTask {
                @TaskAction void doStuff() {}
                @Nested
                Object bean
            }
            class LocalStateBean {
                @LocalState File localStateFile
            }

            task taskWithLocalState(type: TaskWithNestedLocalState) {
                bean = new LocalStateBean(
                    localStateFile: file("\$buildDir/localState")
                )
            }
            task printMetadata(type: PrintInputsAndOutputs) {
                task = taskWithLocalState
            }
        """

        when:
        succeeds "taskWithLocalState", "printMetadata"

        then:
        output.contains "Input property 'bean'"
        output =~ /Local state: '.*localState'/
    }

    @ToBeFixedForConfigurationCache(because = "task references another task")
    def "unnamed file properties are named"() {
        buildFile << """
            import org.gradle.api.internal.tasks.*
            import org.gradle.api.internal.tasks.properties.*

            task myTask {
                inputs.file("input.txt")
                inputs.files("input-1.txt", "input-2.txt")
                inputs.dir("input-dir")

                outputs.file("output.txt")
                outputs.files("output-1.txt", "output-2.txt")
                outputs.dir("output-dir")
                outputs.dirs("output-dir-1", "output-dir-2")
            }
        """ << printProperties("myTask")
        when:
        run "printProperties"
        then:
        output.contains """
            Input file property '\$1'
            Input file property '\$2'
            Input file property '\$3'
            Output file property '\$1'
            Output file property '\$2'
            Output file property '\$3'
            Output file property '\$4'
            """.stripIndent()
    }

    @Issue("https://github.com/gradle/gradle/issues/4085")
    @ToBeFixedForConfigurationCache(because = "task references another task")
    def "can register more unnamed properties after properties have been queried"() {
        buildFile << """
            import org.gradle.api.internal.tasks.*
            import org.gradle.api.internal.tasks.properties.*

            task myTask {
                // Register first unnamed property
                inputs.file("input-1.txt")
                // Trigger calculating unnamed property names
                inputs.hasInputs

                // Register another unnamed property
                inputs.file("input-2.txt")
                // Trigger calculating unnamed property names again
                inputs.hasInputs

                // Register first unnamed property
                outputs.file("output-1.txt")
                // Trigger calculating unnamed property names
                outputs.hasOutput

                // Register another unnamed property
                outputs.file("output-2.txt")
                // Trigger calculating unnamed property names again
                outputs.hasOutput
            }

            ${printProperties("myTask")}
        """
        when:
        run "printProperties"
        then:
        output.contains """
            Input file property '\$1'
            Input file property '\$2'
            Output file property '\$1'
            Output file property '\$2'
            """.stripIndent()
    }

    @ToBeFixedForConfigurationCache(because = "task references another task")
    def "input properties can be overridden"() {
        buildFile << classesForNestedProperties()
        buildFile """
            task test(type: TaskWithNestedObjectProperty) {
                input = "someString"
                bean = new NestedProperty(
                    input: 'someString',
                )
                inputs.property("input", "someOtherString")
                inputs.property("bean.input", "otherNestedString")
            }
            task printMetadata(type: PrintInputsAndOutputs) {
                task = test
            }
        """
        file('input').createDir()
        file('inputFile').createFile()

        when:
        succeeds "test", "printMetadata"

        then:
        output.contains "Input property 'input'"
        output.contains "Input property 'bean.input'"

        output.contains "Input property 'bean'"
        output.contains "Input file property 'bean.inputDir'"
    }

    String classesForNestedProperties() {
        """
            class TaskWithNestedObjectProperty extends DefaultTask {
                @Nested
                Object bean
                @Input
                String input

                @TaskAction
                void doStuff() {}
            }

            class NestedProperty {
                @InputDirectory
                @Optional
                File inputDir

                @OutputDirectory
                @Optional
                File outputDir

                @Input
                String input
                @Nested
                @Optional
                Object nestedBean
                @Destroys File destroyedFile
            }
            class AnotherNestedProperty {
                @InputFile
                File inputFile
            }

            ${printPropertiesTask()}
        """
    }

    String printPropertiesTask() {
        """
            import org.gradle.api.internal.tasks.*
            import org.gradle.api.internal.tasks.properties.*
            import org.gradle.internal.fingerprint.*
            import org.gradle.internal.properties.*
            import org.gradle.internal.properties.bean.*

            import javax.annotation.Nullable

            class PrintInputsAndOutputs extends DefaultTask {
                @Internal
                Task task
                @TaskAction
                void printInputsAndOutputs() {
                    TaskPropertyUtils.visitProperties(project.services.get(PropertyWalker), task, new PropertyVisitor() {
                        @Override
                        void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {
                            println "Input property '\${propertyName}'"
                        }

                        @Override
                        void visitInputFileProperty(String propertyName, boolean optional, InputBehavior behavior, DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingSensitivity, @Nullable FileNormalizer fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
                            println "Input file property '\${propertyName}'"
                        }

                        @Override
                        void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
                            println "Output file property '\${propertyName}'"
                        }

                        @Override
                        void visitDestroyableProperty(Object path) {
                            println "Destroys: '\${path.call()}'"
                        }

                        @Override
                        void visitLocalStateProperty(Object value) {
                            println "Local state: '\${value.call()}'"
                        }
                    })
                }
            }
        """
    }

    def printProperties(String task) {
        printPropertiesTask() << """
            task printProperties(type: PrintInputsAndOutputs) {
                task = $task
            }
        """
    }
}
