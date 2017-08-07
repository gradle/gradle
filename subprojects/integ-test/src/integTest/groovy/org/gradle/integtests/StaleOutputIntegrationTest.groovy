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

package org.gradle.integtests

import org.gradle.api.internal.tasks.execution.CleanupStaleOutputsExecuter
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue
import spock.lang.Unroll

@Unroll
class StaleOutputIntegrationTest extends AbstractIntegrationSpec {

    @Issue(['GRADLE-2440', 'GRADLE-2579'])
    def 'stale output file is removed after input source directory is emptied.'() {
        def taskWithSources = new TaskWithSources()
        taskWithSources.createInputs()
        buildScript(taskWithSources.buildScript)

        when:
        succeeds(taskWithSources.taskPath)

        then:
        taskWithSources.outputFile.exists()
        executedAndNotSkipped(taskWithSources.taskPath)

        when:
        taskWithSources.removeInputs()

        and:
        succeeds(taskWithSources.taskPath)

        then:
        !taskWithSources.outputFile.exists()
        executedAndNotSkipped(taskWithSources.taskPath)

        and:
        succeeds(taskWithSources.taskPath)

        then:
        !taskWithSources.outputFile.exists()
        skipped(taskWithSources.taskPath)
    }

    @Issue("https://github.com/gradle/gradle/issues/973")
    def "only files owned by the build are deleted"() {
        def taskWithSources = new TaskWithSources(outputDir: 'unsafe-dir/output')
        taskWithSources.createInputs()
        buildScript(taskWithSources.buildScript)

        when:
        succeeds(taskWithSources.taskPath)

        then:
        taskWithSources.outputFile.exists()
        executedAndNotSkipped(taskWithSources.taskPath)

        when:
        taskWithSources.removeInputs()
        succeeds(taskWithSources.taskPath)

        then:
        taskWithSources.outputFile.exists()
        skipped(taskWithSources.taskPath)
    }

    def "the output directory is not deleted if there are overlapping outputs"() {
        def taskWithSources = new TaskWithSources()
        taskWithSources.createInputs()
        def overlappingOutputFile = file("${taskWithSources.outputDir}/overlapping.txt")
        buildFile << taskWithSources.buildScript
        buildFile << """
            task taskWithOverlap {
                outputs.file('${taskWithSources.outputDir}/overlapping.txt')
                doLast {
                    file('${taskWithSources.outputDir}/overlapping.txt').text = "overlapping file"
                }
            }
        """.stripIndent()

        when:
        succeeds(taskWithSources.taskPath, "taskWithOverlap")

        then:
        taskWithSources.outputFile.exists()
        overlappingOutputFile.exists()
        executedAndNotSkipped(taskWithSources.taskPath, ":taskWithOverlap")

        when:
        taskWithSources.removeInputs()
        succeeds(taskWithSources.taskPath)

        then:
        overlappingOutputFile.exists()
        !taskWithSources.outputFile.exists()
        executedAndNotSkipped(taskWithSources.taskPath)
    }

    def "custom clean targets are removed"() {
        given:
        buildFile << """
            apply plugin: 'base'
            
            task myTask {
                outputs.dir "external/output"
                outputs.file "customFile"
                outputs.dir "build/dir"
                doLast {}
            }
            
            clean {
                delete "customFile"
            }
        """
        def dirInBuildDir = file("build/dir").createDir()
        def customFile = file("customFile").touch()
        def myTaskDir = file("external/output").createDir()

        when:
        succeeds("myTask")
        then:
        dirInBuildDir.assertDoesNotExist()
        customFile.assertDoesNotExist()
        buildFile.assertExists()
        // We should improve this eventually.  We currently don't delete _all_ outputs from every task
        // because we don't configure every clean task and we don't know if it's safe to remove all outputs.
        myTaskDir.assertExists()
    }

    def "stale outputs are removed after Gradle version change"() {
        given:
        buildFile << """
            apply plugin: 'base'

            task myTask {
                outputs.file "build/file"
                outputs.dir "build/dir"
                doLast {
                    assert !file("build/file").exists()
                    file("build/file").text = "Created"
                    assert !file("build/dir").exists() 
                    assert file("build/dir").mkdirs()
                }
            }
        """
        def dirInBuildDir = file("build/dir").createDir()
        def fileInBuildDir = file("build/file").touch()

        expect:
        succeeds("myTask")

        when:
        // Now that we produce this, we can detect the situation where
        // someone builds with Gradle 4.3, then 4.2 and then 4.3 again.
        file(".gradle/buildOutputCleanup/cache.properties").text = """
            gradle.version=1.0
        """
        // recreate the output
        dirInBuildDir.createDir()
        fileInBuildDir.touch()
        then:
        succeeds("myTask")
    }

