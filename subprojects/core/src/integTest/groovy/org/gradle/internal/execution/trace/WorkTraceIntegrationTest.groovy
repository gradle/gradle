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
        plan.tasks*.id == [":cleanup", ":consumer", ":explicit", ":producer"]
        plan.tasks.find { it.id == ":explicit" }.dependencies == [":consumer"]
        plan.tasks.find { it.id == ":explicit" }.finalizedBy == [":cleanup"]

        and: "the work file holds the task records"
        def records = parse(workFile)
        records*.kind.every { it == "task" }

        and:
        def tasks = records.findAll { it.kind == "task" }.collectEntries { [(it.id): it] }
        tasks.keySet().sort() == [":cleanup", ":consumer", ":explicit", ":producer"]

        and:
        def consumer = tasks[":consumer"]
        consumer.scheduling.inputDependencies.size() == 1
        consumer.scheduling.inputDependencies[0].property == "sourceFiles"
        consumer.scheduling.inputDependencies[0].tasks == [":producer"]
        !consumer.scheduling.containsKey("explicitDependsOn")

        and:
        def explicit = tasks[":explicit"]
        explicit.scheduling.requested
        explicit.scheduling.explicitDependsOn == [":consumer"]
        !explicit.scheduling.containsKey("inputDependencies")
        !consumer.scheduling.containsKey("requested")
        !tasks[":producer"].scheduling.containsKey("requested")
        !tasks[":cleanup"].scheduling.containsKey("requested")

        and:
        tasks[":cleanup"].scheduling.finalizerOf == [":explicit"]

        and: "every scheduled task is reachable from the requested task"
        reachableFrom(":explicit", tasks) == [":cleanup", ":consumer", ":explicit", ":producer"] as Set

        and: "the producer records its input and output files with project-relative locations"
        def producer = tasks[":producer"]
        producer.implementation == "Producer"
        !producer.containsKey("inputsCapturingSkipped")
        producer.inputs*.property == ["source"]
        producer.inputs[0].roots == ["source.txt"]
        producer.outputs.size() == 1
        producer.outputs[0].property == "outputDir"
        producer.outputs[0].type == "DIRECTORY"
        producer.outputs[0].location == "build/produced"
        producer.outputs[0].files == ["produced.txt"]

        and: "the consumer's input root is the producer's output location"
        !consumer.containsKey("inputsCapturingSkipped")
        consumer.inputs*.property == ["sourceFiles"]
        consumer.inputs[0].roots == ["build/produced"]
        consumer.inputs[0].roots == [producer.outputs[0].location]
        !consumer.containsKey("caching")
        !consumer.containsKey("inputValueHashes")
        consumer.inputs.every { !it.containsKey("attributes") }
        consumer.outputs[0].property == "outputFile"
        consumer.outputs[0].type == "FILE"
        consumer.outputs[0].location == "build/consumed.txt"
        consumer.outputs[0].files == [""]

        and: "tasks without outputs are not snapshotted by Gradle, so they have no inputs"
        explicit.inputsCapturingSkipped == true
        !explicit.containsKey("inputs")
        !explicit.containsKey("caching")
        !explicit.containsKey("outputs")

        when: "nothing changed"
        def planBefore = planFile.text
        succeeds("explicit", "-Dorg.gradle.internal.work.trace=")

        then: "the tasks with outputs are up-to-date, and the trace is identical"
        skipped(":producer", ":consumer")
        executedAndNotSkipped(":explicit", ":cleanup")
        planFile.text == planBefore
        sameRecords(parse(workFile), records)
    }

    def "records tasks whose inputs Gradle does not snapshot, without inputs"() {
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
        def tasks = byTask(records)
        tasks.keySet().sort() == [":noOutputs", ":noSource", ":untracked"]

        and: "each task has a record with its scheduling and declared outputs, but no inputs"
        tasks.values().every { it.inputsCapturingSkipped == true && !it.containsKey("inputs") && it.scheduling != null && it.scheduling.requested }
        !tasks[":noOutputs"].containsKey("outputs")
        tasks[":untracked"].outputs[0].location == "build/untracked.txt"
        tasks[":noSource"].outputs[0].location == "build/noSource.txt"
        !tasks[":noSource"].outputs[0].containsKey("files")
        !file("build/noSource.txt").exists()

        when: "run again"
        succeeds("noOutputs", "untracked", "noSource", "-Dorg.gradle.internal.work.trace=")

        then: "the build behaves as before, and the trace is identical"
        executedAndNotSkipped(":noOutputs", ":untracked")
        skipped(":noSource")
        outputContains("Task :noSource NO-SOURCE")
        sameRecords(parse(workFile), records)
    }

    def "traces only the root work graph, not the work graphs of build logic builds"() {
        withBuildLogicPlugin()
        def planFile = file("build/reports/work-trace/plan.jsonl")
        def workFile = file("build/reports/work-trace/work.jsonl")

        when:
        succeeds("explicit", "fromPlugin", "-Dorg.gradle.internal.work.trace=")

        then: "the build logic build ran its own work graph"
        executedAndNotSkipped(":build-logic:compileJava", ":build-logic:jar", ":explicit", ":fromPlugin")

        and: "the plan holds the root work graph only"
        def plans = parse(planFile)
        plans*.kind == ["plan"]
        plans[0].buildPath == ":"
        plans[0].requestedTaskPaths == [":explicit", ":fromPlugin"]
        plans[0].tasks*.id == [":cleanup", ":consumer", ":explicit", ":fromPlugin", ":producer"]

        and: "the work file has no build logic task"
        def records = parse(workFile)
        byTask(records).keySet().sort() == [":cleanup", ":consumer", ":explicit", ":fromPlugin", ":producer"]
        records.findAll { it.kind == "task" }.every { !it.id.startsWith(":build-logic:") }

        when: "run again"
        def planBefore = planFile.text
        succeeds("explicit", "fromPlugin", "-Dorg.gradle.internal.work.trace=")

        then:
        planFile.text == planBefore
        sameRecords(parse(workFile), records)
    }

    @Requires(
        value = TestExecutionPreconditions.IsConfigCached,
        reason = "Checks the plan file across configuration cache runs"
    )
    def "plan file stays up to date on a repeated configuration cache run with an included build"() {
        withBuildLogicPlugin()
        settingsFile << """
            includeBuild("lib")
        """
        file("lib/settings.gradle") << 'rootProject.name = "lib"'
        file("lib/build.gradle") << """
            abstract class LibTask extends DefaultTask {
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction void run() { outputFile.get().asFile.text = "lib" }
            }
            tasks.register("libTask", LibTask) {
                outputFile = layout.buildDirectory.file("lib.txt")
            }
        """
        buildFile << """
            tasks.register("consumeLib") {
                dependsOn(gradle.includedBuild("lib").task(":libTask"))
                doLast { println("consumed lib") }
            }
        """
        def planFile = file("build/reports/work-trace/plan.jsonl")
        def workFile = file("build/reports/work-trace/work.jsonl")

        when:
        succeeds("consumeLib", "-Dorg.gradle.internal.work.trace=")

        then:
        postBuildOutputContains("Configuration cache entry stored.")
        executedAndNotSkipped(":lib:libTask", ":consumeLib")

        and: "one plan record per build of the root work graph"
        def plans = parse(planFile)
        plans*.buildPath == [":", ":lib"]
        plans[0].requestedTaskPaths == [":consumeLib"]
        plans[0].tasks*.id == [":consumeLib"]
        plans[0].tasks[0].dependencies == [":lib:libTask"]
        plans[1].tasks*.id == [":lib:libTask"]

        and: "task records of both builds, none of the plugin build"
        def records = parse(workFile)
        byTask(records).keySet().sort() == [":consumeLib", ":lib:libTask"]
        byTask(records)[":consumeLib"].scheduling.explicitDependsOn == [":lib:libTask"]
        byTask(records)[":lib:libTask"].outputs[0].location == "lib/build/lib.txt"
        plans.every { plan -> plan.tasks.every { !it.id.startsWith(":build-logic:") } }

        when:
        def planBefore = planFile.text
        succeeds("consumeLib", "-Dorg.gradle.internal.work.trace=")

        then:
        postBuildOutputContains("Configuration cache entry reused.")
        planFile.text == planBefore
        sameRecords(parse(workFile), records)
    }

    def "writes the plan and an empty work file on a dry run"() {
        def planFile = file("build/reports/work-trace/plan.jsonl")
        def workFile = file("build/reports/work-trace/work.jsonl")

        when:
        succeeds("explicit", "--dry-run", "-Dorg.gradle.internal.work.trace=")

        then:
        parse(planFile)*.kind == ["plan"]
        parse(planFile)[0].requestedTaskPaths == [":explicit"]
        workFile.isFile()
        workFile.text.isEmpty()
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
        parse(traceDir.file("work.jsonl")).findAll { it.kind == "task" }*.id.sort() == [":cleanup", ":consumer", ":explicit", ":producer"]
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
        records.findAll { it.kind == "task" }.collectEntries { [(it.id): it] }
    }

    /**
     * A build logic build (pluginManagement includeBuild) providing "my.plugin", which registers the "fromPlugin" task,
     * applied by the root project.
     */
    private void withBuildLogicPlugin() {
        settingsFile << """
            pluginManagement { includeBuild("build-logic") }
        """
        file("build-logic/settings.gradle") << 'rootProject.name = "build-logic"'
        file("build-logic/build.gradle") << """
            plugins { id("java-gradle-plugin") }
            gradlePlugin {
                plugins {
                    p {
                        id = "my.plugin"
                        implementationClass = "MyPlugin"
                    }
                }
            }
        """
        file("build-logic/src/main/java/MyPlugin.java") << """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            public class MyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("fromPlugin", task -> task.doLast(t -> System.out.println("from plugin")));
                }
            }
        """
        buildFile.text = 'plugins { id("my.plugin") }\n' + buildFile.text
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
            // empty arrays are omitted from the trace, so every list may be missing
            def scheduling = tasks[current].scheduling
            queue.addAll(scheduling.explicitDependsOn ?: [])
            queue.addAll((scheduling.inputDependencies ?: [])*.tasks.flatten())
            // finalizers: tasks whose finalizerOf names the current task
            queue.addAll(tasks.values().findAll { (it.scheduling.finalizerOf ?: []).contains(current) }*.id)
        }
        seen
    }
}
