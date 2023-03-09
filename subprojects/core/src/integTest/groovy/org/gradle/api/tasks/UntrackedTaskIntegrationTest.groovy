/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class UntrackedTaskIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture, ValidationMessageChecker {

    def "untracked task is not up-to-date"() {
        buildFile("""
            @UntrackedTask(because = "For testing")
            abstract class MyTask extends DefaultTask {
                @InputFile
                abstract RegularFileProperty getInputFile()
                @OutputFile
                abstract RegularFileProperty getOutputFile()
                @TaskAction
                void doStuff() {
                    outputFile.get().asFile.text = inputFile.get().asFile.text
                }
            }
            tasks.register("myTask", MyTask) {
                inputFile = file("input.txt")
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)
        file("input.txt").text = "input"

        when:
        run("myTask", "--info")
        then:
        executedAndNotSkipped(":myTask")
        outputContains("Task ':myTask' is not up-to-date because:")
        outputContains("Task state is not tracked.")

        when:
        run("myTask", "--info")
        then:
        executedAndNotSkipped(":myTask")
        outputContains("Task ':myTask' is not up-to-date because:")
        outputContains("Task state is not tracked.")
    }

    def "fails when incremental task is marked as untracked"() {
        file("input/input.txt") << "Content"
        buildFile(generateUntrackedIncrementalConsumerTask())
        buildFile("""
            tasks.register("consumer", IncrementalConsumer) {
                inputDir = file("input")
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)
        file("input.txt").text = "input"

        when:
        fails("consumer", "--info")
        then:
        failureHasCause("Changes are not tracked, unable determine incremental changes.")
    }

    def "can register untracked tasks via the runtime API"() {
        buildFile("""
            tasks.register("myTask") {
                doNotTrackState("For testing")
                def inputFile = file("input.txt")
                inputs.file(inputFile)
                    .withPropertyName("inputFile")
                def outputFile = project.layout.buildDirectory.file("output.txt")
                outputs.file(outputFile)
                    .withPropertyName("outputFile")
                doLast {
                    outputFile.get().asFile.text = inputFile.text
                }
            }
        """)
        file("input.txt").text = "input"

        when:
        run("myTask")
        then:
        executedAndNotSkipped(":myTask")

        when:
        run("myTask", "--info")
        then:
        executedAndNotSkipped(":myTask")
        outputContains("Task ':myTask' is not up-to-date because:")
        outputContains("Task state is not tracked.")
    }

    def "untracked task is not cached"() {
        buildFile("""
            @CacheableTask
            @UntrackedTask(because = "For testing")
            abstract class MyTask extends DefaultTask {
                @InputFile
                @PathSensitive(PathSensitivity.RELATIVE)
                abstract RegularFileProperty getInputFile()
                @OutputFile
                abstract RegularFileProperty getOutputFile()
                @TaskAction
                void doStuff() {
                    outputFile.get().asFile.text = inputFile.get().asFile.text
                }
            }
            tasks.register("myTask", MyTask) {
                inputFile = file("input.txt")
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)
        file("input.txt").text = "input"

        when:
        withBuildCache().run("myTask", "--info")
        then:
        executedAndNotSkipped(":myTask")
        outputContains("Caching disabled for task ':myTask' because:")
        outputContains("Task is untracked because: For testing")
    }

    def "UntrackedTask annotation does not inherit"() {
        file("input.txt").text = "input"

        buildFile("""
            @UntrackedTask(because = "For testing")
            abstract class MyUntrackedTask extends DefaultTask {
                @InputFile
                abstract RegularFileProperty getInputFile()
                @OutputFile
                abstract RegularFileProperty getOutputFile()
                @TaskAction
                void doStuff() {
                    outputFile.get().asFile.text = inputFile.get().asFile.text
                }
            }

            abstract class SubclassOfUntrackedTask extends MyUntrackedTask {}

            tasks.register("myUntrackedTask", MyUntrackedTask) {
                inputFile = file("input.txt")
                outputFile = project.layout.buildDirectory.file("untracked-output.txt")
            }
            tasks.register("mySubclassOfUntrackedTask", SubclassOfUntrackedTask) {
                inputFile = file("input.txt")
                outputFile = project.layout.buildDirectory.file("subclass-output.txt")
            }
        """)

        when:
        run("myUntrackedTask", "mySubclassOfUntrackedTask")
        then:
        executedAndNotSkipped(":myUntrackedTask", ":mySubclassOfUntrackedTask")

        when:
        run("myUntrackedTask", "mySubclassOfUntrackedTask")
        then:
        executedAndNotSkipped(":myUntrackedTask")
        skipped(":mySubclassOfUntrackedTask")
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "untracked tasks can produce and consume unreadable content"() {
        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")

        buildFile generateProducerTask(true)
        buildFile generateConsumerTask(true)

        buildFile("""
            def producer = tasks.register("producer", Producer) {
                outputDir = project.layout.buildDirectory.dir("root")
            }
            tasks.register("consumer", Consumer) {
                inputDir = producer.flatMap { it.outputDir }
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)

        expect:
        succeeds("consumer", "--info")

        cleanup:
        unreadableDir.setReadable(true)
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "tracked task producing unreadable content fails"() {
        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")
        unreadableDir.mkdirs()
        unreadableDir.setReadable(false)

        buildFile generateProducerTask(false)

        buildFile("""
            tasks.register("producer", Producer) {
                outputDir = project.layout.buildDirectory.dir("root")
            }
        """)

        when:
        executer.withStackTraceChecksDisabled()
        runAndFail "producer"
        then:
        executedAndNotSkipped(":producer")
        failure.assertHasDocumentedCause("Cannot access output property 'outputDir' of task ':producer'. " +
            "Accessing unreadable inputs or outputs is not supported. " +
            "Declare the task as untracked by using Task.doNotTrackState(). " +
            "See https://docs.gradle.org/current/userguide/incremental_build.html#disable-state-tracking for more details.")
        failureHasCause("java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

        cleanup:
        unreadableDir.setReadable(true)
    }

    @Requires(UnitTestPreconditions.UnixDerivative)
    def "tracked task producing named pipe fails"() {
        def rootDir = file("build/root")
        def namedPipe = rootDir.file("unreadable")
        rootDir.mkdirs()
        namedPipe.createNamedPipe()

        buildFile generateProducerTask(false)

        buildFile("""
            tasks.register("producer", Producer) {
                outputDir = project.layout.buildDirectory.dir("root")
            }
        """)

        when:
        executer.withStackTraceChecksDisabled()
        runAndFail "producer"
        then:
        executedAndNotSkipped(":producer")
        failure.assertHasDocumentedCause("Cannot access output property 'outputDir' of task ':producer'. " +
            "Accessing unreadable inputs or outputs is not supported. " +
            "Declare the task as untracked by using Task.doNotTrackState(). " +
            "See https://docs.gradle.org/current/userguide/incremental_build.html#disable-state-tracking for more details.")
        failureHasCause("java.io.IOException: Cannot snapshot ${namedPipe}: not a regular file")
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "tracked task consuming unreadable content fails"() {
        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")
        assert unreadableDir.mkdirs()
        assert unreadableDir.setReadable(false)

        buildFile generateConsumerTask(false)

        buildFile("""
            tasks.register("consumer", Consumer) {
                inputDir = project.layout.buildDirectory.dir("root")
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)

        when:
        executer.withStackTraceChecksDisabled()
        runAndFail "consumer"
        then:
        failure.assertHasDocumentedCause("Cannot access input property 'inputDir' of task ':consumer'. " +
            "Accessing unreadable inputs or outputs is not supported. " +
            "Declare the task as untracked by using Task.doNotTrackState(). " +
            "See https://docs.gradle.org/current/userguide/incremental_build.html#disable-state-tracking for more details.")
        failureHasCause("java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

        cleanup:
        unreadableDir.setReadable(true)
    }

    def "does not clean up stale outputs for untracked tasks"() {
        def untrackedStaleFile = file("build/untracked/stale-output-file").createFile()
        def untrackedOutputFile = file("build/untracked/output.txt")
        def trackedStaleFile = file("build/tracked/stale-output-file").createFile()
        def trackedOutputFile = file("build/tracked/output.txt")

        buildFile("""
            apply plugin: 'base'

            abstract class Producer extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDirectory()

                @TaskAction
                void writeFile() {
                    def outputFile = outputDirectory.file("output.txt").get().asFile
                    outputFile.text = "Produced file"
                }
            }

            @UntrackedTask(because = 'For testing')
            abstract class UntrackedProducer extends Producer {}

            tasks.register("trackedProducer", Producer) {
                outputDirectory = project.layout.buildDirectory.dir('tracked')
            }

            tasks.register("untrackedProducer", UntrackedProducer) {
                outputDirectory = project.layout.buildDirectory.dir('untracked')
            }
        """)

        when:
        run "untrackedProducer", "trackedProducer"
        then:
        executedAndNotSkipped(":untrackedProducer", ":trackedProducer")

        trackedOutputFile.text == "Produced file"
        !trackedStaleFile.exists()

        untrackedOutputFile.text == "Produced file"
        untrackedStaleFile.exists()
    }

    def "invalidates the VFS for output directories of untracked tasks"() {
        buildFile("""
            @UntrackedTask(because = 'For testing')
            abstract class UntrackedProducer extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void createOutput() {
                    outputDir.file("untracked.txt").get().asFile.text = "untracked"
                }
            }

            abstract class TrackedProducer extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void createOutput() {
                    outputDir.file("tracked.txt").get().asFile.text = "tracked"
                }
            }

            abstract class Consumer extends DefaultTask {
                @InputDirectory
                abstract DirectoryProperty getInputDir()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void createOutput() {
                    outputFile.get().asFile.text = inputDir.get().asFile.list().sort().join(", ")
                }
            }

            def producerTracked = tasks.register("producerTracked", TrackedProducer) {
                outputDir = project.layout.buildDirectory.dir("shared-output")
            }
            def producerUntracked = tasks.register("producerUntracked", UntrackedProducer) {
                outputDir = project.layout.buildDirectory.dir("shared-output")
                mustRunAfter(producerTracked)
            }
            tasks.register("consumer", Consumer) {
                inputDir = producerTracked.flatMap { it.outputDir }
                outputFile = project.layout.buildDirectory.file("outputs.txt")
                mustRunAfter(producerUntracked)
            }
        """)

        when:
        run("consumer")
        then:
        executedAndNotSkipped(":producerTracked", ":consumer")
        notExecuted(":producerUntracked")

        when:
        run("producerUntracked", "consumer")
        then:
        executedAndNotSkipped(":producerUntracked", ":consumer")
        skipped(":producerTracked")
    }

    static generateProducerTask(boolean untracked) {
        """
            ${untracked ? "@UntrackedTask(because = 'For testing')" : ""}
            abstract class Producer extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void execute() {
                    def unreadableDir = outputDir.get().dir("unreadable").asFile
                    unreadableDir.mkdirs()
                    assert unreadableDir.setReadable(false)
                    assert !unreadableDir.canRead()
                }
            }
        """
    }

    static generateConsumerTask(boolean untracked) {
        """
            ${untracked ? "@UntrackedTask(because = 'For testing')" : ""}
            abstract class Consumer extends DefaultTask {
                @InputDirectory
                abstract DirectoryProperty getInputDir()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void execute() {
                    def unreadableDir = inputDir.get().dir("unreadable").asFile
                    assert !unreadableDir.canRead()
                    outputFile.get().asFile << "Executed"
                }
            }
        """
    }

    static generateUntrackedIncrementalConsumerTask() {
        """
            @UntrackedTask(because = "For testing")
            abstract class IncrementalConsumer extends DefaultTask {
                @SkipWhenEmpty
                @InputDirectory
                abstract DirectoryProperty getInputDir()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void execute(InputChanges changes) {
                    assert changes != null
                    def unreadableDir = inputDir.get().dir("unreadable").asFile
                    assert !unreadableDir.canRead()
                    outputFile.get().asFile << "Executed"
                }
            }
        """
    }
}
