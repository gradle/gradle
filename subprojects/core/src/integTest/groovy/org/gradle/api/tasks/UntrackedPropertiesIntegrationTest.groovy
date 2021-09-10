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
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.work.InputChanges

class UntrackedPropertiesIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture, ValidationMessageChecker {

    def "task with untracked #properties is not up-to-date"() {
        buildFile("""
            abstract class MyTask extends DefaultTask {
                ${properties == "inputs" ? "@Untracked" : ""}
                @InputFile
                abstract RegularFileProperty getInputFile()
                ${properties == "outputs" ? "@Untracked" : ""}
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
        outputContains("Task has untracked properties.")

        when:
        run("myTask", "--info")
        then:
        executedAndNotSkipped(":myTask")
        outputContains("Task ':myTask' is not up-to-date because:")
        outputContains("Task has untracked properties.")

        where:
        properties << ["inputs", "outputs"]
    }

    def "fails when incremental task has untracked properties using #inputChangesType.simpleName"() {
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

    def "can register untracked #properties via the runtime API"() {
        buildFile("""
            tasks.register("myTask") {
                def inputFile = file("input.txt")
                inputs.file(inputFile)
                    .withPropertyName("inputFile")
                    ${properties == "inputs" ? ".untracked()" : ""}
                def outputFile = project.layout.buildDirectory.file("output.txt")
                outputs.file(outputFile)
                    .withPropertyName("outputFile")
                    ${(properties == "outputs" ? ".untracked()" : "")}
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
        outputContains("Task has untracked properties.")

        where:
        properties << ["inputs", "outputs"]
    }

    def "task with untracked #properties is not cached"() {
        buildFile("""
            @CacheableTask
            abstract class MyTask extends DefaultTask {
                ${properties == "inputs" ? "@Untracked" : ""}
                @InputFile
                @PathSensitive(PathSensitivity.RELATIVE)
                abstract RegularFileProperty getInputFile()
                ${properties == "outputs" ? "@Untracked" : ""}
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
        outputContains(expectedMessage)

        where:
        properties | expectedMessage
        "inputs"   | "Input property 'inputFile' is untracked"
        "outputs"  | "Output property 'outputFile' is untracked"
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "tasks can produce and consume unreadable content via untracked properties"() {
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
    def "task producing unreadable content via tracked property is not stored in execution history"() {
        executer.beforeExecute {
            executer.withStackTraceChecksDisabled()
            executer.expectDeprecationWarning("Cannot access output property 'outputDir' of task ':producer' (see --info log for details). " +
                "Accessing unreadable inputs or outputs has been deprecated. " +
                "This will fail with an error in Gradle 8.0. " +
                "Declare the property as untracked.")
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

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "task producing unreadable content via tracked property is not stored in cache"() {
        executer.beforeExecute {
            executer.withStackTraceChecksDisabled()
            executer.expectDeprecationWarning("Cannot access output property 'outputDir' of task ':producer' (see --info log for details). " +
                "Accessing unreadable inputs or outputs has been deprecated. " +
                "This will fail with an error in Gradle 8.0. " +
                "Declare the property as untracked.")
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
    def "task consuming unreadable content via tracked property is not tracked"() {
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
            "Declare the property as untracked.")
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
    def "task consuming unreadable content via tracked property is not stored in cache"() {
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
            "Declare the property as untracked.")
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

    def "untracked optional absent output properties are ignored"() {
        buildFile("""
            tasks.register("myTask") {
                def inputFile = file("input.txt")
                inputs.file(inputFile)
                    .withPropertyName("inputFile")
                def outputFile = project.layout.buildDirectory.file("output.txt")
                outputs.file(outputFile)
                    .withPropertyName("outputFile")
                outputs.file({ null })
                    .withPropertyName("optionalOutputFile")
                    .optional()
                    .untracked()
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
        run("myTask")
        then:
        skipped(":myTask")
    }

    def "untracked optional absent input properties cause the task to be out-of-date"() {
        buildFile("""
            tasks.register("myTask") {
                def inputFile = file("input.txt")
                inputs.file(inputFile)
                    .withPropertyName("inputFile")
                inputs.file({ null })
                    .withPropertyName("optionalUntrackedInputFile")
                    .optional()
                    .untracked()
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
        outputContains("Task has untracked properties.")
    }

    def "does not clean up stale outputs for untracked output properties"() {
        def untrackedStaleFile = file("build/untracked/stale-output-file").createFile()
        def untrackedOutputFile = file("build/untracked/output.txt")
        def trackedStaleFile = file("build/tracked/stale-output-file").createFile()
        def trackedOutputFile = file("build/tracked/output.txt")

        buildFile("""
            apply plugin: 'base'

            abstract class Producer extends DefaultTask {
                @Untracked
                @OutputDirectory
                abstract DirectoryProperty getUntrackedOutputDirectory()

                @OutputDirectory
                abstract DirectoryProperty getTrackedOutputDirectory()

                @TaskAction
                void writeFile() {
                    writeFile(trackedOutputDirectory)
                    writeFile(untrackedOutputDirectory)
                }

                static void writeFile(DirectoryProperty dir) {
                    def outputFile = dir.file("output.txt").get().asFile
                    outputFile.text = "Produced file"
                }
            }

            tasks.register("producer", Producer) {
                untrackedOutputDirectory = project.layout.buildDirectory.dir('untracked')
                trackedOutputDirectory = project.layout.buildDirectory.dir('tracked')
            }
        """)

        when:
        run "producer"
        then:
        executedAndNotSkipped(":producer")

        trackedOutputFile.text == "Produced file"
        !trackedStaleFile.exists()

        untrackedOutputFile.text == "Produced file"
        untrackedStaleFile.exists()
    }

    @ValidationTestFor(
        ValidationProblemId.INCOMPATIBLE_ANNOTATIONS
    )
    def "fails when @Untracked is applied together with #annotation.simpleName"() {
        expectReindentedValidationMessage()
        buildFile("""
            abstract class InvalidTask extends DefaultTask {
                @Untracked
                @${annotation.simpleName}
                abstract ${inputType} getInput()

                @TaskAction
                void doStuff() {}
            }

            tasks.register("invalid", InvalidTask)
        """)

        when:
        fails("invalid")
        then:
        failureDescriptionContains(
            incompatibleAnnotations {
                type('InvalidTask').property('input')
                annotatedWith(Untracked.simpleName)
                incompatibleWith(annotation.simpleName)
                includeLink()
            }
        )

        where:
        annotation | inputType
        Input      | 'Property<String>'
        Destroys   | 'ConfigurableFileCollection'
        LocalState | 'ConfigurableFileCollection'
    }

    def "invalidates the VFS for untracked output directories"() {
        buildFile("""
            abstract class ProducerWithUntrackedOutput extends DefaultTask {
                @Untracked
                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void createOutput() {
                    outputDir.file("untracked.txt").get().asFile.text = "untracked"
                }
            }

            abstract class ProducerWithTrackedOutput extends DefaultTask {
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

            def producerTracked = tasks.register("producerTracked", ProducerWithTrackedOutput) {
                outputDir = project.layout.buildDirectory.dir("shared-output")
            }
            def producerUntracked = tasks.register("producerUntracked", ProducerWithUntrackedOutput) {
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
            abstract class Producer extends DefaultTask {
                ${untracked ? "@Untracked" : ""}
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
            abstract class Consumer extends DefaultTask {
                ${untracked ? "@Untracked" : ""}
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
            abstract class IncrementalConsumer extends DefaultTask {
                @SkipWhenEmpty
                @InputDirectory
                abstract DirectoryProperty getInputDir()

                @Untracked
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