    def "stale #type is removed before task executes"(String type, Closure creationCommand) {
        def fixture = new StaleOutputFixture(buildDir: 'build')
        fixture.createInputs()
        creationCommand(fixture.outputDir)
        creationCommand(fixture.outputFile)
        buildScript(fixture.buildScript)

        expect:
        succeeds(fixture.taskPath, '-PassertRemoved=true')

        where:
        type                      | creationCommand
        "empty directory"         | { TestFile output -> output.createDir() }
        "directory with contents" | { TestFile output ->
            output.create {
                file("first-file").text = "leftover file"
                file("second-file").text = "leftover file"
            }
        }
        'file'                    | { TestFile output -> output.text = 'leftover file' }
    }

    def "unregistered stale outputs (#nonRegisteredDirectory) are not removed before task executes"() {
        def fixture = new StaleOutputFixture(buildDir: nonRegisteredDirectory)
        fixture.createInputs()
        buildScript(fixture.buildScript)
        fixture.createStaleOutputs()

        when:
        succeeds(fixture.taskPath)

        then:
        fixture.staleFilesAreStillPresent()

        where:
        nonRegisteredDirectory << ['not-safe-to-delete', '.']
    }

    def "stale outputs are cleaned in #outputDir"() {
        def fixture = new StaleOutputFixture(buildDir: outputDir)
        fixture.createInputs()
        buildScript(fixture.buildScript)
        fixture.createStaleOutputs()

        when:
        succeeds(fixture.taskPath, '-PassertRemoved=true')

        then:
        fixture.staleFilesHaveBeenRemoved()

        where:
        outputDir << ['build', 'build/outputs', 'build/some/deeply/nested/structure']
    }

    def "build operations are created for stale outputs cleanup"() {
        def operations = new BuildOperationsFixture(executer, testDirectoryProvider)
        def fixture = new StaleOutputFixture()
        fixture.createInputs()
        buildScript(fixture.buildScript)
        fixture.createStaleOutputs()

        when:
        succeeds(fixture.taskPath, '-PassertRemoved=true')

        then:
        fixture.staleFilesHaveBeenRemoved()
        operations.hasOperation(CleanupStaleOutputsExecuter.CLEAN_STALE_OUTPUTS_DISPLAY_NAME)
    }

    def "no build operations are created for stale outputs cleanup if no files are removed"() {
        def operations = new BuildOperationsFixture(executer, testDirectoryProvider)
        def fixture = new StaleOutputFixture()
        fixture.createInputs()
        buildScript(fixture.buildScript)

        when:
        succeeds(fixture.taskPath, '-PassertRemoved=true')

        then:
        executedAndNotSkipped(fixture.taskPath)
        !operations.first(CleanupStaleOutputsExecuter.CLEAN_STALE_OUTPUTS_DISPLAY_NAME)
    }

    def "overlapping outputs between 'build/outputs' and '#overlappingOutputDir' are not cleaned up"() {
        def fixture = new StaleOutputFixture(buildDir: 'build/outputs', overlappingOutputDir: overlappingOutputDir)
        fixture.createInputs()
        buildScript(fixture.buildScript)

        when:
        succeeds fixture.taskPath, fixture.taskWritingOverlappingOutputs

        then:
        fixture.taskCreatedOutputs()
        fixture.overlappingOutputsAreStillPresent()

        where:
        overlappingOutputDir << ['build/outputs', 'build/outputs/child', 'build', 'build/other-output']
    }

    def "relative paths canonicalized for cleanup registry"() {
        def fixture = new StaleOutputFixture(buildDir: 'build/../some-dir')
        buildScript(fixture.buildScript)
        fixture.createInputs()
        fixture.createStaleOutputs()

        when:
        succeeds(fixture.taskPath)

        then:
        fixture.staleFilesAreStillPresent()
    }

