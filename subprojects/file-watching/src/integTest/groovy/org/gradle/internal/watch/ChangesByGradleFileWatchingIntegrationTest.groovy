/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.watch

import com.gradle.enterprise.testing.annotations.LocalOnly
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture

@LocalOnly
class ChangesByGradleFileWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest implements DirectoryBuildCacheFixture {

    def "detects when outputs are removed for tasks without sources"() {
        buildFile << """
            apply plugin: 'base'

            abstract class Producer extends DefaultTask {
                @InputDirectory
                @SkipWhenEmpty
                abstract DirectoryProperty getSources()

                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void listSources() {
                    outputDir.file("output.txt").get().asFile.text = sources.get().asFile.list().join("\\n")
                }
            }

            abstract class Consumer extends DefaultTask {
                @InputFiles
                abstract DirectoryProperty getInputDirectory()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    def input = inputDirectory.file("output.txt").get().asFile
                    if (input.file) {
                        outputFile.asFile.get().text = input.text
                    } else {
                        outputFile.asFile.get().text = "<empty>"
                    }
                }
            }

            task sourceTask(type: Producer) {
                sources = file("sources")
                outputDir = file("build/output")
            }

            task consumer(type: Consumer) {
                inputDirectory = sourceTask.outputDir
                outputFile = file("build/consumer.txt")
            }
        """

        def sourcesDir = file("sources")
        def sourceFile = sourcesDir.file("source.txt").createFile()
        def outputFile = file("build/output/output.txt")

        when:
        withWatchFs().run ":consumer"
        then:
        executedAndNotSkipped(":sourceTask", ":consumer")
        outputFile.assertExists()

        when:
        sourceFile.delete()
        waitForChangesToBePickedUp()
        withWatchFs().run ":consumer"
        then:
        executedAndNotSkipped(":sourceTask", ":consumer")
        outputFile.assertDoesNotExist()
    }

    def "detects when stale outputs are removed"() {
        buildFile << """
            apply plugin: 'base'

            task producer {
                def inputTxt = file("input.txt")
                def outputTxt = file("build/output.txt")
                inputs.files(inputTxt)
                outputs.file(outputTxt)
                doLast {
                    outputTxt.text = inputTxt.text
                }
            }
        """

        file("input.txt").text = "input"
        def outputFile = file("build/output.txt")

        when:
        withWatchFs().run ":producer"
        then:
        executedAndNotSkipped(":producer")
        outputFile.assertExists()

        when:
        invalidateBuildOutputCleanupState()
        waitForChangesToBePickedUp()
        withWatchFs().run ":producer", "--info"
        then:
        output.contains("Deleting stale output file: ${outputFile.absolutePath}")
        executedAndNotSkipped(":producer")
        outputFile.assertExists()
    }

    def "detects non-incremental cleanup of incremental tasks"() {
        buildFile << """
            abstract class IncrementalTask extends DefaultTask {
                @InputDirectory
                @Incremental
                abstract DirectoryProperty getSources()

                @Input
                abstract Property<String> getInput()

                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void processChanges(InputChanges changes) {
                    outputDir.file("output.txt").get().asFile.text = input.get()
                }
            }

            task incremental(type: IncrementalTask) {
                sources = file("sources")
                input = providers.systemProperty("outputDir")
                outputDir = file("build/\${input.get()}")
            }
        """

        file("sources/input.txt").text = "input"

        when:
        withWatchFs().run ":incremental", "-DoutputDir=output1"
        then:
        executedAndNotSkipped(":incremental")

        when:
        file("build/output2/overlapping.txt").text = "overlapping"
        waitForChangesToBePickedUp()
        withWatchFs().run ":incremental", "-DoutputDir=output2"
        then:
        executedAndNotSkipped(":incremental")
        file("build/output1").assertDoesNotExist()

        when:
        waitForChangesToBePickedUp()
        withWatchFs().run ":incremental", "-DoutputDir=output1"
        then:
        executedAndNotSkipped(":incremental")
        file("build/output1").assertExists()
    }

    def "detects changes to manifest"() {
        buildFile << """
            plugins {
                id 'java'
            }

            jar {
                manifest {
                    attributes('Created-By': providers.systemProperty("creator"))
                }
            }
        """

        when:
        withWatchFs().run "jar", "-Dcreator=first"
        then:
        file("build/tmp/jar/MANIFEST.MF").text.contains("first")
        executedAndNotSkipped(":jar")

        when:
        withWatchFs().run "jar", "-Dcreator=second"
        then:
        file("build/tmp/jar/MANIFEST.MF").text.contains("second")
        executedAndNotSkipped(":jar")
    }

    def "detects when local state is removed"() {
        buildFile << """
            plugins {
                id 'base'
            }

            @CacheableTask
            abstract class WithLocalStateDirectory extends DefaultTask {
                @LocalState
                abstract DirectoryProperty getLocalStateDirectory()
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doStuff() {
                    def localStateDir = localStateDirectory.get().asFile
                    localStateDir.mkdirs()
                    new File(localStateDir, "localState.txt").text = "localState"
                    outputFile.get().asFile.text = "output"
                }
            }

            abstract class WithOutputFile extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doStuff() {
                    outputFile.get().asFile.text = "outputFile"
                }
            }

            task localStateTask(type: WithLocalStateDirectory) {
                localStateDirectory = file("build/overlap")
                outputFile = file("build/localStateOutput.txt")
            }

            task outputFileTask(type: WithOutputFile) {
                outputFile = file("build/overlap/outputFile.txt")
                mustRunAfter localStateTask
            }
        """
        def localStateOutputFile = file("build/localStateOutput.txt")

        when:
        withWatchFs().withBuildCache().run("localStateTask", "outputFileTask")
        then:
        executedAndNotSkipped(":localStateTask", ":outputFileTask")
        localStateOutputFile.exists()

        when:
        localStateOutputFile.delete()
        waitForChangesToBePickedUp()
        withWatchFs().withBuildCache().run("localStateTask", "outputFileTask")
        then:
        skipped(":localStateTask")
        executedAndNotSkipped(":outputFileTask")
    }

    // This makes sure the next Gradle run starts with a clean BuildOutputCleanupRegistry
    private void invalidateBuildOutputCleanupState() {
        file(".gradle/buildOutputCleanup/cache.properties").text = """
            gradle.version=1.0
        """
    }
}
