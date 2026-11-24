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

package org.gradle.execution

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ContinueOnVerificationFailureIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            abstract class Producer extends DefaultTask {
                @Input
                abstract Property<String> getFailureType()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = name
                    switch (failureType.get()) {
                        case "verification": throw new VerificationException("verification failure in \${name}")
                        case "other": throw new RuntimeException("other failure in \${name}")
                    }
                }
            }

            abstract class Consumer extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getInputFiles()

                @TaskAction
                void run() {}
            }

            ["a", "b", "c"].each { taskName ->
                tasks.register(taskName, Producer) {
                    failureType.convention(providers.gradleProperty(taskName).orElse("none"))
                    outputFile.convention(layout.buildDirectory.file(taskName + ".txt"))
                }
            }
        """
    }

    def "runs all dependencies of a lifecycle task when one has a verification failure"() {
        given:
        buildFile << """
            tasks.register("consumer") {
                dependsOn("a", "b")
            }
        """

        when:
        fails("consumer", "-Pa=verification")

        then:
        result.assertTasksScheduled(":a", ":b")
        failure.assertHasCause("verification failure in a")
    }

    def "runs dependencies of a lifecycle task that are not ready when another dependency has a verification failure"() {
        given:
        buildFile << """
            tasks.register("consumer") {
                dependsOn("a", "b")
            }
            tasks.named("b") {
                dependsOn("c")
            }
        """

        when:
        fails("consumer", "-Pa=verification")

        then:
        result.assertTasksScheduled(":a", ":b", ":c")
    }

    def "reports all verification failures"() {
        given:
        buildFile << """
            tasks.register("consumer") {
                dependsOn("a", "b")
            }
        """

        when:
        fails("consumer", "-Pa=verification", "-Pb=verification")

        then:
        result.assertTasksScheduled(":a", ":b")
        failure.assertHasFailures(2)
        failure.assertHasCause("verification failure in a")
        failure.assertHasCause("verification failure in b")
    }

    def "stops at a failure that is not a verification failure"() {
        given:
        buildFile << """
            tasks.register("consumer") {
                dependsOn("a", "b")
            }
        """

        when:
        // Run one task at a time, so that b has not started when a fails
        fails("consumer", "-Pa=other", "--max-workers=1")

        then:
        result.assertTasksScheduled(":a")
        failure.assertHasCause("other failure in a")
    }

    def "stops at a failure that is not a verification failure after a verification failure"() {
        when:
        fails("a", "b", "c", "-Pa=verification", "-Pb=other", "--max-workers=1")

        then:
        result.assertTasksScheduled(":a", ":b")
        failure.assertHasFailures(2)
    }

    def "stops at a verification failure with --no-continue"() {
        given:
        buildFile << """
            tasks.register("consumer") {
                dependsOn("a", "b")
            }
        """

        when:
        fails("consumer", "-Pa=verification", "--no-continue", "--max-workers=1")

        then:
        result.assertTasksScheduled(":a")
    }

    def "runs all dependencies with --continue"() {
        given:
        buildFile << """
            tasks.register("consumer") {
                dependsOn("a", "b")
            }
        """

        when:
        fails("consumer", "-Pa=other", "--continue")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "runs a consumer of the outputs of a task with a verification failure"() {
        given:
        buildFile << """
            tasks.register("consumer", Consumer) {
                inputFiles.from(tasks.named("a").flatMap { it.outputFile })
            }
        """

        when:
        fails("consumer", "-Pa=verification")

        then:
        result.assertTasksScheduled(":a", ":consumer")
    }

    def "does not run a consumer that depends on a task with a verification failure"() {
        given:
        buildFile << """
            tasks.register("consumer", Consumer) {
                inputFiles.from(tasks.named("a").flatMap { it.outputFile })
                dependsOn("a")
            }
        """

        when:
        fails("consumer", "-Pa=verification")

        then:
        result.assertTasksScheduled(":a")
    }

    def "runs a task requested with a task that has a verification failure"() {
        when:
        fails("a", "b", "-Pa=verification", "--max-workers=1")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "runs a task that should run after a task with a verification failure"() {
        given:
        buildFile << """
            tasks.named("b") {
                shouldRunAfter("a")
            }
        """

        when:
        fails("a", "b", "-Pa=verification")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "runs a task that must run after a task with a #failureKind failure and #options"() {
        given:
        buildFile << """
            tasks.named("b") {
                mustRunAfter("a")
            }
        """

        when:
        fails(["a", "b", "-Pa=${failureKind}".toString()] + options)

        then:
        result.assertTasksScheduled(":a", ":b")

        where:
        failureKind    | options
        "verification" | []
        "verification" | ["--continue"]
        "other"        | ["--continue"]
    }

    def "treats a verification failure from a worker action as a verification failure"() {
        given:
        buildFile << """
            abstract class VerifyAction implements org.gradle.workers.WorkAction<org.gradle.workers.WorkParameters.None> {
                void execute() {
                    throw new VerificationException("verification failure in worker")
                }
            }

            abstract class WorkerProducer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutput()

                @javax.inject.Inject
                abstract org.gradle.workers.WorkerExecutor getWorkerExecutor()

                @TaskAction
                void run() {
                    output.get().asFile.text = name
                    workerExecutor.${isolation}().submit(VerifyAction) {}
                }
            }

            def workerProducer = tasks.register("workerProducer", WorkerProducer) {
                output.convention(layout.buildDirectory.file("worker.txt"))
            }

            tasks.register("consumer", Consumer) {
                inputFiles.from(workerProducer)
            }
        """

        when:
        fails("consumer")

        then:
        result.assertTasksScheduled(":workerProducer", ":consumer")
        failure.assertHasCause("verification failure in worker")

        where:
        isolation << ["noIsolation", "classLoaderIsolation", "processIsolation"]
    }

}
