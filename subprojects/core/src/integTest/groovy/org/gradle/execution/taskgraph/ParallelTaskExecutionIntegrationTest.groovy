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

    def "overlapping outputs prevent parallel execution"() {
        given:
        executer.withArgument("-i")
        withParallelThreads(2)

        and:
        buildFile << """
            aPing.outputs.file "dir"
            bPing.outputs.file "dir/file"
        """
        expect:
        blockingServer.expect(":aPing")
        blockingServer.expect(":bPing")

        run":aPing", ":bPing"
    }

    def "independent tasks from multiple projects execute in parallel"() {
        given:
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrent(":a:aPing", ":a:bPing", ":b:aPing")

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
        blockingServer.expectConcurrent(":aPing", ":bPing")

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
        blockingServer.expectConcurrent(":bPing", ":cPing")
        blockingServer.expect(":aPing")

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
        blockingServer.expectConcurrent(":bPing", ":cFailingPing")

        when:
        fails ":aPing"

        then:
        notExecuted(":aPing")
    }

    def "the number of tasks executed in parallel is limited by the number of parallel threads"() {
        given:
        withParallelThreads(2)

        expect:
        blockingServer.expectConcurrent(":aPing", ":bPing")
        blockingServer.expectConcurrent(":cPing", ":dPing")

        run ":aPing", ":bPing", ":cPing", ":dPing"
    }

    def "tasks are run in parallel if there are tasks without async work running in a different project using --parallel"() {
        given:
        executer.withArgument("--parallel")
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrent(":a:aSerialPing", ":b:aPing", ":b:bPing")

        run ":a:aSerialPing", ":b:aPing", ":b:bPing"
    }

    def "tasks are not run in parallel if there are tasks without async work running in a different project without --parallel"() {
        given:
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrent(":a:aSerialPing")
        blockingServer.expectConcurrent(":b:aPing", ":b:bPing")

        run ":a:aSerialPing", ":b:aPing", ":b:bPing"
    }

    def "tasks are not run in parallel if destroy files overlap with output files"() {
        given:
        withParallelThreads(2)
        buildFile << """
            aPing.destroyables.register rootProject.file("dir")
        
            bPing.outputs.file rootProject.file("dir")
        """

        expect:
        blockingServer.expectConcurrent(":aPing")
        blockingServer.expectConcurrent(":bPing")

        run ":aPing", ":bPing"
    }

    def "tasks are not run in parallel if destroy files overlap with output files in multiproject build"() {
        given:
        withParallelThreads(2)
        buildFile << """
            project(':a') { aPing.destroyables.register rootProject.file("dir") }
        
            project(':b') { bPing.outputs.file rootProject.file("dir") }
        """

        expect:
        blockingServer.expectConcurrent(":a:aPing")
        blockingServer.expectConcurrent(":b:bPing")

        run ":a:aPing", ":b:bPing"
    }

    def "tasks are not run in parallel if destroy files overlap with input files (destroy first)"() {
        given:
        withParallelThreads(2)

        buildFile << """
            aPing.destroyables.register file("foo")
        
            bPing.outputs.file file("foo")
            bPing.doLast { file("foo") << "foo" }
            
            cPing.inputs.file file("foo")
            cPing.dependsOn bPing
        """

        expect:
        blockingServer.expectConcurrent(":aPing")
        blockingServer.expectConcurrent(":bPing")
        blockingServer.expectConcurrent(":cPing")

        run ":aPing", ":cPing"
    }

    def "tasks are not run in parallel if destroy files overlap with input files (create/use first)"() {
        given:
        withParallelThreads(2)

        buildFile << """
            aPing.destroyables.register file("foo")
        
            bPing.outputs.file file("foo")
            bPing.doLast { file("foo") << "foo" }
            
            cPing.inputs.file file("foo")
            cPing.dependsOn bPing
        """

        expect:
        blockingServer.expectConcurrent(":bPing")
        blockingServer.expectConcurrent(":cPing")
        blockingServer.expectConcurrent(":aPing")

        run ":cPing", ":aPing"
    }

    def "tasks are not run in parallel if destroy files overlap with input files (destroy first) in multi-project build"() {
        given:
        withParallelThreads(2)

        buildFile << """
            project(':a') { 
                aPing.destroyables.register rootProject.file("foo")
                
                bPing.outputs.file rootProject.file("foo")
                bPing.doLast { rootProject.file("foo") << "foo" }
            }
        
            project(':b') {
                cPing.inputs.file rootProject.file("foo")
                cPing.dependsOn ":a:bPing"
            }
        """

        expect:
        blockingServer.expectConcurrent(":a:aPing")
        blockingServer.expectConcurrent(":a:bPing")
        blockingServer.expectConcurrent(":b:cPing")

        run ":a:aPing", ":b:cPing"
    }

    def "explicit task dependency relationships are honored even if it violates destroys/creates/consumes relationships"() {
        given:
        withParallelThreads(2)

        buildFile << """
            aPing.destroyables.register file("foo")
            aPing.dependsOn ":bPing"
            
            task aIntermediate { dependsOn aPing }
        
            bPing.outputs.file file("foo")
            bPing.doLast { file("foo") << "foo" }
            
            cPing.inputs.file file("foo")
            cPing.dependsOn bPing, aIntermediate
        """

        expect:
        blockingServer.expectConcurrent(":bPing")
        blockingServer.expectConcurrent(":aPing")
        blockingServer.expectConcurrent(":cPing")

        run ":cPing", ":aPing"
    }

    def "explicit ordering relationships are honored even if it violates destroys/creates/consumes relationships"() {
        given:
        withParallelThreads(2)

        buildFile << """
            aPing.destroyables.register file("foo")
            aPing.mustRunAfter ":bPing"
            
            task aIntermediate { dependsOn aPing }
        
            bPing.outputs.file file("foo")
            bPing.doLast { file("foo") << "foo" }
            
            cPing.inputs.file file("foo")
            cPing.dependsOn bPing
            cPing.mustRunAfter aPing
        """

        expect:
        blockingServer.expectConcurrent(":bPing")
        blockingServer.expectConcurrent(":aPing")
        blockingServer.expectConcurrent(":cPing")

        run ":cPing", ":aPing"
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
        blockingServer.expectConcurrent(":aPing")
        fails ":aPing"

        and:
        failure.assertHasCause "BOOM!"
    }
}
