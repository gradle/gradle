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

class StaleOutputCleanupIntegrationTest extends AbstractIntegrationSpec {

    class StaleOutputFixture {
        TestFile outputDir
        TestFile outputFile

        StaleOutputFixture(TestFile buildDir) {
            outputDir = buildDir.file("outputDir")
            outputFile = buildDir.file("outputFile")
        }

        void createOutputs() {
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
    }

    def setup() {
        executer.beforeExecute {
            executer.withArgument('--info')
        }

        file("input.txt").text = "input file"
        file("inputs").create {
            file("inputFile1.txt").text = "input 1 in dir"
            file("inputFile2.txt").text = "input 2 in dir"
        }

        buildScript """
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
        staleOutputs.createOutputs()
        succeeds("task")

        then:
        staleOutputs.haveBeenRemoved()
    }

}
