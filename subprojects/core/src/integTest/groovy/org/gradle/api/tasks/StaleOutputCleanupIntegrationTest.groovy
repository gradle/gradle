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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

class StaleOutputCleanupIntegrationTest extends AbstractIntegrationSpec {

    class StaleOutputFixture {
        TestFile outputDir
        TestFile outputFile

        StaleOutputFixture(TestFile buildDir) {
            outputDir = buildDir.file("outputDir")
            outputFile = buildDir.file("outputFile")
        }

        void createLeftoverOutputs() {
            outputDir.create {
                file("first-file").text = "leftover file"
                file("second-file").text = "leftover file"
            }
            outputFile.text = "leftover file"
        }

        void haveBeenRemoved() {
            assert outputFile.text != "leftover file"
            assert !outputDir.allDescendants().contains("first-file")
            assert !outputDir.allDescendants().contains("second-file")
        }

        void areStillPresent() {
            assert outputFile.text == "leftover file"
            assert outputDir.allDescendants().contains("first-file")
            assert outputDir.allDescendants().contains("second-file")
        }
    }

    def setup() {
        file("input.txt").text = "input file"
        file("inputs").create {
            file("inputFile1.txt").text = "input 1 in dir"
            file("inputFile2.txt").text = "input 2 in dir"
        }

        buildScript """
            apply plugin: 'base'

            task task(type: MyTask) {
                inputDir = file("inputs")
                inputFile = file("input.txt")
                input = "This is the text"
                outputDir = file("\$buildDir/outputDir")
                outputFile = file("\$buildDir/outputFile")
            }
            
            class MyTask extends DefaultTask {
                @InputDirectory File inputDir
                @Input String input
                @InputFile File inputFile
                @OutputDirectory File outputDir
                @OutputFile File outputFile
                
                @TaskAction
                void doExecute() {
                    outputFile.text = input
                    project.copy {
                        into outputDir
                        from(inputDir) {
                            into 'subDir'
                        }
                        from inputFile 
                    }
                }
            } 
        """

    }

    def "stale files are removed before task executes"() {
        def staleOutputs = new StaleOutputFixture(file('build'))
        when:
        staleOutputs.createLeftoverOutputs()
        succeeds("task")

        then:
        staleOutputs.haveBeenRemoved()
    }

    def "unregistered stale files are not removed before task executes"() {
        def staleOutputs = new StaleOutputFixture(file('not-safe-to-delete'))
        when:
        staleOutputs.createLeftoverOutputs()
        succeeds("task")

        then:
        staleOutputs.areStillPresent()
    }

    def "overlapping outputs are not cleaned up"() {
        def staleOutputs = new StaleOutputFixture(file('build'))
        def outputDir = TextUtil.normaliseFileSeparators(staleOutputs.outputDir.absolutePath)
        def outputFile = TextUtil.normaliseFileSeparators(staleOutputs.outputFile.absolutePath)
        buildFile << """
            task overlappingTask {
                inputs.property('input') { 'some' }
                outputs.dir('${outputDir}').withPropertyName('outputDir')
                outputs.file('${outputFile}').withPropertyName('outputFile')
                doLast {
                    def outputDir = file('${outputDir}')
                    outputDir.mkdirs()
                    new File(outputDir, 'other-overlapping-file.txt').text = "overlapping output"
                    file('${outputFile}').text = "another overlapping output"
                }
            }
        """

        when:
        succeeds "overlappingTask", "task"

        then:
        file(staleOutputs.outputDir).allDescendants().contains('other-overlapping-file.txt')

    }

    def "directory containing outputs is not deleted"() {
        buildFile << """
            task writeDirectlyToBuild {
                outputs.dir(buildDir)
                
                doLast {
                    file("\$buildDir/new-output.txt").text = "new output"
                }
            }
        """

        when:
        succeeds "task", "writeDirectlyToBuild"

        then:
        file('build/outputDir/input.txt').text == "input file"
        file('build/new-output.txt').text == "new output"
    }

}
