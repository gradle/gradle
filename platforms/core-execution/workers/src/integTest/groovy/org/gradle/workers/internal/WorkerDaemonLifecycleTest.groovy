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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.ProcessFixture
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.workers.fixtures.WorkerExecutorFixture

@IntegrationTestTimeout(180)
class WorkerDaemonLifecycleTest extends AbstractDaemonWorkerExecutorIntegrationSpec {
    String logSnapshot = ""

    def "worker daemons are not reused across builds"() {
        fixture.withWorkActionClassInBuildScript()
        buildFile << """
            import org.gradle.workers.internal.WorkerDaemonFactory

            task runInWorker1(type: WorkerTask) {
                isolationMode = 'processIsolation'
            }

            task runInWorker2(type: WorkerTask) {
                isolationMode = 'processIsolation'
                doFirst {
                    def all = services.get(WorkerDaemonFactory.class).clientsManager.allClients.size()
                    def idle = services.get(WorkerDaemonFactory.class).clientsManager.idleClients.size()
                    println "Existing worker daemons: \${idle} idle out of \${all} total"
                }
            }
        """

        when:
        succeeds "runInWorker1"

        and:
        succeeds "runInWorker2"

        then:
        assertDifferentDaemonsWereUsed("runInWorker1", "runInWorker2")
    }

    def "worker daemons can be restarted when daemon is stopped"() {
        fixture.withWorkActionClassInBuildScript()
        buildFile << """
            task runInWorker1(type: WorkerTask) {
                isolationMode = 'processIsolation'
            }

            task runInWorker2(type: WorkerTask) {
                isolationMode = 'processIsolation'
            }
        """

        when:
        succeeds "runInWorker1"

        then:
        stopDaemonsNow()

        when:
        succeeds "runInWorker2"

        then:
        assertDifferentDaemonsWereUsed("runInWorker1", "runInWorker2")
    }

    def "worker daemons are not reused when classpath changes"() {
        fixture.withWorkActionClassInBuildScript()
        buildFile << """
            task runInWorker1(type: WorkerTask) {
                isolationMode = 'processIsolation'
            }

            task runInWorker2(type: WorkerTask) {
                isolationMode = 'processIsolation'
            }
        """

        when:
        succeeds "runInWorker1"

        then:
        buildFile << """
            task someNewTask
        """

        when:
        succeeds "runInWorker2"

        then:
        assertDifferentDaemonsWereUsed("runInWorker1", "runInWorker2")

        when:
        file("buildSrc/src/main/java/NewClass.java") << "public class NewClass { }"

        then:
        succeeds "runInWorker1"

        and:
        assertDifferentDaemonsWereUsed("runInWorker1", "runInWorker2")
    }

    def "worker daemons are not reused when they fail unexpectedly"() {
        workerExecutorThatCanFailUnexpectedly.writeToBuildFile()
        buildFile << """
            task runInWorker1(type: WorkerTask) {
                isolationMode = 'processIsolation'
            }

            task runInWorker2(type: WorkerTask) {
                // This will cause the worker process to fail with exit code 127
                list = runInWorker1.list + ["poisonPill"]

                isolationMode = 'processIsolation'
            }
        """

        when:
        succeeds "runInWorker1"

        and:
        executer.withStackTraceChecksDisabled()
        fails "runInWorker2"

        then:
        assertDifferentDaemonsWereUsed("runInWorker1", "runInWorker2")

        then:
        succeeds "runInWorker1"

        and:
        assertDifferentDaemonsWereUsed("runInWorker1", "runInWorker2")
    }

    def "all daemons are stopped with the build session except Java"() {
        fixture.withWorkActionClassInBuildScript()
        file('src/main/java').createDir()
        file('src/main/java/Test.java') << "public class Test {}"
        buildFile << """
            apply plugin: "java"

            task runInWorker(type: WorkerTask) {
                isolationMode = 'processIsolation'
            }

            tasks.withType(JavaCompile) {
                options.fork = true
            }
        """

        when:
        args("--info")
        succeeds "compileJava", "runInWorker"

        then:
        sinceSnapshot().count("Started Gradle worker daemon") == 2
        sinceSnapshot().contains("Stopped 1 worker daemon(s).")
    }

    @Requires(UnitTestPreconditions.Unix)
    def "worker daemons exit after the build is complete"() {
        fixture.withWorkActionClassInBuildScript()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = 'processIsolation'
            }
        """

        when:
        succeeds "runInWorker"

        and:
        def daemonProcess = new ProcessFixture(daemons.daemon.context.pid)
        def children = daemonProcess.getChildProcesses()
        // Sends a kill -9 to the daemon process only
        daemons.daemon.killDaemonOnly()

        then:
        children.length == 0

        cleanup:
        // In the event this fails, clean up any orphaned children
        children.each { child ->
            new ProcessFixture(child as Long).kill(true)
        }
    }

    void newSnapshot() {
        logSnapshot = daemons.daemon.log
    }

    String sinceSnapshot() {
        return daemons.daemon.log - logSnapshot
    }

    WorkerExecutorFixture.WorkActionClass getWorkerExecutorThatCanFailUnexpectedly() {
        def executionClass = fixture.workActionThatCreatesFiles
        executionClass.action += """
            if (getParameters().getFiles().contains("poisonPill")) {
                System.exit(127);
            }
        """
        return executionClass
    }
}
