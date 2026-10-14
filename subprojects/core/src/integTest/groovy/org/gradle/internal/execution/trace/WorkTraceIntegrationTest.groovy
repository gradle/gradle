/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.execution.trace

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

class WorkTraceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile """
            abstract class Producer extends DefaultTask {
                @InputFile abstract RegularFileProperty getSource()
                @OutputDirectory abstract DirectoryProperty getOutputDir()
                @TaskAction void run() {
                    new File(outputDir.get().asFile, "produced.txt").text = source.get().asFile.text
                }
            }

            abstract class Consumer extends DefaultTask {
                @InputFiles abstract ConfigurableFileCollection getSourceFiles()
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction void run() {
                    outputFile.get().asFile.text = sourceFiles.asFileTree.files*.text.join("\\n")
                }
            }

            def producer = tasks.register("producer", Producer) {
                source = layout.projectDirectory.file("source.txt")
                outputDir = layout.buildDirectory.dir("produced")
            }
            def consumer = tasks.register("consumer", Consumer) {
                sourceFiles.from(producer.map { it.outputDir })
                outputFile = layout.buildDirectory.file("consumed.txt")
            }
            def cleanup = tasks.register("cleanup") {
                doLast { println("cleaning up") }
            }
            tasks.register("explicit") {
                dependsOn(consumer)
                finalizedBy(cleanup)
                doLast { println("explicit ran") }
            }
        """
        file("source.txt") << "hello"
    }

    def "writes a work trace to the default directory and describes the task graph"() {
        def planFile = file("build/reports/work-trace/plan.jsonl")
        def workFile = file("build/reports/work-trace/work.jsonl")

        when:
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=")

        then:
        output.contains("See the work trace at:")
        planFile.isFile()
        workFile.isFile()

        and: "the plan file holds the plan record"
        def plans = parse(planFile)
        plans*.kind == ["plan"]
        def plan = plans[0]
        plan.buildPath == ":"
        plan.requestedTaskPaths == [":explicit"]
        plan.tasks.size() == 4
        plan.tasks*.buildTreeTaskPath == [":cleanup", ":consumer", ":explicit", ":producer"]
        plan.tasks.find { it.buildTreeTaskPath == ":explicit" }.dependencies == [":consumer"]
        plan.tasks.find { it.buildTreeTaskPath == ":explicit" }.finalizedBy == [":cleanup"]

        and: "the work file starts with the projects record, mapping the root project to the test directory"
        def records = parse(workFile)
        records[0].kind == "projects"
        records[0].projects.find { it.project == ":" }.dir == testDirectory.absolutePath
        records.drop(1)*.kind.every { it == "task" }

        and:
        def tasks = records.findAll { it.kind == "task" }.collectEntries { [(it.buildTreeTaskPath): it] }
        tasks.keySet().sort() == [":cleanup", ":consumer", ":explicit", ":producer"]

        and:
        def consumer = tasks[":consumer"]
        consumer.scheduling.inputDependencies.size() == 1
        consumer.scheduling.inputDependencies[0].property == "sourceFiles"
        consumer.scheduling.inputDependencies[0].tasks == [":producer"]
        consumer.scheduling.explicitDependsOn == []

        and:
        def explicit = tasks[":explicit"]
        explicit.scheduling.requested
        explicit.scheduling.explicitDependsOn == [":consumer"]
        explicit.scheduling.inputDependencies == []
        !consumer.scheduling.requested
        !tasks[":producer"].scheduling.requested
        !tasks[":cleanup"].scheduling.requested

        and:
        tasks[":cleanup"].scheduling.finalizerOf == [":explicit"]

        and: "every scheduled task is reachable from the requested task"
        reachableFrom(":explicit", tasks) == [":cleanup", ":consumer", ":explicit", ":producer"] as Set

        and: "the producer records its input and output files with project-relative locations"
        def producer = tasks[":producer"]
        producer.implementation == "Producer"
        producer.inputsCaptured
        producer.inputs*.property == ["source"]
        producer.inputs[0].roots == [[project: ":", path: "source.txt"]]
        producer.outputs.size() == 1
        producer.outputs[0].property == "outputDir"
        producer.outputs[0].type == "DIRECTORY"
        producer.outputs[0].location == [project: ":", path: "build/produced"]
        producer.outputs[0].files == ["produced.txt"]

        and: "the consumer's input root is the producer's output location"
        consumer.inputsCaptured
        consumer.inputs*.property == ["sourceFiles"]
        consumer.inputs[0].roots == [producer.outputs[0].location]
        consumer.caching.key =~ /[0-9a-f]{32}/
        consumer.caching.disabledReasonCategory == "BUILD_CACHE_DISABLED"
        consumer.outputs[0].property == "outputFile"
        consumer.outputs[0].type == "FILE"
        consumer.outputs[0].location == [project: ":", path: "build/consumed.txt"]
        consumer.outputs[0].files == [""]

        and: "tasks without inputs and outputs are captured with empty inputs"
        explicit.inputsCaptured
        explicit.inputs == []
        explicit.outputs == []

        when: "nothing changed"
        def planBefore = planFile.text
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=")

        then: "the tasks with outputs are up-to-date, and the trace is identical"
        skipped(":producer", ":consumer")
        executedAndNotSkipped(":explicit", ":cleanup")
        planFile.text == planBefore
        sameRecords(parse(workFile), records)
    }

    def "captures inputs of tasks that Gradle does not snapshot otherwise, without changing their execution"() {
        buildFile """
            abstract class NoOutputs extends DefaultTask {
                @InputFile abstract RegularFileProperty getSource()
                @TaskAction void run() { println("no outputs: " + source.get().asFile.text) }
            }

            @UntrackedTask(because = "test")
            abstract class Untracked extends DefaultTask {
                @InputFile abstract RegularFileProperty getSource()
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction void run() { outputFile.get().asFile.text = source.get().asFile.text }
            }

            abstract class NoSource extends DefaultTask {
                @InputFiles @SkipWhenEmpty abstract ConfigurableFileCollection getSources()
                @InputFile abstract RegularFileProperty getConfig()
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction void run() { outputFile.get().asFile.text = "never" }
            }

            tasks.register("noOutputs", NoOutputs) {
                source = layout.projectDirectory.file("source.txt")
            }
            tasks.register("untracked", Untracked) {
                source = layout.projectDirectory.file("source.txt")
                outputFile = layout.buildDirectory.file("untracked.txt")
            }
            tasks.register("noSource", NoSource) {
                sources.from(layout.projectDirectory.dir("missing-sources"))
                config = layout.projectDirectory.file("source.txt")
                outputFile = layout.buildDirectory.file("noSource.txt")
            }
        """
        def workFile = file("build/reports/work-trace/work.jsonl")

        when:
        succeeds("noOutputs", "untracked", "noSource", "-Dorg.gradle.internal.work.trace=")

        then:
        executedAndNotSkipped(":noOutputs", ":untracked")
        skipped(":noSource")
        outputContains("Task :noSource NO-SOURCE")
        def records = parse(workFile)
        def tasks = records.findAll { it.kind == "task" }.collectEntries { [(it.buildTreeTaskPath): it] }

        and:
        tasks[":noOutputs"].inputsCaptured
        tasks[":noOutputs"].inputs*.property == ["source"]
        tasks[":noOutputs"].inputs[0].roots == [[project: ":", path: "source.txt"]]
        tasks[":noOutputs"].caching.disabledReason != null

        and:
        tasks[":untracked"].inputsCaptured
        tasks[":untracked"].inputs*.property == ["source"]
        tasks[":untracked"].inputs[0].roots == [[project: ":", path: "source.txt"]]
        tasks[":untracked"].outputs[0].location == [project: ":", path: "build/untracked.txt"]

        and: "the non-primary input of the NO-SOURCE task is captured"
        tasks[":noSource"].inputsCaptured
        tasks[":noSource"].inputs*.property.sort() == ["config", "sources"]
        tasks[":noSource"].inputs.find { it.property == "config" }.roots == [[project: ":", path: "source.txt"]]
        tasks[":noSource"].outputs[0].files == []
        !file("build/noSource.txt").exists()

        when: "run again"
        succeeds("noOutputs", "untracked", "noSource", "-Dorg.gradle.internal.work.trace=")

        then: "tasks without history still execute, and the trace is identical"
        executedAndNotSkipped(":noOutputs", ":untracked")
        skipped(":noSource")
        outputContains("Task :noSource NO-SOURCE")
        sameRecords(parse(workFile), records)
    }

    def "writes the plan and the projects record on a dry run"() {
        def planFile = file("build/reports/work-trace/plan.jsonl")
        def workFile = file("build/reports/work-trace/work.jsonl")

        when:
        succeeds("explicit", "--dry-run", "-Dorg.gradle.internal.work.trace=")

        then:
        parse(planFile)*.kind == ["plan"]
        parse(planFile)[0].requestedTaskPaths == [":explicit"]
        parse(workFile)*.kind == ["projects"]
        parse(workFile)[0].projects.find { it.project == ":" }.dir == testDirectory.absolutePath
        output.contains("See the work trace at:")
    }

    def "writes the work trace to an explicit directory"() {
        def traceDir = file("custom/trace")

        when:
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=${traceDir.absolutePath}")

        then:
        output.contains("See the work trace at:")
        traceDir.file("plan.jsonl").isFile()
        traceDir.file("work.jsonl").isFile()
        !file("build/reports/work-trace").exists()
        parse(traceDir.file("plan.jsonl"))*.kind == ["plan"]
        parse(traceDir.file("work.jsonl")).findAll { it.kind == "task" }*.buildTreeTaskPath.sort() == [":cleanup", ":consumer", ":explicit", ":producer"]
    }

    def "does not write a work trace when the option is not set"() {
        when:
        succeeds("explicit")

        then:
        !output.contains("See the work trace at:")
        !file("build/reports/work-trace").exists()
    }

    @Requires(
        value = TestExecutionPreconditions.IsConfigCached,
        reason = "Checks the configuration cache inputs of the work trace"
    )
    def "the option and the plan file are configuration cache inputs"() {
        def planFile = file("build/reports/work-trace/plan.jsonl")
        def workFile = file("build/reports/work-trace/work.jsonl")

        when: "first run stores the entry"
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=")
        def planBefore = planFile.text

        then:
        postBuildOutputContains("Configuration cache entry stored.")

        when: "unchanged run reuses the entry; the plan file is left untouched"
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=")

        then:
        postBuildOutputContains("Configuration cache entry reused.")
        planFile.text == planBefore
        parse(workFile)*.kind.count { it == "task" } == 4

        when: "the plan file is deleted"
        planFile.delete()
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=")

        then: "the entry is invalidated and the plan file written again"
        postBuildOutputContains("Configuration cache entry stored.")
        planFile.text == planBefore

        when: "the plan file is edited"
        planFile << "x"
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=")

        then:
        postBuildOutputContains("Configuration cache entry stored.")
        planFile.text == planBefore

        when: "and left alone"
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=")

        then:
        postBuildOutputContains("Configuration cache entry reused.")
        planFile.text == planBefore

        when: "the trace directory changes"
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=${file("elsewhere").absolutePath}")

        then:
        postBuildOutputContains("Configuration cache entry stored.")
        file("elsewhere/plan.jsonl").isFile()

        when: "the trace is turned off"
        succeeds("explicit")

        then:
        postBuildOutputContains("Configuration cache entry stored.")
        !output.contains("See the work trace at:")

        when: "and stays off"
        succeeds("explicit")

        then:
        postBuildOutputContains("Configuration cache entry reused.")
        !output.contains("See the work trace at:")

        when: "and is turned on again"
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=")

        then:
        postBuildOutputContains("Configuration cache entry stored.")
        output.contains("See the work trace at:")
    }

    /**
     * Task records are written as tasks finish, so independent tasks may appear in any order.
     */
    private static boolean sameRecords(List<Map> actual, List<Map> expected) {
        assert actual.findAll { it.kind != "task" } == expected.findAll { it.kind != "task" }
        assert byTask(actual) == byTask(expected)
        true
    }

    private static Map<String, Map> byTask(List<Map> records) {
        records.findAll { it.kind == "task" }.collectEntries { [(it.buildTreeTaskPath): it] }
    }

    private static List<Map> parse(TestFile traceFile) {
        def slurper = new JsonSlurper()
        traceFile.readLines().findAll { !it.isBlank() }.collect { slurper.parseText(it) as Map }
    }

    private static Set<String> reachableFrom(String start, Map<String, Map> tasks) {
        Set<String> seen = [] as Set
        List<String> queue = [start]
        while (!queue.isEmpty()) {
            def current = queue.remove(0)
            if (!seen.add(current)) {
                continue
            }
            def scheduling = tasks[current].scheduling
            queue.addAll(scheduling.explicitDependsOn)
            queue.addAll(scheduling.inputDependencies*.tasks.flatten())
            // finalizers: tasks whose finalizerOf names the current task
            queue.addAll(tasks.values().findAll { it.scheduling.finalizerOf.contains(current) }*.buildTreeTaskPath)
        }
        seen
    }
}
