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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.FileSystemWatchingHelper
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

import static org.hamcrest.CoreMatchers.anyOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith


class TaskLifecycleListenerIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer blockingServer = new BlockingHttpServer()

    def "listener is notified around task actions"() {
        buildFile """
            abstract class PrintingListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    println "before \${task.path}"
                }

                void afterExecution(Task task, TaskState state) {
                    println "after \${task.path} executed=\${state.executed} failure=\${state.failure}"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(PrintingListener)
                doLast { println "action \${path}" }
            }
        """

        when:
        run "foo"

        then:
        result.groupedOutput.task(":foo").output == """before :foo
action :foo
after :foo executed=true failure=null"""
    }

    def "listener registered on task collection receives typed task"() {
        buildFile """
            abstract class GreetingTask extends DefaultTask {
                @Input
                abstract Property<String> getGreeting()

                @TaskAction
                void greet() {}
            }

            abstract class GreetingListener implements TaskLifecycleListener<GreetingTask> {
                void beforeExecution(GreetingTask task) {
                    println "before \${task.path} greeting=\${task.greeting.get()}"
                }
            }

            tasks.withType(GreetingTask).addLifecycleListener(GreetingListener)

            tasks.register("hello", GreetingTask) { greeting = "hello" }
            tasks.register("other")
        """

        when:
        run "hello", "other"

        then:
        outputContains("before :hello greeting=hello")
        outputDoesNotContain("before :other")
    }

    def "listener types are checked by the Kotlin compiler"() {
        buildKotlinFile """
            abstract class GreetingTask : DefaultTask() {
                @TaskAction
                fun greet() {}
            }

            abstract class AnyTaskListener : TaskLifecycleListener<Task> {
                override fun beforeExecution(task: Task) {
                    println("any listener \${task.path}")
                }
            }

            abstract class GreetingListener : TaskLifecycleListener<GreetingTask> {
                override fun beforeExecution(task: GreetingTask) {
                    println("greeting listener \${task.path}")
                }
            }

            abstract class AnyTaskOnTypedCollectionListener : TaskLifecycleListener<Task> {
                override fun beforeExecution(task: Task) {
                    println("any listener on typed collection \${task.path}")
                }
            }

            tasks.register<GreetingTask>("hello") {
                addLifecycleListener(AnyTaskListener::class.java)
            }
            tasks.withType<GreetingTask>().addLifecycleListener(GreetingListener::class.java)
            tasks.withType<GreetingTask>().addLifecycleListener(AnyTaskOnTypedCollectionListener::class.java)
        """

        when:
        run "hello"

        then:
        outputContains("any listener :hello")
        outputContains("greeting listener :hello")
        outputContains("any listener on typed collection :hello")
    }

    def "listener for a specific task type can be registered on a matching task in Kotlin"() {
        buildKotlinFile """
            abstract class GreetingTask : DefaultTask() {
                @TaskAction
                fun greet() {}
            }

            abstract class GreetingListener : TaskLifecycleListener<GreetingTask> {
                override fun beforeExecution(task: GreetingTask) {
                    println("greeting listener \${task.path}")
                }
            }

            tasks.register<GreetingTask>("hello") {
                addLifecycleListener(GreetingListener::class.java)
            }
        """

        when:
        run "hello"

        then:
        outputContains("greeting listener :hello")
    }

    def "listener for a specific task type is rejected at registration on other tasks in Kotlin"() {
        buildKotlinFile """
            abstract class GreetingTask : DefaultTask() {
                @TaskAction
                fun greet() {}
            }

            abstract class GreetingListener : TaskLifecycleListener<GreetingTask>

            tasks.register("other") {
                addLifecycleListener(GreetingListener::class.java)
            }
        """

        when:
        fails "other"

        then:
        failure.assertThatCause(containsString("the listener accepts tasks of type"))
        failure.assertThatCause(containsString("GreetingTask, but the task is of type org.gradle.api.DefaultTask."))
    }

    def "listeners of tasks executing in parallel are notified concurrently"() {
        blockingServer.start()
        createDirs("a", "b")
        settingsFile """
            include("a", "b")

            abstract class BlockingListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    def projectName = task.path.split(':')[1]
                    ${blockingServer.callFromBuildUsingExpression("projectName")}
                }
            }

            gradle.lifecycle.beforeProject {
                tasks.register("work") {
                    addLifecycleListener(BlockingListener)
                }
            }
        """

        expect:
        blockingServer.expectConcurrent("a", "b")
        run ":a:work", ":b:work", "--parallel", "--max-workers=2"
    }

    def "listener is notified on the thread executing the task"() {
        buildFile """
            abstract class ThreadListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    println "before thread \${Thread.currentThread().id}"
                }

                void afterExecution(Task task, TaskState state) {
                    println "after thread \${Thread.currentThread().id}"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(ThreadListener)
                doLast {
                    println "action thread \${Thread.currentThread().id}"
                }
            }
        """

        when:
        run "foo"

        then:
        def threads = ["before", "action", "after"].collect { phase ->
            output.readLines().find { it.startsWith("$phase thread ") }.tokenize(" ").last()
        }
        threads.unique().size() == 1
    }

    def "new listener instance is created for each task execution"() {
        buildFile """
            abstract class TimingListener implements TaskLifecycleListener<Task> {
                private String startedBy

                void beforeExecution(Task task) {
                    println "fresh \${task.path} = \${startedBy == null}"
                    startedBy = task.path
                }

                void afterExecution(Task task, TaskState state) {
                    println "paired \${startedBy} -> \${task.path}"
                }
            }

            tasks.configureEach { addLifecycleListener(TimingListener) }
            tasks.register("a")
            tasks.register("b") { dependsOn("a") }
        """

        when:
        run "b"

        then:
        outputContains("fresh :a = true")
        outputContains("fresh :b = true")
        outputContains("paired :a -> :a")
        outputContains("paired :b -> :b")
    }

    def "listener is notified for #outcome task"() {
        buildFile """
            abstract class PrintingListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    println "before \${task.path}"
                }

                void afterExecution(Task task, TaskState state) {
                    println "after \${task.path}"
                }
            }

            abstract class CopyTask extends DefaultTask {
                @InputFiles @SkipWhenEmpty
                abstract ConfigurableFileCollection getSources()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                void run() { output.get().asFile.text = "done" }
            }

            tasks.register("upToDate", CopyTask) {
                sources.from("input.txt")
                output = layout.buildDirectory.file("upToDate.txt")
            }
            tasks.register("noSource", CopyTask) {
                output = layout.buildDirectory.file("noSource.txt")
            }
            tasks.register("skipped") {
                onlyIf { false }
            }
            tasks.configureEach { addLifecycleListener(PrintingListener) }
        """
        file("input.txt").text = "input"

        when:
        run task
        run task

        then:
        result.groupedOutput.task(":$task").outcome == outcome
        result.groupedOutput.task(":$task").output == "before :$task\nafter :$task"

        where:
        task       | outcome
        "upToDate" | "UP-TO-DATE"
        "noSource" | "NO-SOURCE"
        "skipped"  | "SKIPPED"
    }

    def "failure in beforeExecution fails the task and skips its actions"() {
        buildFile """
            abstract class PrintingListener implements TaskLifecycleListener<Task> {
                void afterExecution(Task task, TaskState state) {
                    println "after \${task.path} failure=\${state.failure?.cause?.message}"
                }
            }

            abstract class FailingListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    throw new RuntimeException("before failure")
                }

                void afterExecution(Task task, TaskState state) {
                    println "should not be notified"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(PrintingListener)
                addLifecycleListener(FailingListener)
                doLast { println "task action ran" }
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasDescription("Execution failed for task ':foo'")
        failure.assertHasCause("before failure")
        outputDoesNotContain("task action ran")
        outputDoesNotContain("should not be notified")
        outputContains("after :foo failure=before failure")
    }

    def "failure to create listener fails the task and skips its actions"() {
        buildFile """
            abstract class BrokenListener implements TaskLifecycleListener<Task> {
                BrokenListener() {
                    throw new RuntimeException("broken constructor")
                }
            }

            tasks.register("foo") {
                addLifecycleListener(BrokenListener)
                doLast { println "task action ran" }
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasDescription("Execution failed for task ':foo'")
        failure.assertHasCause("broken constructor")
        outputDoesNotContain("task action ran")
    }

    def "failures of task action and afterExecution are both reported"() {
        buildFile """
            abstract class FailingListener implements TaskLifecycleListener<Task> {
                void afterExecution(Task task, TaskState state) {
                    throw new RuntimeException("after failure")
                }
            }

            tasks.register("foo") {
                addLifecycleListener(FailingListener)
                doLast { throw new RuntimeException("action failure") }
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasCause("action failure")
        failure.assertHasCause("after failure")
    }

    def "failure in afterExecution fails the task"() {
        buildFile """
            abstract class FailingListener implements TaskLifecycleListener<Task> {
                void afterExecution(Task task, TaskState state) {
                    throw new RuntimeException("after failure")
                }
            }

            abstract class PrintingListener implements TaskLifecycleListener<Task> {
                void afterExecution(Task task, TaskState state) {
                    println "after \${task.path}"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(FailingListener)
                addLifecycleListener(PrintingListener)
                doLast { println "task action ran" }
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasDescription("Execution failed for task ':foo'")
        failure.assertHasCause("after failure")
        outputContains("task action ran")
        outputContains("after :foo")
    }

    def "afterExecution receives task action failure"() {
        buildFile """
            abstract class PrintingListener implements TaskLifecycleListener<Task> {
                void afterExecution(Task task, TaskState state) {
                    println "after \${task.path} failure=\${state.failure?.cause?.message}"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(PrintingListener)
                doLast { throw new RuntimeException("action failure") }
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasCause("action failure")
        outputContains("after :foo failure=action failure")
    }

    def "listener can use injected services and build services without declaring their usage"() {
        settingsFile """
            enableFeaturePreview "\${org.gradle.api.internal.FeaturePreviews.Feature.INTERNAL_BUILD_SERVICE_USAGE}"
        """
        buildFile """
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.api.services.ServiceReference
            import javax.inject.Inject
            import java.util.concurrent.atomic.AtomicInteger

            abstract class Counter implements BuildService<BuildServiceParameters.None> {
                private final AtomicInteger count = new AtomicInteger()

                int increment() { count.incrementAndGet() }
            }

            gradle.sharedServices.registerIfAbsent("counter", Counter)

            abstract class CountingListener implements TaskLifecycleListener<Task> {
                @ServiceReference("counter")
                abstract Property<Counter> getCounter()

                @Inject
                abstract ProviderFactory getProviders()

                void afterExecution(Task task, TaskState state) {
                    println "count \${task.path} = \${counter.get().increment()} \${providers.provider { 'injected' }.get()}"
                }
            }

            tasks.configureEach { addLifecycleListener(CountingListener) }
            tasks.register("a")
            tasks.register("b") { dependsOn("a") }
        """

        when:
        run "b"

        then:
        outputContains("count :a = 1 injected")
        outputContains("count :b = 2 injected")
    }

    def "listener can use build service without injecting other services"() {
        buildFile """
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.api.services.ServiceReference

            abstract class Greeter implements BuildService<BuildServiceParameters.None> {
                String greet(String path) { "hello \$path" }
            }

            gradle.sharedServices.registerIfAbsent("greeter", Greeter)

            abstract class GreetingListener implements TaskLifecycleListener<Task> {
                @ServiceReference("greeter")
                abstract Property<Greeter> getGreeter()

                void beforeExecution(Task task) {
                    println greeter.get().greet(task.path)
                }
            }

            tasks.register("foo") {
                addLifecycleListener(GreetingListener)
            }
        """

        when:
        run "foo"

        then:
        outputContains("hello :foo")
    }

    def "listener can reference build service by type"() {
        buildFile """
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.api.services.ServiceReference

            abstract class Greeter implements BuildService<BuildServiceParameters.None> {
                String greet(String path) { "hello \$path" }
            }

            gradle.sharedServices.registerIfAbsent("greeter", Greeter)

            abstract class GreetingListener implements TaskLifecycleListener<Task> {
                @ServiceReference
                abstract Property<Greeter> getGreeter()

                void beforeExecution(Task task) {
                    println greeter.get().greet(task.path)
                }
            }

            tasks.register("foo") {
                addLifecycleListener(GreetingListener)
            }
        """

        when:
        run "foo"

        then:
        outputContains("hello :foo")
    }

    def "listener can reference optional build service that is not registered"() {
        buildFile """
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.api.services.ServiceReference

            abstract class Greeter implements BuildService<BuildServiceParameters.None> {}

            abstract class GreetingListener implements TaskLifecycleListener<Task> {
                @Optional
                @ServiceReference("greeter")
                abstract Property<Greeter> getGreeter()

                void beforeExecution(Task task) {
                    println "greeter present: \${greeter.present}"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(GreetingListener)
            }
        """

        when:
        run "foo"

        then:
        outputContains("greeter present: false")
    }

    def "listener fails when referenced build service is not registered"() {
        buildFile """
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.api.services.ServiceReference

            abstract class Greeter implements BuildService<BuildServiceParameters.None> {}

            abstract class GreetingListener implements TaskLifecycleListener<Task> {
                @ServiceReference("greeter")
                abstract Property<Greeter> getGreeter()

                void beforeExecution(Task task) {
                    greeter.get()
                }
            }

            tasks.register("foo") {
                addLifecycleListener(GreetingListener)
                doLast { println "task action ran" }
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasDescription("Execution failed for task ':foo'")
        outputDoesNotContain("task action ran")
    }

    def "listener can be registered on all tasks in Kotlin DSL"() {
        buildKotlinFile """
            abstract class PrintingListener : TaskLifecycleListener<Task> {
                override fun beforeExecution(task: Task) {
                    println("before \${task.path}")
                }
            }

            tasks.addLifecycleListener(PrintingListener::class.java)
            tasks.register("foo")
        """

        when:
        run "foo"

        then:
        outputContains("before :foo")
    }

    def "registering the same listener type twice has no effect"() {
        buildFile """
            abstract class PrintingListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    println "before \${task.path}"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(PrintingListener)
                addLifecycleListener(PrintingListener)
            }
        """

        when:
        run "foo"

        then:
        result.groupedOutput.task(":foo").output == "before :foo"
    }

    def "cannot register listener after task has started executing"() {
        buildFile """
            abstract class PrintingListener implements TaskLifecycleListener<Task> {}

            tasks.register("foo") {
                doLast { addLifecycleListener(PrintingListener) }
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasCause("Cannot call Task.addLifecycleListener(Class) on task ':foo' after task has started execution.")
    }

    def "beforeExecution is called before task inputs are snapshotted"() {
        buildFile """
            abstract class CopyTask extends DefaultTask {
                @InputFile
                abstract RegularFileProperty getInput()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                void run() {
                    output.get().asFile.text = input.get().asFile.text
                }
            }
            abstract class OverwritingListener implements TaskLifecycleListener<CopyTask> {
                void beforeExecution(CopyTask task) {
                    task.input.get().asFile.text = "from listener"
                }
            }

            tasks.register("copy", CopyTask) {
                input = file("input.txt")
                output = layout.buildDirectory.file("output.txt")
                addLifecycleListener(OverwritingListener)
            }
        """
        file("input.txt").text = "initial"

        when:
        run "copy"

        then:
        executedAndNotSkipped(":copy")
        file("build/output.txt").text == "from listener"

        when: "the input written by the listener was snapshotted in the first run"
        run "copy"

        then:
        skipped(":copy")
    }

    def "beforeExecution cannot mutate the task: #description"() {
        buildFile """
            abstract class MutatingListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    $mutation
                }
            }

            tasks.register("other")
            tasks.register("foo") {
                addLifecycleListener(MutatingListener)
                doLast { println "task action ran" }
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasDescription("Execution failed for task ':foo'")
        failure.assertHasCause("Cannot call $method on task ':foo' from a task lifecycle listener. Only the values of task inputs can be changed.")
        outputDoesNotContain("task action ran")

        where:
        description           | mutation                             | method
        "declare input"       | 'task.inputs.property("x", "y")'     | "TaskInputs.property(String, Object)"
        "declare output"      | 'task.outputs.file("out.txt")'       | "TaskOutputs.file(Object)"
        "add dependency"      | 'task.dependsOn("other")'            | "Task.dependsOn(Object...)"
        "add action"          | 'task.doFirst { }'                   | "Task.doFirst(Closure)"
        "change skip"         | 'task.enabled = false'               | "Task.setEnabled(boolean)"
    }

    def "beforeExecution can change the value of a task input"() {
        buildFile """
            abstract class GreetingTask extends DefaultTask {
                @Input
                abstract Property<String> getGreeting()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                void greet() {
                    output.get().asFile.text = greeting.get()
                }
            }

            abstract class GreetingListener implements TaskLifecycleListener<GreetingTask> {
                void beforeExecution(GreetingTask task) {
                    task.greeting.set("from listener")
                }
            }

            tasks.register("greet", GreetingTask) {
                greeting = "from configuration"
                output = layout.buildDirectory.file("greeting.txt")
                addLifecycleListener(GreetingListener)
            }
        """

        when:
        run "greet"

        then:
        executedAndNotSkipped(":greet")
        file("build/greeting.txt").text == "from listener"

        when: "the value set by the listener was fingerprinted"
        run "greet"

        then:
        skipped(":greet")
    }

    def "beforeExecution cannot change the location of a task output"() {
        buildFile """
            import javax.inject.Inject

            abstract class GreetingTask extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                void greet() {
                    output.get().asFile.text = "hello"
                }
            }

            abstract class RelocatingListener implements TaskLifecycleListener<GreetingTask> {
                @Inject
                abstract ProjectLayout getLayout()

                void beforeExecution(GreetingTask task) {
                    task.output.set(layout.buildDirectory.file("elsewhere.txt"))
                }
            }

            tasks.register("greet", GreetingTask) {
                output = layout.buildDirectory.file("greeting.txt")
                addLifecycleListener(RelocatingListener)
            }
        """

        when:
        fails "greet"

        then:
        failure.assertHasDescription("Execution failed for task ':greet'")
        failure.assertHasCause("The value for task ':greet' property 'output' is final and cannot be changed any further.")
        !file("build/greeting.txt").exists()
        !file("build/elsewhere.txt").exists()
    }

    def "beforeExecution changing an input to files of another task does not add a task dependency"() {
        buildFile """
            import javax.inject.Inject

            abstract class ConsumerTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getSources()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                void consume() {
                    output.get().asFile.text = "consumed"
                }
            }

            abstract class WiringListener implements TaskLifecycleListener<ConsumerTask> {
                @Inject
                abstract ProjectLayout getLayout()

                void beforeExecution(ConsumerTask task) {
                    task.sources.from(layout.buildDirectory.file("produced.txt"))
                }
            }

            tasks.register("producer") {
                def outputFile = layout.buildDirectory.file("produced.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.get().asFile.text = "produced"
                }
            }

            tasks.register("consumer", ConsumerTask) {
                output = layout.buildDirectory.file("consumed.txt")
                addLifecycleListener(WiringListener)
            }
        """

        when:
        fails "producer", "consumer"

        then:
        // Producer and consumer can run in parallel with the configuration cache, and each report the problem
        failure.assertThatAllDescriptions(anyOf(
            startsWith("A problem was found with the configuration of task ':producer'"),
            startsWith("A problem was found with the configuration of task ':consumer'")
        ))
        failure.assertHasErrorOutput("Task ':consumer' uses this output of task ':producer' without declaring an explicit or implicit dependency.")
    }

    def "registering a listener does not affect up-to-date checks and the build cache"() {
        settingsFile << new TestBuildCache(file("cache-dir")).localCacheConfiguration()
        buildFile """
            @CacheableTask
            abstract class CopyTask extends DefaultTask {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                abstract RegularFileProperty getInput()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                void run() {
                    output.get().asFile.text = input.get().asFile.text
                }
            }

            abstract class PrintingListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    println "listener notified"
                }
            }

            tasks.register("copy", CopyTask) {
                input = file("input.txt")
                output = layout.buildDirectory.file("output.txt")
                if (providers.gradleProperty("withListener").present) {
                    addLifecycleListener(PrintingListener)
                }
            }
        """
        file("input.txt").text = "input"

        when:
        withBuildCache().run "copy"

        then:
        executedAndNotSkipped(":copy")

        when:
        withBuildCache().run "copy", "-PwithListener"

        then:
        outputContains("listener notified")
        skipped(":copy")

        when:
        file("build/output.txt").delete()
        withBuildCache().run "copy", "-PwithListener"

        then:
        outputContains("listener notified")
        result.groupedOutput.task(":copy").outcome == "FROM-CACHE"

        when: "the listener is removed again"
        withBuildCache().run "copy"

        then:
        outputDoesNotContain("listener notified")
        skipped(":copy")
    }

    def "afterExecution is called after task outputs are captured"() {
        buildFile """
            abstract class CopyTask extends DefaultTask {
                @InputFile
                abstract RegularFileProperty getInput()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                void run() {
                    output.get().asFile.text = input.get().asFile.text
                }
            }
            abstract class OutputChangingListener implements TaskLifecycleListener<CopyTask> {
                void afterExecution(CopyTask task, TaskState state) {
                    task.output.get().asFile << " changed"
                }
            }

            tasks.register("copy", CopyTask) {
                input = file("input.txt")
                output = layout.buildDirectory.file("output.txt")
                addLifecycleListener(OutputChangingListener)
            }
        """
        file("input.txt").text = "input"
        // On Windows, the file system event for the change made by the listener can arrive after the next build has started,
        // which then uses the outputs retained from the previous build
        def args = OperatingSystem.current().windows ? [FileSystemWatchingHelper.disableFsWatchingArgument] : []

        when:
        run(["copy"] + args)

        then:
        executedAndNotSkipped(":copy")
        file("build/output.txt").text == "input changed"

        when: "the change made by the listener is detected as an output change"
        run(["copy"] + args)

        then:
        executedAndNotSkipped(":copy")
        file("build/output.txt").text == "input changed"
    }

    @UnsupportedWithConfigurationCache(because = "uses legacy task execution listeners")
    def "listener is notified inside legacy task execution listeners"() {
        buildFile """
            abstract class PrintingListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    println "lifecycle listener before"
                }

                void afterExecution(Task task, TaskState state) {
                    println "lifecycle listener after"
                }
            }

            gradle.taskGraph.beforeTask { println "legacy before" }
            gradle.taskGraph.afterTask { println "legacy after" }

            tasks.register("foo") {
                addLifecycleListener(PrintingListener)
                doLast { println "task action ran" }
            }
        """

        when:
        run "foo"

        then:
        result.groupedOutput.task(":foo").output == """legacy before
lifecycle listener before
task action ran
lifecycle listener after
legacy after"""
    }

    def "cannot register listener from a listener of the same task"() {
        buildFile """
            abstract class OtherListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    println "other listener notified"
                }
            }

            abstract class RegisteringListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    task.addLifecycleListener(OtherListener)
                }
            }

            tasks.register("foo") {
                addLifecycleListener(RegisteringListener)
                doLast { println "task action ran" }
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasDescription("Execution failed for task ':foo'")
        failure.assertHasCause("Cannot call Task.addLifecycleListener(Class) on task ':foo' from a task lifecycle listener. Only the values of task inputs can be changed.")
        outputDoesNotContain("other listener notified")
        outputDoesNotContain("task action ran")
    }

    def "cannot register listener for incompatible task type"() {
        buildFile """
            abstract class CopyTask extends DefaultTask {}

            abstract class CopyListener implements TaskLifecycleListener<CopyTask> {}

            tasks.register("foo") {
                addLifecycleListener(CopyListener)
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasCause("Cannot add lifecycle listener CopyListener to task ':foo': the listener accepts tasks of type CopyTask, but the task is of type org.gradle.api.DefaultTask.")
    }

    def "cannot register listener whose task type cannot be determined"() {
        buildFile """
            abstract class GenericListener<T extends Task> implements TaskLifecycleListener<T> {}

            tasks.register("foo") {
                addLifecycleListener(GenericListener)
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasCause("Cannot add lifecycle listener GenericListener to task ':foo': the task type the listener accepts cannot be determined.")
    }

    def "cannot register listener with nested property"() {
        buildFile """
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.api.services.ServiceReference

            abstract class Greeter implements BuildService<BuildServiceParameters.None> {}

            gradle.sharedServices.registerIfAbsent("greeter", Greeter)

            abstract class Greeting {
                @ServiceReference("greeter")
                abstract Property<Greeter> getGreeter()
            }

            abstract class NestedListener implements TaskLifecycleListener<Task> {
                @Nested
                abstract Greeting getGreeting()
            }

            tasks.register("foo") {
                addLifecycleListener(NestedListener)
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasCause("Cannot add lifecycle listener NestedListener to task ':foo': property 'greeting' is annotated with @Nested, which is not supported for lifecycle listeners.")
    }

    def "listener can inject #service"() {
        buildFile """
            import javax.inject.Inject

            abstract class InjectingListener implements TaskLifecycleListener<Task> {
                @Inject
                abstract $service getService()

                void beforeExecution(Task task) {
                    println "injected \${service != null}"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(InjectingListener)
            }
        """

        when:
        run "foo"

        then:
        outputContains("injected true")

        where:
        service << ["ObjectFactory", "ProviderFactory", "FileSystemOperations", "ArchiveOperations", "ExecOperations", "ProjectLayout", "org.gradle.api.problems.Problems"]
    }

    def "listener cannot inject #service"() {
        buildFile """
            import javax.inject.Inject

            abstract class InjectingListener implements TaskLifecycleListener<Task> {
                @Inject
                abstract $service getService()

                void beforeExecution(Task task) {
                    println "injected \${service}"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(InjectingListener)
                doLast { println "task action ran" }
            }
        """

        when:
        fails "foo"

        then:
        failure.assertHasDescription("Execution failed for task ':foo'")
        failure.assertThatCause(containsString("available in task lifecycle listener services."))
        outputDoesNotContain("task action ran")

        where:
        service << ["Project", "org.gradle.api.internal.project.ProjectInternal", "org.gradle.api.invocation.Gradle", "TaskContainer", "WorkerExecutor", "org.gradle.api.services.BuildServiceRegistry", "org.gradle.internal.instantiation.managed.ManagedObjectRegistry"]
    }
}
