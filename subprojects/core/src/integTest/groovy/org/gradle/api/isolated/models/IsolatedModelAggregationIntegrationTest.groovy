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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.util.internal.ToBeImplemented

class IsolatedModelAggregationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        // Required, because the Gradle API jar is computed once a day,
        // and the new API might not be visible for tests that require compilation
        // against that API, e.g. the cases like a plugin defined in an included build
        executer.requireOwnGradleUserHomeDir()
    }

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

            gradle.lifecycle.beforeProject { Project project ->
                def numberTask = project.tasks.register("number", SomeTask) {
                    number = project.name.length()
                    output = project.layout.buildDirectory.file("out.txt")
                }

                Provider<RegularFile> taskOutput = numberTask.flatMap { it.output }
                isolated.models.register("numberFile", RegularFile, taskOutput)
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

            tasks.register("sum", SummingTask) {
                Map<IsolatedProject, Provider<RegularFile>> numberFiles =
                    isolated.models.fromProjects("numberFile", RegularFile, allprojects)
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

    @ToBeImplemented
    def "settings plugin can collect models contributed by projects"() {
        file("build-logic/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """

        kotlinFile "build-logic/src/main/kotlin/my/ProjectApi.kt", """
            package my

            import org.gradle.api.isolated.models.IsolatedModelRouter
            import org.gradle.api.provider.Provider
            import javax.inject.Inject

            abstract class ProjectApi {
                @get:Inject
                abstract val isolatedModels: IsolatedModelRouter

                fun tag(provider: Provider<String>) {
                    isolatedModels.register("tag", String::class.java, provider)
                }
            }
        """

        kotlinFile "build-logic/src/main/kotlin/my-settings-plugin.settings.gradle.kts", """
            import org.gradle.kotlin.dsl.support.serviceOf

            gradle.lifecycle.beforeProject {
                extensions.create<my.ProjectApi>("projectApi")
            }

            val flowScope = serviceOf<FlowScope>()
            val flowProviders = serviceOf<FlowProviders>()

            flowScope.always(ModelCollectingFlow::class) {
                parameters {
                    buildResult = flowProviders.buildWorkResult.map { it.failure.isEmpty() }
                }
            }

            abstract class ModelCollectingFlow : FlowAction<ModelCollectingFlow.Parameters> {
                interface Parameters : FlowParameters {
                    @get:Input
                    val buildResult: Property<Boolean>

                    @get:Input
                    val tags: MapProperty<String, String>
                }

                // TODO: injecting the router directly leaves a hole of late discovery of models dependent on tasks
                //  these tasks cannot be executed at this point (in build-finished callback)
                @get:Inject
                abstract val router: IsolatedModelRouter

                override fun execute(parameters: Parameters) {
                    val tags: Map<IsolatedProject, Provider<String>> =
                        router.fromProjects("tag", String::class.java, listOf()) // listOf(":", ":a", ":b").map { project(it) }) // projects are not available in the Flow scope

                    println("Tags after build finished: \${tags.mapValues { it.value.orNull }.toSortedMap(compareBy { it.path })}")
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
        fails "help"

        then:
        failure.assertHasDescription("No service of type IsolatedModelRouter available in flow services.")
        // TODO: expected result
//        outputContains("Tags after build finished: {:=tag-root, :a=null, :b=tag-b}")
    }

    def "validating overlapping task outputs"() {
        buildFile file("build-logic/build.gradle"), """
            plugins {
                id 'groovy-gradle-plugin'
            }

            gradlePlugin {
                plugins {
                    myProjectPlugin {
                        id = 'my-project-plugin'
                        implementationClass = 'my.MyProjectPlugin'
                    }
                }
            }
        """

        groovyFile "build-logic/src/main/groovy/MyTaskOutputs.groovy", """
            import org.gradle.api.file.RegularFile
            class MyTaskOutputs {
                String taskName
                RegularFile outputFile

                MyTaskOutputs(taskName, outputFile) {
                    this.taskName = taskName
                    this.outputFile = outputFile
                }
            }
        """

        groovyFile "build-logic/src/main/groovy/NumberTask.groovy", """
            import org.gradle.api.DefaultTask
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Input
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import org.gradle.api.file.RegularFileProperty

            abstract class NumberTask extends DefaultTask {
                @Input abstract Property<Integer> getNumber()
                @OutputFile abstract RegularFileProperty getOutput()
                @TaskAction run() { output.get().asFile.text = number.get().toString() }
            }
        """

        groovyFile "build-logic/src/main/groovy/my/MyProjectPlugin.groovy", """
            package my

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import javax.inject.Inject
            import org.gradle.api.file.RegularFile
            import org.gradle.api.provider.Provider
            import java.util.Map
            import java.util.List
            import org.gradle.api.isolated.models.IsolatedModelRouter

            abstract class MyProjectPlugin implements Plugin<Project> {
                void apply(Project project) {
                    def task = project.tasks.register("number", NumberTask) {
                        number = project.name.length()
                        output = project.isolated.rootProject.projectDirectory.file("build/out.txt")
                    }

                    def taskPath = project.identityPath.child(task.name)
                    Provider<List<MyTaskOutputs>> taskOutputs = task.flatMap { it.output }
                        .map { new MyTaskOutputs(taskPath, it) }
                    project.isolated.models.register("taskOutputs", List<MyTaskOutputs>, taskOutputs)
                }
            }
        """

        createDirs("sub")

        settingsFile """
            pluginManagement {
                includeBuild("build-logic")
            }

            plugins {
                // Required to share the MyTaskOutputs type
                id("my-project-plugin") apply false
            }

            rootProject.name = "root"
            include(":sub")

            gradle.beforeProject { Project project ->
                project.plugins.apply("my-project-plugin")
            }
        """

        buildFile """
            abstract class CheckOverlaps extends DefaultTask {
                @Input abstract ListProperty<MyTaskOutputs> getCandidates()
                @TaskAction run() {
                    // Simulate overlap checking by checking for equality of the paths, instead of containment
                    Map<String, List<String>> tasksByOutputPath = [:]
                    candidates.get().each { taskOutputs ->
                        def outputPath = taskOutputs.outputFile.asFile.getAbsolutePath()
                        tasksByOutputPath.computeIfAbsent(outputPath) { [] }.add(taskOutputs.taskName)
                    }

                    println("There are " + tasksByOutputPath.size() + " output locations")
                    tasksByOutputPath.entrySet().each {
                        def tasks = it.value
                        if (tasks.size() > 1) {
                            println("Tasks " + tasks.toSorted() + " conflict for output location: " + it.key)
                        }
                    }
                }
            }

            project.tasks.register("overlapsCheck", CheckOverlaps) {
                Map<IsolatedProject, Provider<List<MyTaskOutputs>>> models =
                    isolated.models.fromProjects("taskOutputs", List<MyTaskOutputs>, allprojects)
                println("Models: \$models")
                models.values().each { candidates.add(it) }
            }
        """

        when:
        run "number", "overlapsCheck"

        then:
        executed(":number", ":sub:number", ":overlapsCheck")

        and:
        outputContains("Tasks [:number, :sub:number] conflict for output location")
        file("build/out.txt").text == "3"
    }

}
