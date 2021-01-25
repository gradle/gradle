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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Timeout

@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
class ParallelTaskExecutionIntegrationTest extends AbstractIntegrationSpec {

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

            abstract class InvalidPing extends Ping {
                @Optional @Input File invalidInput
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
                tasks.addRule("<>Ping") { String name ->
                    if (name.endsWith("Ping") && name.size() == 5) {
                        tasks.create(name, Ping)
                    }
                }
                tasks.addRule("<>FailingPing") { String name ->
                    if (name.endsWith("FailingPing")) {
                        tasks.create(name, FailingPing)
                    }
                }
                tasks.addRule("<>InvalidPing") { String name ->
                    if (name.endsWith("InvalidPing")) {
                        tasks.create(name, InvalidPing)
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
            blockingServer.expect(":aPing")
            blockingServer.expect(":bPing")
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

    def "tasks are not run in parallel if there are tasks without async work running in a different project without --parallel"() {
        given:
        withParallelThreads(3)

        expect:
        2.times {
            blockingServer.expectConcurrent(":a:aSerialPing")
            blockingServer.expectConcurrent(":b:aPing", ":b:bPing")
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
            blockingServer.expectConcurrent(":aPing")
            blockingServer.expectConcurrent(":bPing")
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
            blockingServer.expectConcurrent(":a:aPing")
            blockingServer.expectConcurrent(":b:bPing")
            run ":a:aPing", ":b:bPing"
        }
    }

    def "tasks are not run in parallel if destroy files overlap with input files (destroy first)"() {
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
            blockingServer.expectConcurrent(":aPing")
            blockingServer.expectConcurrent(":bPing")
            blockingServer.expectConcurrent(":cPing")
            run ":aPing", ":cPing"
        }
    }

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

    def "other tasks are not started when an invalid task task is running"() {
        given:
        withParallelThreads(3)

        expect:
        2.times {
            executer.expectDocumentedDeprecationWarning("Property 'invalidInput' has @Input annotation used on property of type 'File'. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "Execution optimizations are disabled due to the failed validation. " +
                "See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.")

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

    def "invalid task is not executed in parallel with other task"() {
        given:
        withParallelThreads(3)

        expect:
        2.times {
            executer.expectDocumentedDeprecationWarning("Property 'invalidInput' has @Input annotation used on property of type 'File'. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "Execution optimizations are disabled due to the failed validation. " +
                "See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.")

            blockingServer.expectConcurrent(":aPing", ":bPing")
            blockingServer.expect(":cInvalidPing")
            run ":aPing", ":bPing", ":cInvalidPing"
        }
    }
}
