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

        buildFile << """
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
                    inputs.fileProperties.each { property ->
                        println "Input: \${property.propertyName} \${property.propertyFiles.files*.name.sort()}"
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
        output.contains "Input: inputDirectory [inputA.txt, inputB.txt]"
        output.contains "Input: inputFile [input.txt]"
        output.contains "Input: inputFiles [input1.txt, input2.txt]"
        output.contains "Input: nested.inputFile [input-nested.txt]"
        output.contains "Output: namedOutputDirectories.one [outputs-one]"
        output.contains "Output: namedOutputDirectories.two [outputs-two]"
        output.contains "Output: namedOutputFiles.one [output-one.txt]"
        output.contains "Output: namedOutputFiles.two [output-two.txt]"
        output.contains "Output: nested.outputFiles [output-nested-1.txt, output-nested-2.txt]"
        output.contains "Output: outputDirectories [outputs1, outputs2]"
        output.contains "Output: outputDirectory [outputs]"
        output.contains "Output: outputFile [output.txt]"
        output.contains "Output: outputFiles [output1.txt, output2.txt]"
    }
}
