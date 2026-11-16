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

package org.gradle.integtests

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations

import static org.hamcrest.CoreMatchers.containsString

class ProviderCalculusExecutionIntegrationTest extends AbstractIntegrationSpec implements TasksWithInputsAndOutputs, StableConfigurationCacheDeprecations {

    @Override
    protected void setupExecuter() {
        super.setupExecuter()
    }


    // ---------------------------------------------------------------------------------------------
    // Provider Calculus: output property whose *location* is produced by another task.
    //
    //   t.outputFile <- gen.flatMap { it.outputDir }.map { it.file("t.txt") }
    //
    // Notation would give      P[ D{gen} | D{t} ]^{gen, t}
    // Implementation (hypothesis, AbstractProperty.getProducer): P[ S | D{t} ]^{t}  -- gen is dropped
    //
    // Assertions encode the hypothesis; flip them if it is wrong.
    // ---------------------------------------------------------------------------------------------

    private void withOutputLocationMappedFromAnotherTask() {
        buildFile << """
            abstract class Gen extends DefaultTask {
                @OutputDirectory abstract DirectoryProperty getOutputDir()
                @TaskAction void run() { new File(outputDir.get().asFile, "gen.txt").text = "gen" }
            }
            abstract class Use extends DefaultTask {
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction void run() { outputFile.get().asFile.text = "use" }
            }
            abstract class Consume extends DefaultTask {
                @InputFile abstract RegularFileProperty getInputFile()
                @TaskAction void run() { println("consumed: " + inputFile.get().asFile.text) }
            }

            def gen = tasks.register("gen", Gen) {
                outputDir.set(layout.buildDirectory.dir("gen"))
            }
            def location = gen.flatMap { it.outputDir }.map { it.file("t.txt") }
            def t = tasks.register("t", Use) {
                outputFile.set(location)
            }
            tasks.register("consume", Consume) {
                inputFile.set(t.flatMap { it.outputFile })
            }
        """
    }

    def "output property with location mapped from another task reports only its owning task as producer and dependency"() {
        withOutputLocationMappedFromAnotherTask()
        buildFile << """
            def paths = { container ->
                org.gradle.api.internal.tasks.TaskDependencyUtil
                    .getDependencies(null, container as org.gradle.api.internal.tasks.TaskDependencyContainer)
                    .collect { it.path }.sort()
            }
            def describe = { String name, p ->
                def producer = p.producer
                println("\$name: Delta=" + paths { ctx -> producer.visitDependencies(ctx) } +
                        " Pi=" + paths { ctx -> producer.visitGuardedDependencies(ctx) })
            }
            describe("location", location)
            describe("t.outputFile", t.get().outputFile)
        """

        expect:
        succeeds "help"
        // upstream: P[ D{gen} | D{gen} ]^{gen}
        outputContains("location: Delta=[:gen] Pi=[:gen]")
        // hypothesis: owning task replaces the upstream producer, so :gen disappears
        outputContains("t.outputFile: Delta=[:t] Pi=[:t]")
    }

    def "task with output location mapped from another task does not schedule the location producer"() {
        withOutputLocationMappedFromAnotherTask()

        expect:
        // hypothesis: no :t -> :gen edge is inferred from an output property, so resolving t's output
        // location trips the guard on the mapped upstream. With the configuration cache enabled this
        // may instead fail at store time; the cause should still name :gen.
        fails "t"
        failure.assertThatCause(containsString("before task ':gen' has completed"))
    }

    def "consumer of output location mapped from another task works only when the owning task declares the dependency"() {
        withOutputLocationMappedFromAnotherTask()
        buildFile << """
            t.configure { dependsOn(gen) }
        """

        expect:
        succeeds "consume"
        // :consume gets Delta = {t}; :gen is reached only through the explicit t -> gen edge
        result.assertTasksScheduledInOrder(":gen", ":t", ":consume")
        outputContains("consumed: use")
    }

    def "configuration-time read of mapped output location checks only the owning task"() {
        withOutputLocationMappedFromAnotherTask()
        buildFile << """
            println(t.flatMap { it.outputFile }.map { it.asFile.name }.get())
        """

        expect:
        fails "help"
        // the guard reads Pi(nu) u Pi(kappa) = {t}; :gen (the real producer of the value) is never checked
        failure.assertThatCause(containsString("before task ':t' has completed"))
        failure.assertThatCause(org.hamcrest.CoreMatchers.not(containsString("task ':gen'")))
    }
}