    def "relative paths are canonicalized for output files"() {
        def fixture = new StaleOutputFixture(buildDir: 'build/output/../other-output')
        buildScript(fixture.buildScript)
        buildFile << """
            task writeToRealOutput() {
                outputs.dir 'build/other-output'
                doLast {
                    file('build/other-output/output-file.txt').text = "Hello world"
                }
            }
        """.stripIndent()
        fixture.createInputs()

        when:
        succeeds fixture.taskPath, 'writeToRealOutput'

        then:
        fixture.taskCreatedOutputs()
    }

    class TaskWithSources {
        String outputDir = "build/output"
        File inputFile = file('src/data/input.txt')
        String taskName = 'test'

        String getBuildScript() {
            """       
                apply plugin: 'base'

                task ${taskName} {
                    def sources = files("src")
                    inputs.dir sources skipWhenEmpty()
                    outputs.dir "${outputDir}"
                    doLast {
                        file("${outputDir}").mkdirs()
                        sources.asFileTree.visit { details ->
                            if (!details.directory) {
                                def output = file("${outputDir}/\$details.relativePath")
                                output.parentFile.mkdirs()
                                output.text = details.file.text
                            }
                        }
                    }
                }
            """.stripIndent()
        }

        File getOutputFile() {
            file("${outputDir}/data/input.txt")
        }

        String getTaskPath() {
            ":${taskName}"
        }

        void removeInputs() {
            inputFile.parentFile.deleteDir()
        }

        void createInputs() {
            inputFile.text = "input"
        }
    }

    class StaleOutputFixture {
        String buildDir = 'build'
        String overlappingOutputDir

        TestFile getOutputDir() {
            file(outputDirPath)
        }

        TestFile getOutputFile() {
            file(outputFilePath)
        }

        String getOutputDirPath() {
            return "${buildDir}/outputDir"
        }

        String getOutputFilePath() {
            return "${buildDir}/outputFile"
        }

        String getOverlappingOutputDir() {
            this.overlappingOutputDir ?: buildDir
        }

        void createStaleOutputs() {
            outputDir.create {
                file("first-file").text = "leftover file"
                file("second-file").text = "leftover file"
            }
            outputFile.text = "leftover file"
        }

        void createInputs() {
            file("input.txt").text = "input file"
            file("inputs").create {
                file("inputFile1.txt").text = "input 1 in dir"
                file("inputFile2.txt").text = "input 2 in dir"
            }
        }

        void staleFilesHaveBeenRemoved() {
            assert outputFile.text != "leftover file"
            assert !outputDir.allDescendants().contains("first-file")
            assert !outputDir.allDescendants().contains("second-file")
        }

        void staleFilesAreStillPresent() {
            assert outputFile.exists()
            assert outputDir.allDescendants().contains("first-file")
            assert outputDir.allDescendants().contains("second-file")
        }

        void taskCreatedOutputs() {
            assert outputFile.text == "This is the text"
            assert outputDir.allDescendants().containsAll('subDir/inputFile1.txt', 'subDir/inputFile2.txt')
        }

        File getOverlappingOutputFile() {
            file("${getOverlappingOutputDir()}/new-output.txt")
        }

        void overlappingOutputsAreStillPresent() {
            assert overlappingOutputFile.text == "new output"
        }

        String getTaskName() {
            'task'
        }

        String getTaskPath() {
            ":${taskName}"
        }

        String getTaskWritingOverlappingOutputs() {
            ':writeDirectlyToOutputDir'
        }

        String getBuildScript() {
            """
            apply plugin: 'base'

            task ${taskName}(type: MyTask) {
                inputDir = file("inputs")
                inputFile = file("input.txt")
                input = "This is the text"
                outputDir = file("$buildDir/outputDir")
                outputFile = file("$buildDir/outputFile")
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

            if (project.findProperty('assertRemoved')) {
                ${taskName}.doFirst {
                    assert !file('${outputDirPath}').exists()
                    assert !file('${outputFilePath}').exists()
                }
            }
                
            task writeDirectlyToOutputDir {
                outputs.dir('${buildDir}')
                
                doLast {
                    file("${getOverlappingOutputDir()}").mkdirs()
                    file("${getOverlappingOutputDir()}/new-output.txt").text = "new output"
                }
            }

        """.stripIndent()
        }
    }

}
