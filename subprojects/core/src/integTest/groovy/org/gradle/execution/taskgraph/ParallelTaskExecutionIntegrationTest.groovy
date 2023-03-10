/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.execution.taskgraph

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Timeout

@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
class ParallelTaskExecutionIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {

    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()

        settingsFile << 'include "a", "b"'

        buildFile << """
            abstract class SerialPing extends DefaultTask {

                SerialPing() { outputs.upToDateWhen { false } }

                @TaskAction
                void ping() {
                    new URL("http://localhost:${blockingServer.port}/" + path).text
                }
            }

            interface TestParallelWorkActionConfig extends WorkParameters {
                Property<String> getPath()
            }

            abstract class TestParallelWorkAction implements WorkAction<TestParallelWorkActionConfig> {
                void execute() {
                    new URL("http://localhost:${blockingServer.port}/" + parameters.path.get()).text
                }
            }

            abstract class Ping extends DefaultTask {

                Ping() { outputs.upToDateWhen { false } }

                @Inject
                protected abstract WorkerExecutor getWorkerExecutor()

                @TaskAction
                void ping() {
                    def taskPath = path
                    workerExecutor.noIsolation().submit(TestParallelWorkAction) { config ->
                        config.path.set(taskPath)
                    }
                }
            }

            abstract class PingWithCacheableWarnings extends Ping {
                @Optional @InputFile File invalidInput
            }

            class FailingPing extends DefaultTask {

                FailingPing() { outputs.upToDateWhen { false } }

                @TaskAction
                void ping() {
                    new URL("http://localhost:${blockingServer.port}/" + path).text
                    throw new RuntimeException("task failure")
                }
            }

            allprojects {
                def pingEndings = ['FailingPing', 'PingWithCacheableWarnings', 'SerialPing', 'InvalidPing']

                tasks.addRule("<>Ping") { String name ->
                    if (name.endsWith("Ping") && pingEndings.every { !name.endsWith(it) }) {
                        tasks.create(name, Ping)
                    }
                }
                tasks.addRule("<>FailingPing") { String name ->
                    if (name.endsWith("FailingPing")) {
                        tasks.create(name, FailingPing)
                    }
                }
                tasks.addRule("<>PingWithCacheableWarnings") { String name ->
                    if (name.endsWith("PingWithCacheableWarnings")) {
                        tasks.create(name, PingWithCacheableWarnings)
                    }
                }
                tasks.addRule("<>SerialPing") { String name ->
                    if (name.endsWith("SerialPing")) {
                        tasks.create(name, SerialPing)
                    }
                }
            }
        """
        executer.beforeExecute {
            withArgument('--info')
        }
    }

    void withInvalidPing() {
        buildFile << """
            abstract class InvalidPing extends Ping {
                @org.gradle.integtests.fixtures.validation.ValidationProblem File invalidInput
            }
            allprojects {
                tasks.addRule("<>InvalidPing") { String name ->
                    if (name.endsWith("InvalidPing")) {
                        tasks.create(name, InvalidPing)
                    }
                }
            }
        """
    }

    void withParallelThreads(int threadCount) {
        executer.beforeExecute {
            withArgument("--max-workers=$threadCount")
        }
    }

    def "overlapping outputs prevent parallel execution"() {
        given:
        withParallelThreads(2)

        and:
        buildFile << """
            aPing.outputs.dir "dir"
            bPing.outputs.file "dir/file"
        """
        expect:
        2.times {
            blockingServer.expectConcurrent(1, ":aPing", ":bPing")
            run ":aPing", ":bPing"
        }
    }

    def "independent tasks from multiple projects execute in parallel"() {
        given:
        withParallelThreads(3)

        expect:
        2.times {
            blockingServer.expectConcurrent(":a:aPing", ":a:bPing", ":b:aPing")
            run ":a:aPing", ":a:bPing", ":b:aPing"
        }
    }

    def "two tasks with should run after execute in parallel"() {
        given:
        withParallelThreads(2)

        when:
        buildFile << """
            bPing.shouldRunAfter aPing
        """

        then:
        2.times {
            blockingServer.expectConcurrent(":aPing", ":bPing")
            run ":aPing", ":bPing"
        }
    }

    def "tasks that should run after are chosen last when there are more tasks than workers"() {
        given:
        withParallelThreads(2)

        when:
        buildFile << """
            aPing.shouldRunAfter bPing, cPing
        """

        then:
        2.times {
            blockingServer.expectConcurrent(":bPing", ":cPing")
            blockingServer.expectConcurrent(":aPing")
            run ":aPing", ":bPing", ":cPing"
        }
    }

    def "two tasks that are dependencies of another task are executed in parallel"() {
        given:
        withParallelThreads(2)

        when:
        buildFile << """
            aPing.dependsOn bPing, cPing
        """

        then:
        2.times {
            blockingServer.expectConcurrent(":bPing", ":cPing")
            blockingServer.expect(":aPing")
            run ":aPing"
        }
    }

    def "task is not executed if one of its dependencies executed in parallel fails"() {
        given:
        withParallelThreads(2)

        and:
        buildFile << """
            aPing.dependsOn bPing, cFailingPing
        """

        expect:
        2.times {
            blockingServer.expectConcurrent(":bPing", ":cFailingPing")
            fails ":aPing"
            notExecuted(":aPing")
        }
    }

    def "the number of tasks executed in parallel is limited by the number of parallel threads"() {
        given:
        withParallelThreads(2)

        expect:
        2.times {
            blockingServer.expectConcurrent(":aPing", ":bPing")
            blockingServer.expectConcurrent(":cPing", ":dPing")
            run ":aPing", ":bPing", ":cPing", ":dPing"
        }
    }

    def "tasks are run in parallel if there are tasks without async work running in a different project using --parallel"() {
        given:
        executer.beforeExecute {
            withArgument("--parallel")
        }
        withParallelThreads(3)

        expect:
        2.times {
            blockingServer.expectConcurrent(":a:aSerialPing", ":b:aPing", ":b:bPing")
            run ":a:aSerialPing", ":b:aPing", ":b:bPing"
        }
    }

    def "tasks from same project do not run in parallel even when tasks do undeclared dependency resolution"() {
        given:
        executer.beforeExecute {
            withArgument("--parallel")
        }
        withParallelThreads(3)

        buildFile("""
            allprojects {
                apply plugin: 'java-library'
            }
            project(":b") {
                dependencies {
                    implementation project(":a")
                }

                task undeclared {
                    def runtimeClasspath = configurations.runtimeClasspath
                    doLast {
                        ${blockingServer.callFromBuild("before-resolve")}
                        runtimeClasspath.files.each { }
                        ${blockingServer.callFromBuild("after-resolve")}
                    }
                }
                task other {
                    doLast {
                        ${blockingServer.callFromBuild("other")}
                    }
                }
            }
        """)

        when:
        blockingServer.expect("before-resolve")
        blockingServer.expect("after-resolve")
        blockingServer.expect("other")
        run("undeclared", "other")

        then:
        noExceptionThrown()
    }

    def "tasks are not run in parallel if there are tasks without async work running in a different project without --parallel"() {
        given:
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrent(":a:aSerialPing")
        blockingServer.expectConcurrent(":b:aPing", ":b:bPing")
        run ":a:aSerialPing", ":b:aPing", ":b:bPing"

        // when configuration is loaded from configuration cache, all tasks are executed in parallel
        if (GradleContextualExecuter.configCache) {
            blockingServer.expectConcurrent(":a:aSerialPing", ":b:aPing", ":b:bPing")
            run ":a:aSerialPing", ":b:aPing", ":b:bPing"
        }
    }

    def "tasks are not run in parallel if destroy files overlap with output files"() {
        given:
        withParallelThreads(2)
        buildFile << """
            def dir = rootProject.file("dir")

            aPing.destroyables.register dir

            bPing.outputs.file dir
        """

        expect:
        2.times {
            blockingServer.expectConcurrent(1, ":aPing", ":bPing")
            run ":aPing", ":bPing"
        }
    }

    def "tasks are not run in parallel if destroy files overlap with output files in multiproject build"() {
        given:
        withParallelThreads(2)
        buildFile << """
            def dir = rootProject.file("dir")

            project(':a') { aPing.destroyables.register dir }

            project(':b') { bPing.outputs.file dir }
        """

        expect:
        2.times {
            blockingServer.expectConcurrent(1, ":a:aPing", ":b:bPing")
            run ":a:aPing", ":b:bPing"
        }
    }

    def "tasks are not run in parallel if destroy files overlap with input files (destroy first)"() {
        given:
        withParallelThreads(2)

        buildFile << """
            def foo = file("foo")

            destroyerPing.destroyables.register foo

            producerPing.outputs.file foo
            producerPing.doLast { foo << "foo" }

            consumerPing.inputs.file foo
            consumerPing.dependsOn producerPing
        """

        expect:
        2.times {
            blockingServer.expectConcurrent(":destroyerPing")
            blockingServer.expectConcurrent(":producerPing")
            blockingServer.expectConcurrent(":consumerPing")
            run ":destroyerPing", ":consumerPing"
        }
    }

    @Flaky(because = "https://github.com/gradle/gradle-private/issues/3570")
    def "tasks are not run in parallel if destroy files overlap with input files (create/use first)"() {
        given:
        withParallelThreads(2)

        buildFile << """
            def foo = file("foo")

            aPing.destroyables.register foo

            bPing.outputs.file foo
            bPing.doLast { foo << "foo" }

            cPing.inputs.file foo
            cPing.dependsOn bPing
        """

        expect:
        2.times {
            blockingServer.expectConcurrent(":bPing")
            blockingServer.expectConcurrent(":cPing")
            blockingServer.expectConcurrent(":aPing")
            run ":cPing", ":aPing"
        }
    }

    def "tasks are not run in parallel if destroy files overlap with input files (destroy first) in multi-project build"() {
        given:
        withParallelThreads(2)

        buildFile << """
            def foo = rootProject.file("foo")

            project(':a') {
                aPing.destroyables.register foo

                bPing.outputs.file foo
                bPing.doLast { foo << "foo" }
            }

            project(':b') {
                cPing.inputs.file foo
                cPing.dependsOn ":a:bPing"
            }
        """

        expect:
        2.times {
            blockingServer.expectConcurrent(":a:aPing")
            blockingServer.expectConcurrent(":a:bPing")
            blockingServer.expectConcurrent(":b:cPing")
            run ":a:aPing", ":b:cPing"
        }
    }

    def "explicit task dependency relationships are honored even if it violates destroys/creates/consumes relationships"() {
        given:
        withParallelThreads(2)

        buildFile << """
            def foo = file("foo")

            aPing.destroyables.register foo
            aPing.dependsOn ":bPing"

            task aIntermediate { dependsOn aPing }

            bPing.outputs.file foo
            bPing.doLast { foo << "foo" }

            cPing.inputs.file foo
            cPing.dependsOn bPing, aIntermediate
        """

        expect:
        2.times {
            blockingServer.expectConcurrent(":bPing")
            blockingServer.expectConcurrent(":aPing")
            blockingServer.expectConcurrent(":cPing")
            run ":cPing", ":aPing"
        }
    }

    def "explicit ordering relationships are honored even if it violates destroys/creates/consumes relationships"() {
        given:
        withParallelThreads(2)

        buildFile << """
            def foo = file("foo")

            aPing.destroyables.register foo
            aPing.mustRunAfter ":bPing"

            task aIntermediate { dependsOn aPing }

            bPing.outputs.file foo
            bPing.doLast { foo << "foo" }

            cPing.inputs.file foo
            cPing.dependsOn bPing
            cPing.mustRunAfter aPing
        """

        expect:
        2.times {
            blockingServer.expectConcurrent(":bPing")
            blockingServer.expectConcurrent(":aPing")
            blockingServer.expectConcurrent(":cPing")
            run ":cPing", ":aPing"
        }
    }

    @Timeout(30)
    def "handles an exception thrown while walking the task graph when a finalizer is present"() {
        given:
        withParallelThreads(2)

        buildFile << """
            class BrokenTask extends DefaultTask {
                @OutputFiles
                FileCollection getOutputFiles() {
                    throw new RuntimeException('BOOM!')
                }

                @TaskAction
                void doSomething() {
                    println "Executing broken task..."
                }
            }

            task brokenTask(type: BrokenTask)
            aPing.finalizedBy brokenTask
        """

        expect:
        2.times {
            blockingServer.expectConcurrent(":aPing")
            fails ":aPing"
            failure.assertHasCause "BOOM!"
        }
    }

    @Requires({ GradleContextualExecuter.embedded })
    // this test only works in embedded mode because of the use of validation test fixtures
    def "other tasks are not started when an invalid task is running"() {
        given:
        withParallelThreads(3)
        withInvalidPing()

        expect:
        2.times {
            expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, dummyValidationProblem('InvalidPing', 'invalidInput'), 'id', 'section')

            blockingServer.expect(":aInvalidPing")
            blockingServer.expectConcurrent(":bPing", ":cPing")
            run ":aInvalidPing", ":bPing", ":cPing"
        }
    }

    def "cacheability warnings do not prevent a task from running in parallel"() {
        given:
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrent(":aPingWithCacheableWarnings", ":bPing", ":cPing")
        run ":aPingWithCacheableWarnings", ":bPing", ":cPing"
    }

    @Requires({ GradleContextualExecuter.embedded })
    // this test only works in embedded mode because of the use of validation test fixtures
    def "invalid task is not executed in parallel with other task"() {
        given:
        withParallelThreads(3)
        withInvalidPing()

        expect:
        2.times {
            expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, dummyValidationProblem('InvalidPing', 'invalidInput'), 'id', 'section')

            blockingServer.expectConcurrent(":aPing", ":bPing")
            blockingServer.expect(":cInvalidPing")
            run ":aPing", ":bPing", ":cInvalidPing"
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/17013")
    def "does not deadlock when resolving outputs requires resolving multiple artifacts"() {
        buildFile("""
            import org.gradle.util.internal.GFileUtils

            abstract class OutputDeadlockTask extends DefaultTask {
                @Inject
                abstract ProjectLayout getProjectLayout()

                @InputFiles
                abstract ConfigurableFileCollection getInputFiles()

                @OutputFiles
                List<RegularFile> getOutputFiles() {
                    def buildDirectory = projectLayout.buildDirectory
                    return inputFiles.collect { buildDirectory.file(it.name + ".out").get() }
                }

                @TaskAction
                void execute() {
                    def buildDirectory = projectLayout.buildDirectory
                    inputFiles.files.each { File inputFile ->
                        def outputFile = buildDirectory.file(inputFile.name + ".out").get().asFile
                        GFileUtils.copyFile(inputFile, outputFile)
                    }
                }
            }
            allprojects {
                apply plugin: 'java-library'

                tasks.register("outputDeadlock", OutputDeadlockTask) {
                    inputFiles.from(configurations.compileClasspath)
                }

                ${RepoScriptBlockUtil.mavenCentralRepository()}

                dependencies {
                    api 'org.apache.commons:commons-math3:3.6.1'
                    api 'org.apache.commons:commons-io:1.3.2'
                }
            }
        """)
        withParallelThreads(3)
        executer.beforeExecute {
            withArgument("--parallel")
        }

        expect:
        succeeds("outputDeadlock")
    }

    @Issue("https://github.com/gradle/gradle/issues/17905")
    def "does not fail when outputs requires resolving configurations"() {
        buildFile("""
            abstract class OutputsAsMappedInputs extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getInput()

                @OutputFiles
                List<File> getOutputFiles() {
                    return input.files.collect { project.file("build/outputs/\${it.name}") }
                }

                @TaskAction
                void exec() {}
            }

            abstract class BasicTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getInput()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void exec() {}
            }
            subprojects {
                configurations.create("myconfig1") {
                    assert canBeResolved
                    canBeConsumed = false
                }
                configurations.create("myconfig2") {
                    assert canBeResolved
                    canBeConsumed = false
                }

                def problematic = tasks.register("problematicTask", OutputsAsMappedInputs) {
                    input.from(configurations.myconfig1)
                }
                def basic = tasks.register("basicTask", BasicTask) {
                    input.from(configurations.myconfig2)
                    outputFile.set(project.layout.buildDirectory.file("output.txt"))
                }
                tasks.register("runAll") {
                    dependsOn(problematic, basic)
                }
            }
        """)
        withParallelThreads(3)

        expect:
        succeeds "runAll", "--parallel"
    }
}
