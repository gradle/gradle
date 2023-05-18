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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.execution.steps.CleanupStaleOutputsStep
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

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
        taskWithSources.outputsHaveBeenRemoved()
        executedAndNotSkipped(taskWithSources.taskPath)

        and:
        succeeds(taskWithSources.taskPath)

        then:
        taskWithSources.outputsHaveBeenRemoved()
        skipped(taskWithSources.taskPath)

        when:
        taskWithSources.outputFile << "Added by other task"
        run(taskWithSources.taskPath)
        then:
        taskWithSources.outputFile.exists()
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
                def overlappingFile = file('${taskWithSources.outputDir}/overlapping.txt')
                outputs.file(overlappingFile)
                doLast {
                    overlappingFile.text = "overlapping file"
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
        taskWithSources.onlyOutputFileHasBeenRemoved()
        executedAndNotSkipped(taskWithSources.taskPath)
    }

    @Issue("https://github.com/gradle/gradle/issues/8299")
    def "two tasks can output in the same directory with --rerun-tasks"() {
        buildFile << """
            apply plugin: 'base'

            task firstCopy {
                inputs.file('first.file')
                outputs.dir('build/destination')
                def outputFile = file('build/destination/first.file')
                def inputFile = file('first.file')
                doLast {
                    outputFile.text = inputFile.text
                }
            }

            task secondCopy {
                inputs.file('second.file')
                outputs.dir('build/destination')
                def outputFile = file('build/destination/second.file')
                def inputFile = file('second.file')
                doLast {
                    outputFile.text = inputFile.text
                }
            }

            secondCopy.dependsOn firstCopy
        """
        file("first.file").createFile()
        file("second.file").createFile()

        when:
        succeeds("secondCopy", "--rerun-tasks", "--info")
        then:
        file("build/destination/first.file").exists()
        file("build/destination/second.file").exists()
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
        file("build/dir/stale-file.txt").touch()
        def customFile = file("customFile").touch()
        def myTaskDir = file("external/output").createDir()

        when:
        succeeds("myTask")
        then:
        dirInBuildDir.assertIsEmptyDir()
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
                def builtFile = file('build/file')
                def builtDir = file('build/dir')
                outputs.file builtFile
                outputs.dir builtDir
                doLast {
                    assert !builtFile.exists()
                    builtFile.text = "Created"
                    assert builtDir.directory
                    assert builtDir.list().length == 0
                }
            }
        """
        def dirInBuildDir = file("build/dir").createDir()
        def staleFileInDir = file("build/dir/stale-file.txt").touch()
        def fileInBuildDir = file("build/file").touch()

        expect:
        succeeds("myTask")

        when:
        invalidateBuildOutputCleanupState()
        dirInBuildDir.createDir()
        staleFileInDir.touch()
        fileInBuildDir.touch()
        then:
        succeeds("myTask")
    }

    // This makes sure the next Gradle run starts with a clean BuildOutputCleanupRegistry
    private void invalidateBuildOutputCleanupState() {
        file(".gradle/buildOutputCleanup/cache.properties").text = """
            gradle.version=1.0
        """
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
        operations.hasOperation(CleanupStaleOutputsStep.CLEAN_STALE_OUTPUTS_DISPLAY_NAME)
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
        !operations.first(CleanupStaleOutputsStep.CLEAN_STALE_OUTPUTS_DISPLAY_NAME)
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
                def outputFile = file('build/other-output/output-file.txt')
                doLast {
                    outputFile.text = "Hello world"
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
                    def sources = file("src")
                    inputs.dir sources skipWhenEmpty()
                    outputs.dir "${outputDir}"
                    def outputDir = file("${outputDir}")
                    def sourceTree = files(sources).asFileTree
                    doLast {
                        outputDir.mkdirs()
                        sourceTree.visit { details ->
                            if (!details.directory) {
                                def output = new File(outputDir, "\$details.relativePath")
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

        void outputsHaveBeenRemoved() {
            assert !outputFile.exists()
            assert !file(outputDir).exists()
        }

        void onlyOutputFileHasBeenRemoved() {
            assert !outputFile.exists()
            assert file(outputDir).exists()
        }
    }

    @ToBeImplemented("We don't currently clean up local state")
    def "stale local state file is removed after input source directory is emptied"() {
        def taskWithLocalState = new TaskWithLocalState()
        taskWithLocalState.createInputs()
        buildScript(taskWithLocalState.buildScript)

        when:
        succeeds(taskWithLocalState.taskPath)

        then:
        taskWithLocalState.localStateFile.exists()
        executedAndNotSkipped(taskWithLocalState.taskPath)

        when:
        taskWithLocalState.removeInputs()

        and:
        succeeds(taskWithLocalState.taskPath)

        then:
        // FIXME This should be localStateHasBeenRemoved(), and the task should not be skipped
        taskWithLocalState.localStateHasNotBeenRemoved()
        skipped(taskWithLocalState.taskPath)
    }

    def "up-to-date checks detect removed stale outputs"() {

        given:
        buildFile << """
            plugins {
                id 'base'
            }

            def originalDir = file('build/original')
            def backupDir = file('backup')

            task backup(type: Copy) {
                from originalDir
                into backupDir
            }

            task restore(type: Copy) {
                from backupDir
                into originalDir
            }
        """

        and:
        def original = file('build/original/original.txt')
        original.text = "Original"
        def backup = file('backup/original.txt')

        and:
        executer.beforeExecute {
            withArgument("--max-workers=1")
        }

        when:
        succeeds "backup"
        then:
        executedAndNotSkipped(':backup')

        when:
        succeeds "restore"

        then:
        executedAndNotSkipped(':restore')
        original.text == backup.text
        original.text == "Original"

        when:
        // The whole setup is as follows:
        // - Both the restore and the backup task have an entry in the task history
        // - The output of the restore path is the input of the backup task.
        //   This means when the backup task is up-to-date, then the contents of the output of the restore path is in the file system mirror.
        //
        // If cleaning up stale output files does not invalidate the file system mirror, then the restore task would be up-to-date.
        invalidateBuildOutputCleanupState()
        succeeds 'backup'
        then:
        skipped ':backup'

        when:
        succeeds 'restore', '--info'
        then:
        output.contains("Deleting stale output file: ${original.parentFile.absolutePath}")
        executedAndNotSkipped(':restore')
        original.text == backup.text
        original.text == "Original"
    }

    def "task with file tree output can be up-to-date"() {
        buildFile << """
            plugins {
                id 'base'
            }

            abstract class TaskWithFileTreeOutput extends DefaultTask {
                @Input
                String input

                @Internal
                File outputDir

                @Inject
                abstract ObjectFactory getObjectFactory()

                @OutputFiles
                FileCollection getOutputFileTree() {
                    objectFactory.fileTree().setDir(outputDir).include('**/myOutput.txt')
                }

                @TaskAction
                void generateOutputs() {
                    outputDir.mkdirs()
                    new File(outputDir, 'myOutput.txt').text = input
                }
            }

            task custom(type: TaskWithFileTreeOutput) {
                outputDir = file('build/outputs')
                input = 'input'
            }
        """
        def taskPath = ':custom'

        when:
        run taskPath
        then:
        executedAndNotSkipped taskPath

        when:
        run taskPath, '--info'

        then:
        skipped taskPath
    }

    class TaskWithLocalState {
        String localStateDir = "build/state"
        String outputFile = "build/output.txt"
        File inputFile = file('src/data/input.txt')
        String taskName = 'test'

        String getBuildScript() {
            """
                apply plugin: 'base'

                task ${taskName} {
                    def sources = file("src")
                    inputs.dir sources skipWhenEmpty()
                    outputs.file "$outputFile"
                    localState.register "$localStateDir"
                    def stateDir = file("${localStateDir}")
                    def sourceTree = files(sources).asFileTree
                    doLast {
                        stateDir.mkdirs()
                        sourceTree.visit { details ->
                            if (!details.directory) {
                                def output = new File(stateDir, "\${details.relativePath}.info")
                                output.parentFile.mkdirs()
                                output.text = "Analysis for \${details.relativePath}"
                            }
                        }
                    }
                }
            """
        }

        String getTaskPath() {
            ":${taskName}"
        }

        TestFile getLocalStateFile() {
            file("${localStateDir}/data/input.txt.info")
        }

        void removeInputs() {
            inputFile.parentFile.deleteDir()
        }

        void createInputs() {
            inputFile.text = "input"
        }

        void localStateHasBeenRemoved() {
            assert !file(localStateDir).exists()
        }

        void localStateHasNotBeenRemoved() {
            assert file(localStateDir).exists()
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

            abstract class MyTask extends DefaultTask {
                @InputDirectory File inputDir
                @Input String input
                @InputFile File inputFile
                @OutputDirectory File outputDir
                @OutputFile File outputFile

                @Inject abstract FileSystemOperations getFileSystemOperations()

                @TaskAction
                void doExecute() {
                    outputFile.text = input
                    fileSystemOperations.copy {
                        into outputDir
                        from(inputDir) {
                            into 'subDir'
                        }
                        from inputFile
                    }
                }
            }

            if (project.findProperty('assertRemoved')) {
                def outputDir = file('${outputDirPath}')
                def outputFile = file('${outputFilePath}')
                ${taskName}.doFirst {
                    assert outputDir.directory
                    assert outputDir.list().length == 0
                    assert !outputFile.exists()
                }
            }

            task writeDirectlyToOutputDir {
                outputs.dir('${buildDir}')

                def overlappingDir = file("${getOverlappingOutputDir()}")
                doLast {
                    overlappingDir.mkdirs()
                    new File(overlappingDir, "new-output.txt").text = "new output"
                }
            }

        """.stripIndent()
        }
    }
}
