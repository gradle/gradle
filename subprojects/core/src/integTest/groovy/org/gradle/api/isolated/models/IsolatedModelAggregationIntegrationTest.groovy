/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.isolated.models

import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.provider.Provider
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.isolated.models.IsolatedModelRouter
import org.gradle.test.fixtures.dsl.GradleDsl

import javax.inject.Inject

class IsolatedModelAggregationIntegrationTest extends AbstractIntegrationSpec {

    def "can aggregate task outputs from all projects and process them in root project task"() {
        settingsFile """
            rootProject.name = "root"
            include(":sub")

            abstract class SomeTask extends DefaultTask {
                @Input
                abstract Property<Integer> getNumber()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction run() {
                    output.get().asFile.text = number.get().toString()
                }
            }

            gradle.lifecycle.beforeProject { project ->
                def numberTask = project.tasks.register("number", SomeTask) {
                    number = project.name.length()
                    output = project.layout.buildDirectory.file("out.txt")
                }

                Provider<RegularFile> taskOutput = numberTask.flatMap { it.output }
                def router = project.services.get(org.gradle.internal.isolated.models.IsolatedModelRouter)
                router.postModel(router.key("numberFile", RegularFile), router.work(taskOutput))
            }
        """

        createDirs("sub")

        buildFile """
            abstract class SummingTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getNumbers()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction run() {
                    def sum = numbers.files.collect {
                        it.text.toInteger()
                    }.sum() as Integer

                    output.get().asFile.text = sum.toString()
                }
            }

            def router = project.services.get(org.gradle.internal.isolated.models.IsolatedModelRouter)

            tasks.register("sum", SummingTask) {
                Map<String, Provider<RegularFile>> numberFiles =
                    router.getProjectModels(router.key("numberFile", RegularFile), allprojects.collect { it.path })
                // TODO: number = files(...) eagerly evaluates the providers inside the collection
                numbers.from(files(numberFiles.values()))
                output = layout.buildDirectory.file("sum.txt")
            }
        """

        when:
        run "sum"

        then:

        def outFile = file("build/sum.txt")
        outFile.exists()
        outFile.text == "7"
    }

    def "settings plugin can collect models contributed by projects"() {
        file("build-logic/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """

        file("build-logic/src/main/kotlin/my/ProjectApi.kt") << """
            package my

            import ${Inject.name}
            import ${Provider.name}
            import ${IsolatedModelRouter.name}

            abstract class ProjectApi {
                @get:Inject
                abstract val router: IsolatedModelRouter

                fun tag(provider: Provider<String>) {
                    router.postModel(router.key("tag", String::class.java), router.work(provider))
                }
            }
        """

        file("build-logic/src/main/kotlin/my-settings-plugin.settings.gradle.kts") << """
            import org.gradle.kotlin.dsl.support.serviceOf

            gradle.lifecycle.beforeProject {
                extensions.create<my.ProjectApi>("projectApi")
            }

            val flowScope = serviceOf<${FlowScope.name}>()
            val flowProviders = serviceOf<${FlowProviders.name}>()

            flowScope.always(ModelCollectingFlow::class) {
                parameters {
                    buildResult = flowProviders.buildWorkResult.map { it.failure.isEmpty() }
                }
            }

            abstract class ModelCollectingFlow : ${FlowAction.name}<ModelCollectingFlow.Parameters> {
                interface Parameters : ${FlowParameters.name} {
                    @get:Input
                    val buildResult: Property<Boolean>

                    @get:Input
                    val tags: MapProperty<String, String>
                }

                // TODO: injecting the router directly leaves a hole of late discovery of models dependent on tasks
                //  these tasks cannot be executed at this point (in build-finished callback)
                @get:Inject
                abstract val router: ${IsolatedModelRouter.name}

                override fun execute(parameters: Parameters) {
                    val tags: Map<String, Provider<String>> =
                        router.getProjectModels(router.key("tag", String::class.java), listOf(":", ":a", ":b"))

                    println("Tags after build finished: \${tags.mapValues { it.value.orNull }.toSortedMap()}")
                }
            }
        """

        settingsFile """
            pluginManagement {
                includeBuild("build-logic")
            }

            plugins {
                id("my-settings-plugin")
            }

            include("a", "b")
        """

        buildFile """
            projectApi.tag(provider { "tag-root" })
        """

        buildFile "a/build.gradle", """
            println("Skip contributing a tag")
        """

        buildFile "b/build.gradle", """
            projectApi.tag(provider { "tag-b" })
        """

        when:
        run "help"

        then:
        outputContains("Tags after build finished: {:=tag-root, :a=null, :b=tag-b}")
    }

    def "validating overlapping task outputs"() {
        // For all the tasks scheduled in the invocation, validate that none of their output file locations intersect
        // And report which tasks do have overlapping outputs

        createDirs("sub")
        settingsFile """
            rootProject.name = "root"
            include(":sub")


            abstract class NumberTask extends DefaultTask {
                @Input abstract Property<Integer> getNumber()
                @OutputFile abstract RegularFileProperty getOutput()
                @TaskAction run() { output.get().asFile.text = number.get().toString() }
            }

            gradle.beforeProject { Project project ->
                def router = project.services.get(org.gradle.internal.isolated.models.IsolatedModelRouter)

                def task = project.tasks.register("number", NumberTask) {
                    number = project.name.length()
                    output = project.isolated.rootProject.projectDirectory.file("build/out.txt")
                }

                Provider<List<RegularFile>> taskOutputs = task.flatMap { it.output }.map { [it] }
                router.postModel(router.key("taskOutputs", List<RegularFile>), router.work(taskOutputs))
            }
        """

        buildFile """
            abstract class CheckOverlaps extends DefaultTask {
                @InputFiles abstract ConfigurableFileCollection getCandidates()
                @TaskAction run() {
                    List<RegularFile> files = candidates.files.toList()
                    println("Checking files \$files")
                    Set<RegularFile> overlaps = []
                    for (i in 0..< files.size()) {
                      for (j in (i+1)..< files.size()) {
                        def f1 = files[i].asFile
                        def f2 = files[j].asFile
                        if (f1 == f2) {
                            overlaps += f1
                        }
                      }
                    }
                    println("Found \${overlaps.size()} task output overlaps (checked \${files.size()} files)")
                    overlaps.each {
                        println("- '\$it'")
                    }
                }
            }

            def router = project.services.get(org.gradle.internal.isolated.models.IsolatedModelRouter)
                project.tasks.register("overlapsCheck", CheckOverlaps) {
                    Map<String, Provider<List<RegularFile>>> models =
                        router.getProjectModels(router.key("taskOutputs", List<RegularFile>), allprojects.collect { it.path })
                    println("Models: \$models")
                    candidates.from(files(models.values().flatten()))
            }
        """

        when:
        run "number", "overlapsCheck"

        then:
        executed(":number", ":sub:number", ":overlapsCheck")

        and:
        outputContains("Found 1 task output overlaps (checked 1 files)")
        file("build/out.txt").text == "3"
    }


}
