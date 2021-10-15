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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.work.InputChanges

class UntrackedTaskIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture, ValidationMessageChecker {

    def "untracked task is not up-to-date"() {
        buildFile("""
            @Untracked(because = "For testing")
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
        buildFile(generateUntrackedIncrementalConsumerTask(inputChangesType))
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

        where:
        //noinspection UnnecessaryQualifiedReference, GrDeprecatedAPIUsage
        inputChangesType << [InputChanges, org.gradle.api.tasks.incremental.IncrementalTaskInputs]
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
            @Untracked(because = "For testing")
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
        outputContains("'Task is untracked because: For testing' satisfied")
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
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

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "tracked task producing unreadable content is not stored in execution history"() {
        executer.beforeExecute {
            executer.withStackTraceChecksDisabled()
            executer.expectDeprecationWarning("Cannot access output property 'outputDir' of task ':producer' (see --info log for details). " +
                "Accessing unreadable inputs or outputs has been deprecated. " +
                "This will fail with an error in Gradle 8.0. " +
                "Declare the task as untracked by using Task.doNotTrackState().")
        }

        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")

        buildFile generateProducerTask(false)

        buildFile("""
            tasks.register("producer", Producer) {
                outputDir = project.layout.buildDirectory.dir("root")
            }
        """)

        when:
        run "producer", "--info"
        then:
        executedAndNotSkipped(":producer")
        outputContains("Cannot access output property 'outputDir' of task ':producer'")
        outputContains("java.io.UncheckedIOException: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

        when:
        unreadableDir.setReadable(true)
        run "producer", "--info"
        then:
        executedAndNotSkipped(":producer")
        outputContains("Task ':producer' is not up-to-date because:")
        outputContains("No history is available.")
        outputContains("Cannot access output property 'outputDir' of task ':producer'")
        outputContains("java.io.UncheckedIOException: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

        cleanup:
        unreadableDir.setReadable(true)
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    def "pipe as output file emits deprecation message"() {
        executer.beforeExecute {
            executer.withStackTraceChecksDisabled()
            executer.expectDeprecationWarning("Cannot access output property 'outputFile' of task ':producer' (see --info log for details). " +
                "Accessing unreadable inputs or outputs has been deprecated. " +
                "This will fail with an error in Gradle 8.0. " +
                "Declare the task as untracked by using Task.doNotTrackState().")
        }

        def rootDir = createDir("build")
        def unreadableFile = rootDir.file("unreadable")
        unreadableFile.createNamedPipe()

        buildFile("""
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void execute() {
                    outputFile.get().asFile
                }
            }

            tasks.register("producer", Producer) {
                outputFile = project.layout.buildDirectory.file("unreadable")
            }
        """)

        when:
        run "producer", "--info"
        then:
        executedAndNotSkipped(":producer")
        outputContains("Cannot access output property 'outputFile' of task ':producer'")
        outputContains("org.gradle.api.UncheckedIOException: Unsupported file type for ${unreadableFile.absolutePath}")
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "task producing unreadable content is not stored in cache"() {
        executer.beforeExecute {
            executer.withStackTraceChecksDisabled()
            executer.expectDeprecationWarning("Cannot access output property 'outputDir' of task ':producer' (see --info log for details). " +
                "Accessing unreadable inputs or outputs has been deprecated. " +
                "This will fail with an error in Gradle 8.0. " +
                "Declare the task as untracked by using Task.doNotTrackState().")
        }

        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")

        buildFile generateProducerTask(false)

        buildFile("""
            tasks.register("producer", Producer) {
                outputs.cacheIf { true }
                outputDir = project.layout.buildDirectory.dir("root")
            }
        """)

        when:
        withBuildCache().run "producer", "--info"
        then:
        executedAndNotSkipped(":producer")
        outputContains("Cannot access output property 'outputDir' of task ':producer'")
        outputContains("java.io.UncheckedIOException: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

        when:
        unreadableDir.setReadable(true)
        assert rootDir.deleteDir()

        withBuildCache().run "producer", "--info"
        then:
        executedAndNotSkipped(":producer")
        outputContains("Cannot access output property 'outputDir' of task ':producer'")
        outputContains("java.io.UncheckedIOException: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

        cleanup:
        unreadableDir.setReadable(true)
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "task consuming unreadable content is not tracked"() {
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
        executer.expectDeprecationWarning("Cannot access input property 'inputDir' of task ':consumer' (see --info log for details). " +
            "Accessing unreadable inputs or outputs has been deprecated. " +
            "This will fail with an error in Gradle 8.0. " +
            "Declare the task as untracked by using Task.doNotTrackState().")
        run "consumer", "--info"
        then:
        executedAndNotSkipped(":consumer")
        outputContains("Task ':consumer' is not up-to-date because:")
        outputContains("Change tracking is disabled.")
        outputContains("Cannot access input property 'inputDir' of task ':consumer'")
        outputContains("java.io.UncheckedIOException: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

        cleanup:
        unreadableDir.setReadable(true)
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "task consuming unreadable content is not stored in cache"() {
        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")
        assert unreadableDir.mkdirs()
        assert unreadableDir.setReadable(false)

        buildFile generateConsumerTask(false)

        buildFile("""
            tasks.register("consumer", Consumer) {
                outputs.cacheIf { true }
                inputDir = project.layout.buildDirectory.dir("root")
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)

        when:
        executer.withStackTraceChecksDisabled()
        executer.expectDeprecationWarning("Cannot access input property 'inputDir' of task ':consumer' (see --info log for details). " +
            "Accessing unreadable inputs or outputs has been deprecated. " +
            "This will fail with an error in Gradle 8.0. " +
            "Declare the task as untracked by using Task.doNotTrackState().")
        withBuildCache().run "consumer", "--info"
        then:
        executedAndNotSkipped(":consumer")
        outputContains("Task ':consumer' is not up-to-date because:")
        outputContains("Change tracking is disabled.")
        outputContains("Caching disabled for task ':consumer' because:")
        outputContains("Cacheability was not determined")
        outputContains("Cannot access input property 'inputDir' of task ':consumer'")
        outputContains("java.io.UncheckedIOException: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

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

            @Untracked(because = 'For testing')
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
            @Untracked(because = 'For testing')
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
            ${untracked ? "@Untracked(because = 'For testing')" : ""}
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
            ${untracked ? "@Untracked(because = 'For testing')" : ""}
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

    static generateUntrackedIncrementalConsumerTask(Class<?> inputChangesType) {
        """
            @Untracked(because = "For testing")
            abstract class IncrementalConsumer extends DefaultTask {
                @SkipWhenEmpty
                @InputDirectory
                abstract DirectoryProperty getInputDir()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void execute(${inputChangesType.name} changes) {
                    assert changes != null
                    def unreadableDir = inputDir.get().dir("unreadable").asFile
                    assert !unreadableDir.canRead()
                    outputFile.get().asFile << "Executed"
                }
            }
        """
    }
}
