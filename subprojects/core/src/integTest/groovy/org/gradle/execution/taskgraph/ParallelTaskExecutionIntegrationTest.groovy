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

@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
class ParallelTaskExecutionIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()

        settingsFile << 'include "a", "b"'

        buildFile << """
            import javax.inject.Inject
            import org.gradle.workers.WorkerExecutor
            import org.gradle.workers.IsolationMode

            class SerialPing extends DefaultTask {
                @TaskAction
                void ping() {
                    new URL("http://localhost:${blockingServer.port}/" + path).text
                }
            }

            public class TestParallelRunnable implements Runnable {
                final String path 

                @Inject
                public TestParallelRunnable(String path) {
                    this.path = path
                }
                
                public void run() {
                    new URL("http://localhost:${blockingServer.port}/" + path).text
                }
            }
            
            class Ping extends DefaultTask {
                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }
                
                @TaskAction
                void ping() {
                    workerExecutor.submit(TestParallelRunnable) { config ->
                        config.params = [ path ]
                        config.isolationMode = IsolationMode.NONE
                    }
                }
            }

            class FailingPing extends DefaultTask {
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
                tasks.addRule("<>SerialPing") { String name ->
                    if (name.endsWith("SerialPing")) {
                        tasks.create(name, SerialPing)
                    }
                }
            }
        """
        executer.withArgument('--info')
    }

    void withParallelThreads(int threadCount) {
        executer.withArgument("--max-workers=$threadCount")
    }

    def "info is logged when overlapping outputs prevent parallel execution"() {
        given:
        executer.withArgument("-i")
        withParallelThreads(2)

        and:
        buildFile << """
            aPing.outputs.file "dir"
            bPing.outputs.file "dir/file"
        """
        expect:
        blockingServer.expectSerialExecution(":aPing")
        blockingServer.expectSerialExecution(":bPing")
        run":aPing", ":bPing"
        result.assertOutputContains("Cannot execute task :bPing in parallel with task :aPing due to overlapping output: ${file("dir")}")
    }

    def "independent tasks from multiple projects execute in parallel"() {
        given:
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrentExecution(":a:aPing", ":a:bPing", ":b:aPing")

        run ":a:aPing", ":a:bPing", ":b:aPing"
    }

    def "two tasks with should run after execute in parallel"() {
        given:
        withParallelThreads(2)

        when:
        buildFile << """
            bPing.shouldRunAfter aPing
        """

        then:
        blockingServer.expectConcurrentExecution(":aPing", ":bPing")

        run ":aPing", ":bPing"
    }

    def "two tasks that are dependencies of another task are executed in parallel"() {
        given:
        withParallelThreads(2)

        when:
        buildFile << """
            aPing.dependsOn bPing, cPing
        """

        then:
        blockingServer.expectConcurrentExecution(":bPing", ":cPing")
        blockingServer.expectSerialExecution(":aPing")

        run ":aPing"
    }

    def "task is not executed if one of its dependencies executed in parallel fails"() {
        given:
        withParallelThreads(2)

        when:
        buildFile << """
            aPing.dependsOn bPing, cFailingPing
        """

        then:
        blockingServer.expectConcurrentExecution(":bPing", ":cFailingPing")

        when:
        fails ":aPing"

        then:
        notExecuted(":aPing")
    }

    def "the number of tasks executed in parallel is limited by the number of parallel threads"() {
        given:
        withParallelThreads(2)

        expect:
        blockingServer.expectConcurrentExecution(":aPing", ":bPing")
        blockingServer.expectConcurrentExecution(":cPing", ":dPing")

        run ":aPing", ":bPing", ":cPing", ":dPing"
    }

    def "tasks are run in parallel if there are tasks without async work running in a different project using --parallel"() {
        given:
        executer.withArgument("--parallel")
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrentExecution(":a:aSerialPing", ":b:aPing", ":b:bPing")

        run ":a:aSerialPing", ":b:aPing", ":b:bPing"
    }

    def "tasks are not run in parallel if there are tasks without async work running in a different project without --parallel"() {
        given:
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrentExecution(":a:aSerialPing")
        blockingServer.expectConcurrentExecution(":b:aPing", ":b:bPing")

        run ":a:aSerialPing", ":b:aPing", ":b:bPing"
    }

    def "tasks are not run in parallel if destroy files overlap with output files"() {
        given:
        withParallelThreads(2)
        buildFile << """
            aPing.destroyables.file rootProject.file("dir")
        
            bPing.outputs.file rootProject.file("dir")
        """

        expect:
        blockingServer.expectConcurrentExecution(":aPing")
        blockingServer.expectConcurrentExecution(":bPing")

        run ":aPing", ":bPing"

        and:
        output.contains "Cannot execute task :bPing in parallel with task :aPing due to overlapping output: ${file("dir")}"
    }

    def "tasks are not run in parallel if destroy files overlap with output files in multiproject build"() {
        given:
        withParallelThreads(2)
        buildFile << """
            project(':a') { aPing.destroyables.file rootProject.file("dir") }
        
            project(':b') { bPing.outputs.file rootProject.file("dir") }
        """

        expect:
        blockingServer.expectConcurrentExecution(":a:aPing")
        blockingServer.expectConcurrentExecution(":b:bPing")

        run ":a:aPing", ":b:bPing"

        and:
        output.contains "Cannot execute task :b:bPing in parallel with task :a:aPing due to overlapping output: ${file("dir")}"
    }

    def "tasks are not run in parallel if destroy files overlap with input files (destroy first)"() {
        given:
        withParallelThreads(2)

        buildFile << """
            aPing.destroyables.file file("foo")
        
            bPing.outputs.file file("foo")
            bPing.doLast { file("foo") << "foo" }
            
            cPing.inputs.file file("foo")
            cPing.dependsOn bPing
        """

        expect:
        blockingServer.expectConcurrentExecution(":aPing")
        blockingServer.expectConcurrentExecution(":bPing")
        blockingServer.expectConcurrentExecution(":cPing")

        run ":aPing", ":cPing"

        and:
        output.contains "Cannot execute task :bPing in parallel with task :aPing due to overlapping output: ${file("foo")}"
    }

    def "tasks are not run in parallel if destroy files overlap with input files (create/use first)"() {
        given:
        withParallelThreads(2)

        buildFile << """
            aPing.destroyables.file file("foo")
        
            bPing.outputs.file file("foo")
            bPing.doLast { file("foo") << "foo" }
            
            cPing.inputs.file file("foo")
            cPing.dependsOn bPing
        """

        expect:
        blockingServer.expectConcurrentExecution(":bPing")
        blockingServer.expectConcurrentExecution(":cPing")
        blockingServer.expectConcurrentExecution(":aPing")

        run ":cPing", ":aPing"

        and:
        output.contains "Cannot execute task :aPing in parallel with task :bPing due to overlapping output: ${file("foo")}"

        and:
        output.contains "Cannot execute task :aPing in parallel with task :cPing due to overlapping input/destroy: ${file("foo")}"
    }

    def "tasks are not run in parallel if destroy files overlap with input files (destroy first) in multi-project build"() {
        given:
        withParallelThreads(2)

        buildFile << """
            project(':a') { 
                aPing.destroyables.file rootProject.file("foo")
                
                bPing.outputs.file rootProject.file("foo")
                bPing.doLast { rootProject.file("foo") << "foo" }
            }
        
            project(':b') {
                cPing.inputs.file rootProject.file("foo")
                cPing.dependsOn ":a:bPing"
            }
        """

        expect:
        blockingServer.expectConcurrentExecution(":a:aPing")
        blockingServer.expectConcurrentExecution(":a:bPing")
        blockingServer.expectConcurrentExecution(":b:cPing")

        run ":a:aPing", ":b:cPing"

        and:
        output.contains "Cannot execute task :a:bPing in parallel with task :a:aPing due to overlapping output: ${file("foo")}"
    }

    def "explicit task dependency relationships are honored even if it violates destroys/creates/consumes relationships"() {
        given:
        withParallelThreads(2)

        buildFile << """
            aPing.destroyables.file file("foo")
            aPing.dependsOn ":bPing"
            
            task aIntermediate { dependsOn aPing }
        
            bPing.outputs.file file("foo")
            bPing.doLast { file("foo") << "foo" }
            
            cPing.inputs.file file("foo")
            cPing.dependsOn bPing, aIntermediate
        """

        expect:
        blockingServer.expectConcurrentExecution(":bPing")
        blockingServer.expectConcurrentExecution(":aPing")
        blockingServer.expectConcurrentExecution(":cPing")

        run ":cPing", ":aPing"
    }

    def "explicit ordering relationships are honored even if it violates destroys/creates/consumes relationships"() {
        given:
        withParallelThreads(2)

        buildFile << """
            aPing.destroyables.file file("foo")
            aPing.mustRunAfter ":bPing"
            
            task aIntermediate { dependsOn aPing }
        
            bPing.outputs.file file("foo")
            bPing.doLast { file("foo") << "foo" }
            
            cPing.inputs.file file("foo")
            cPing.dependsOn bPing
            cPing.mustRunAfter aPing
        """

        expect:
        blockingServer.expectConcurrentExecution(":bPing")
        blockingServer.expectConcurrentExecution(":aPing")
        blockingServer.expectConcurrentExecution(":cPing")

        run ":cPing", ":aPing"
    }
}
