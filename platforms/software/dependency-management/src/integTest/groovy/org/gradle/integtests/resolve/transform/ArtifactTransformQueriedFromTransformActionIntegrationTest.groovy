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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.modes.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

class ArtifactTransformQueriedFromTransformActionIntegrationTest extends AbstractIntegrationSpec {

    private static final String DEPRECATION = "Querying the output of one artifact transform from the action of another, without declaring that output as an input of the other. This behavior has been deprecated. " +
        "This will fail with an error in Gradle 10. " +
        "The output of artifact transform MakeRed, registered in root project 'root', was queried by the action of artifact transform MakeGreen, which did not declare it as an input. " +
        "Declare the output of artifact transform MakeRed as an input of artifact transform MakeGreen, using a parameter annotated with @InputFiles. " +
        "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#querying_transform_output_from_transform_action"

    private static final String UNDECLARED_TRANSFORM_INPUT = "Querying the output of an artifact transform from a task action without declaring it as a task input has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. " +
        "Declare the files or artifacts produced by the configuration using the transform as a task input to properly wire it into the execution plan. " +
        "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#undeclared_artifact_transform_input"

    private static final String RED_EXTERNAL = "[second1-1.0.jar.red, second2-1.0.jar.red, second3-1.0.jar.red, second4-1.0.jar.red]"
    private static final String RED_WITH_PROJECT = "[c.jar.red, second1-1.0.jar.red, second2-1.0.jar.red, second3-1.0.jar.red, second4-1.0.jar.red]"
    private static final String GREEN_RESULT = "result = [first1-1.0.jar.green, first2-1.0.jar.green, first3-1.0.jar.green, first4-1.0.jar.green, first5-1.0.jar.green, first6-1.0.jar.green, first7-1.0.jar.green, first8-1.0.jar.green]"

    /**
     * The root project resolves "firstPath" to green, using MakeGreen, defined by each test. The actions of MakeGreen
     * query the output of MakeRed, which transforms the artifacts of "secondPath", in different ways.
     */
    private void setupBuildWithTransforms() {
        createDirs("a")
        settingsFile << """
            rootProject.name = "root"
            include("a")
        """
        (1..8).each { publishBlueModule("first$it") }
        (1..4).each { publishBlueModule("second$it") }

        buildFile << """
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.api.services.BuildServiceRegistry
            import org.gradle.api.services.ServiceReference
            import org.gradle.workers.WorkParameters
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            def color = Attribute.of("color", String)

            interface NameParams extends TransformParameters {
                @Input
                Property<String> getName()
            }

            abstract class MakeRed implements TransformAction<NameParams> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    outputs.file(inputArtifact.get().asFile.name + ".red").text = "red"
                }
            }

            allprojects {
                ${mavenTestRepository()}

                def firstScope = configurations.dependencyScope("first")
                def secondScope = configurations.dependencyScope("second")
                configurations.resolvable("firstPath") {
                    extendsFrom(firstScope)
                    attributes.attribute(color, "blue")
                }
                configurations.resolvable("secondPath") {
                    extendsFrom(secondScope)
                    attributes.attribute(color, "blue")
                }

                dependencies {
                    attributesSchema {
                        attribute(color)
                    }
                    ${(1..8).collect { "first(\"test:first${it}:1.0\")" }.join("\n")}
                    ${(1..4).collect { "second(\"test:second${it}:1.0\")" }.join("\n")}
                    registerTransform(MakeRed) {
                        from.attribute(color, "blue")
                        to.attribute(color, "red")
                        parameters {
                            name = "red"
                        }
                    }
                }
            }

            project(":a") {
                tasks.register("warm") {
                    // Runs MakeRed on the same artifacts with the same parameters, so that when the transform action below queries
                    // the output of MakeRed, its execution is reused. Running MakeRed within the execution of another transform
                    // is not supported either, and can fail.
                    def redFiles = configurations.secondPath.incoming.artifactView {
                        attributes.attribute(color, "red")
                    }.files
                    doLast {
                        println("warm = " + redFiles.files*.name.sort())
                    }
                }
            }

            def redView = configurations.secondPath.incoming.artifactView {
                attributes.attribute(color, "red")
            }.files

            abstract class FilesService implements BuildService<FilesService.Params> {
                interface Params extends BuildServiceParameters {
                    ConfigurableFileCollection getFiles()
                }

                List<String> names() {
                    return parameters.files.files*.name.sort()
                }
            }
            def filesService = gradle.sharedServices.registerIfAbsent("files", FilesService) {
                parameters.files.from(redView)
            }

            interface FilesSourceParams extends ValueSourceParameters {
                ConfigurableFileCollection getFiles()
            }
            abstract class FilesSource implements ValueSource<List<String>, FilesSourceParams> {
                List<String> obtain() {
                    return parameters.files.files*.name.sort()
                }
            }
        """
    }

    /**
     * Adds the artifact of project ":c" to the artifacts of "secondPath".
     */
    private void addProjectArtifact() {
        createDirs("c")
        settingsFile << """
            include("c")
        """
        buildFile << """
            project(":c") {
                file("c.jar").text = "c"
                configurations.consumable("outgoing") {
                    attributes.attribute(color, "blue")
                    outgoing.artifact(file("c.jar"))
                }
            }

            dependencies {
                second(project(":c"))
            }
        """
    }

    private void publishBlueModule(String name) {
        mavenRepo.module("test", name, "1.0")
            .adhocVariants()
            .variant("runtime", [color: "blue"])
            .withModuleMetadata()
            .publish()
    }

    /**
     * Defines MakeGreen, whose actions print the result of the given query, and the task that resolves the output of MakeGreen.
     */
    private void transformActionQueries(String query, String params = "", String members = "", String setParams = "") {
        buildFile << """
            interface GreenParams extends NameParams {
                ${params}
            }

            abstract class MakeGreen implements TransformAction<GreenParams> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                ${members}

                void transform(TransformOutputs outputs) {
                    println("transform action saw " + ${query})
                    outputs.file(inputArtifact.get().asFile.name + ".green").text = "green"
                }
            }

            dependencies {
                registerTransform(MakeGreen) {
                    from.attribute(color, "blue")
                    to.attribute(color, "green")
                    parameters {
                        // Ensure the transform action runs rather than reusing a previous result
                        name = UUID.randomUUID().toString()
                        ${setParams}
                    }
                }
            }

            def greenView = configurations.firstPath.incoming.artifactView {
                attributes.attribute(color, "green")
            }.files

            tasks.register("consume") {
                dependsOn(":a:warm")
                def untransformed = configurations.secondPath.incoming.files
                doLast {
                    // Resolve the files, but not the transformed files, so that a transform action may be the first to require MakeRed.
                    // Calculating the file of a project artifact requires the lock of its project, which a transform action may not acquire.
                    println("untransformed = " + untransformed.files*.name.sort())
                    println("result = " + greenView.files*.name.sort())
                }
            }
        """
    }

    private void runWithParallel(boolean parallel, String task) {
        if (parallel) {
            executer.withArgument("--parallel")
        }
        runTransformActionsOneAtATime()
        run(task)
    }

    /**
     * Avoids a deadlock between transform actions running at the same time, which still needs to be addressed.
     */
    private void runTransformActionsOneAtATime() {
        executer.withArgument("--max-workers=1")
    }

    /**
     * Defines a task that submits worker API actions, which print the files they query through their parameters.
     */
    private void workActionQueries(String setParams) {
        buildFile << """
            interface ReadParams extends WorkParameters {
                Property<FilesService> getService()
                ListProperty<String> getNames()
            }

            abstract class ReadFiles implements WorkAction<ReadParams> {
                void execute() {
                    def names = parameters.service.present ? parameters.service.get().names() : parameters.names.get()
                    println("work action saw " + names)
                }
            }

            abstract class Submit extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @Internal
                abstract Property<FilesService> getService()

                @Internal
                abstract ListProperty<String> getNames()

                @Internal
                abstract Property<Object> getGraph()

                @TaskAction
                void submit() {
                    // Resolve the graph, but not the files, so that a work action is the first to require MakeRed
                    graph.get()
                    def queue = workerExecutor.noIsolation()
                    def service = this.service
                    def names = this.names
                    (1..4).each {
                        queue.submit(ReadFiles) { parameters ->
                            if (service.present) {
                                parameters.service.set(service)
                            } else {
                                parameters.names.set(names)
                            }
                        }
                    }
                    // Do not await the work actions, as the task would keep its project lock while waiting for them
                }
            }

            tasks.register("submit", Submit) {
                dependsOn(":a:warm")
                usesService(filesService)
                graph = configurations.secondPath.incoming.resolutionResult.rootComponent
                ${setParams}
            }
        """
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @UnsupportedWithConfigurationCache(
        because = "Accessing a project that no task declared as an input fails, as the project is not scheduled",
        iterationMatchers = [".*project=true.*"]
    )
    @ToBeFixedForIsolatedProjects(because = "test setup uses allprojects { ... } and project(...) { ... }")
    def "deprecated when a transform action queries the output of a transform through a build service it is given (parallel=#parallel, project=#project)"() {
        // A transform of a project artifact isolates its parameters through another caller than a transform of an external artifact.
        // The route the action takes to the files does not depend on the kind of artifact, so only this test covers project artifacts.
        setupBuildWithTransforms()
        if (project) {
            addProjectArtifact()
        }
        transformActionQueries(
            "parameters.service.get().names()",
            """
                @Internal
                Property<FilesService> getService()
            """,
            "",
            "service = filesService"
        )

        when:
        executer.expectDocumentedDeprecationWarning(DEPRECATION)
        if (project) {
            // The transform of the project artifact is executed outside of the work graph, without being declared as an input of the task
            executer.expectDocumentedDeprecationWarning(UNDECLARED_TRANSFORM_INPUT)
        }
        runWithParallel(parallel, "consume")

        then:
        outputContains("transform action saw " + (project ? RED_WITH_PROJECT : RED_EXTERNAL))
        outputContains(GREEN_RESULT)

        where:
        parallel | project
        true     | false
        false    | false
        true     | true
        false    | true
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @ToBeFixedForConfigurationCache(because = "A build service referenced by the parameters of a transform has no value when loaded from the configuration cache, when the type of the parameters extends another type whose properties are set")
    def "deprecated when a transform action queries the output of a transform through a build service referenced by its parameters (parallel=#parallel)"() {
        setupBuildWithTransforms()
        transformActionQueries(
            "parameters.service.get().names()",
            """
                @ServiceReference("files")
                Property<FilesService> getService()
            """
        )

        when:
        executer.expectDocumentedDeprecationWarning(DEPRECATION)
        runWithParallel(parallel, "consume")

        then:
        outputContains("transform action saw " + RED_EXTERNAL)
        outputContains(GREEN_RESULT)

        where:
        parallel << [true, false]
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @UnsupportedWithConfigurationCache(because = "Only the build services referenced by the work graph are registered when loaded from the configuration cache")
    def "deprecated when a transform action queries the output of a transform through a build service looked up from an injected registry (parallel=#parallel)"() {
        setupBuildWithTransforms()
        transformActionQueries(
            "(services.registrations.getByName(\"files\").service.get() as FilesService).names()",
            "",
            """
                @Inject
                abstract BuildServiceRegistry getServices()
            """
        )

        when:
        executer.expectDocumentedDeprecationWarning(DEPRECATION)
        runWithParallel(parallel, "consume")

        then:
        outputContains("transform action saw " + RED_EXTERNAL)
        outputContains(GREEN_RESULT)

        where:
        parallel << [true, false]
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @UnsupportedWithConfigurationCache(because = "Build services are recreated from their parameters when loaded from the configuration cache, without their state")
    def "deprecated when a transform action queries the output of a transform held in the state of a build service it is given (parallel=#parallel)"() {
        setupBuildWithTransforms()
        buildFile << """
            abstract class StateService implements BuildService<BuildServiceParameters.None> {
                FileCollection files
            }
            def stateService = gradle.sharedServices.registerIfAbsent("state", StateService) {
            }
            stateService.get().files = redView
        """
        transformActionQueries(
            "parameters.service.get().files.files*.name.sort()",
            """
                @Internal
                Property<StateService> getService()
            """,
            "",
            "service = stateService"
        )

        when:
        executer.expectDocumentedDeprecationWarning(DEPRECATION)
        runWithParallel(parallel, "consume")

        then:
        outputContains("transform action saw " + RED_EXTERNAL)
        outputContains(GREEN_RESULT)

        where:
        parallel << [true, false]
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @ToBeFixedForIsolatedProjects(because = "test setup uses allprojects { ... } and project(...) { ... }")
    def "deprecated when the constructor of a transform action queries the output of a transform through a build service it is given (parallel=#parallel)"() {
        setupBuildWithTransforms()
        transformActionQueries(
            "names",
            """
                @Internal
                Property<FilesService> getService()
            """,
            """
                private final List<String> names

                MakeGreen() {
                    names = parameters.service.get().names()
                }
            """,
            "service = filesService"
        )

        when:
        executer.expectDocumentedDeprecationWarning(DEPRECATION)
        runWithParallel(parallel, "consume")

        then:
        outputContains("transform action saw " + RED_EXTERNAL)
        outputContains(GREEN_RESULT)

        where:
        parallel << [true, false]
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @ToBeFixedForIsolatedProjects(because = "test setup uses allprojects { ... } and project(...) { ... }")
    def "deprecated when a transform action queries the output of a transform that a task also uses (parallel=#parallel, tasks=#tasks)"() {
        // Whether the task isolates the parameters of MakeRed before the transform action queries its output depends on scheduling
        setupBuildWithTransforms()
        transformActionQueries(
            "parameters.service.get().names()",
            """
                @Internal
                Property<FilesService> getService()
            """,
            "",
            "service = filesService"
        )
        buildFile << """
            tasks.register("red") {
                inputs.files(redView)
                doLast {
                    println("red = " + redView.files*.name.sort())
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning(DEPRECATION)
        if (parallel) {
            executer.withArgument("--parallel")
        }
        runTransformActionsOneAtATime()
        run(*tasks)

        then:
        outputContains("transform action saw " + RED_EXTERNAL)
        outputContains(GREEN_RESULT)

        where:
        parallel | tasks
        true     | ["consume", "red"]
        true     | ["red", "consume"]
        false    | ["consume", "red"]
        false    | ["red", "consume"]
    }

    def "deprecated when a settings transform action queries the output of a settings transform through a build service it is given"() {
        (1..2).each { publishBlueModule("first$it") }
        (1..2).each { publishBlueModule("second$it") }
        settingsFile << """
            import org.gradle.api.services.BuildServiceParameters

            def color = Attribute.of("color", String)

            interface NameParams extends TransformParameters {
                @Input
                Property<String> getName()
            }

            abstract class MakeRed implements TransformAction<NameParams> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    outputs.file(inputArtifact.get().asFile.name + ".red").text = "red"
                }
            }

            abstract class FilesService implements BuildService<FilesService.Params> {
                interface Params extends BuildServiceParameters {
                    ConfigurableFileCollection getFiles()
                }

                List<String> names() {
                    return parameters.files.files*.name.sort()
                }
            }

            interface GreenParams extends NameParams {
                @Internal
                Property<FilesService> getService()
            }

            abstract class MakeGreen implements TransformAction<GreenParams> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    println("transform action saw " + parameters.service.get().names())
                    outputs.file(inputArtifact.get().asFile.name + ".green").text = "green"
                }
            }

            buildscript.repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            def firstPath = buildscript.configurations.detachedConfiguration(
                buildscript.dependencies.create("test:first1:1.0"),
                buildscript.dependencies.create("test:first2:1.0")
            )
            firstPath.attributes.attribute(color, "blue")
            def secondPath = buildscript.configurations.detachedConfiguration(
                buildscript.dependencies.create("test:second1:1.0"),
                buildscript.dependencies.create("test:second2:1.0")
            )
            secondPath.attributes.attribute(color, "blue")

            def redView = secondPath.incoming.artifactView {
                attributes.attribute(color, "red")
            }.files
            def filesService = gradle.sharedServices.registerIfAbsent("files", FilesService) {
                parameters.files.from(redView)
            }

            buildscript.dependencies {
                attributesSchema {
                    attribute(color)
                }
                registerTransform(MakeRed) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                    parameters {
                        name = "red"
                    }
                }
                registerTransform(MakeGreen) {
                    from.attribute(color, "blue")
                    to.attribute(color, "green")
                    parameters {
                        name = UUID.randomUUID().toString()
                        service = filesService
                    }
                }
            }

            def greenView = firstPath.incoming.artifactView {
                attributes.attribute(color, "green")
            }.files
            println("result = " + greenView.files*.name.sort())
        """

        when:
        executer.expectDocumentedDeprecationWarning(DEPRECATION.replace("root project 'root'", "build ':'"))
        runTransformActionsOneAtATime()
        run("help")

        then:
        outputContains("transform action saw [second1-1.0.jar.red, second2-1.0.jar.red]")
        outputContains("result = [first1-1.0.jar.green, first2-1.0.jar.green]")
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @ToBeFixedForIsolatedProjects(because = "test setup uses allprojects { ... } and project(...) { ... }")
    def "transform action may query the output of a transform through a file collection parameter that is not an input (parallel=#parallel)"() {
        // The file collection is resolved when the parameters of MakeGreen are isolated, before its actions run
        setupBuildWithTransforms()
        transformActionQueries(
            "parameters.files.files*.name.sort()",
            """
                @Internal
                ConfigurableFileCollection getFiles()
            """,
            "",
            "files.from(redView)"
        )

        when:
        runWithParallel(parallel, "consume")

        then:
        outputContains("transform action saw " + RED_EXTERNAL)
        outputContains(GREEN_RESULT)

        where:
        parallel << [true, false]
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @ToBeFixedForIsolatedProjects(because = "test setup uses allprojects { ... } and project(...) { ... }")
    def "transform action may query the output of a transform through a value source in a property parameter (parallel=#parallel)"() {
        // The value source is obtained when the parameters of MakeGreen are isolated, before its actions run
        setupBuildWithTransforms()
        buildFile << """
            abstract class JoinedFilesSource implements ValueSource<String, FilesSourceParams> {
                String obtain() {
                    return parameters.files.files*.name.sort().toString()
                }
            }
        """
        transformActionQueries(
            "parameters.names.get()",
            """
                @Internal
                Property<String> getNames()
            """,
            "",
            "names = providers.of(JoinedFilesSource) { spec -> spec.parameters.files.from(redView) }"
        )

        when:
        runWithParallel(parallel, "consume")

        then:
        outputContains("transform action saw " + RED_EXTERNAL)
        outputContains(GREEN_RESULT)

        where:
        parallel << [true, false]
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @ToBeFixedForIsolatedProjects(because = "test setup uses allprojects { ... } and project(...) { ... }")
    def "transform action may query the output of a transform through a value source in a list property parameter (parallel=#parallel)"() {
        // The value source is obtained when the parameters of MakeGreen are isolated, before its actions run
        setupBuildWithTransforms()
        transformActionQueries(
            "parameters.names.get()",
            """
                @Internal
                ListProperty<String> getNames()
            """,
            "",
            "names = providers.of(FilesSource) { spec -> spec.parameters.files.from(redView) }"
        )

        when:
        runWithParallel(parallel, "consume")

        then:
        outputContains("transform action saw " + RED_EXTERNAL)
        outputContains(GREEN_RESULT)

        where:
        parallel << [true, false]
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @ToBeFixedForIsolatedProjects(because = "test setup uses allprojects { ... } and project(...) { ... }")
    def "transform action may query the output of a transform through a value source it creates (parallel=#parallel)"() {
        // The value source is obtained by the transform action, but its files were resolved when the parameters of MakeGreen were isolated
        setupBuildWithTransforms()
        transformActionQueries(
            "namesFromValueSource()",
            """
                @Internal
                ConfigurableFileCollection getFiles()
            """,
            """
                @Inject
                abstract ProviderFactory getProviders()

                List<String> namesFromValueSource() {
                    def files = parameters.files
                    return providers.of(FilesSource) { spec -> spec.parameters.files.from(files) }.get()
                }
            """,
            "files.from(redView)"
        )

        when:
        runWithParallel(parallel, "consume")

        then:
        outputContains("transform action saw " + RED_EXTERNAL)
        outputContains(GREEN_RESULT)

        where:
        parallel << [true, false]
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @ToBeFixedForIsolatedProjects(because = "test setup uses allprojects { ... } and project(...) { ... }")
    def "worker API action may query the output of a transform through a build service it is given (parallel=#parallel)"() {
        // Worker API actions may acquire project locks, as the task releases its project lock when it waits for them after its actions
        setupBuildWithTransforms()
        workActionQueries("service = filesService")

        when:
        runWithParallel(parallel, "submit")

        then:
        outputContains("work action saw " + RED_EXTERNAL)

        where:
        parallel << [true, false]
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @ToBeFixedForIsolatedProjects(because = "test setup uses allprojects { ... } and project(...) { ... }")
    def "worker API action may query the output of a transform through a value source it is given (parallel=#parallel)"() {
        // Worker API actions may acquire project locks, as the task releases its project lock when it waits for them after its actions
        setupBuildWithTransforms()
        workActionQueries("names = providers.of(FilesSource) { spec -> spec.parameters.files.from(redView) }")

        when:
        runWithParallel(parallel, "submit")

        then:
        outputContains("work action saw " + RED_EXTERNAL)

        where:
        parallel << [true, false]
    }

    def "transform action can use the output of another transform declared as an input"() {
        ["foo", "bar"].each {
            mavenRepo.module("org", it, "1")
                .hasType("txt")
                .adhocVariants()
                .variant("runtime", [color: "blue"])
                .withModuleMetadata()
                .publish()
        }
        // The code between the markers is the example of the upgrading guide, see
        // upgrading_version_9.adoc#querying_transform_output_from_transform_action. Keep the two identical,
        // except for escaping in this Groovy string.
        buildKotlinFile << """
            ${mavenTestRepository(GradleDsl.KOTLIN)}

            abstract class MakeRed : TransformAction<TransformParameters.None> {
                @get:InputArtifact
                abstract val inputArtifact: Provider<FileSystemLocation>

                override fun transform(outputs: TransformOutputs) {
                    val input = inputArtifact.get().asFile
                    input.copyTo(outputs.file(input.name + ".red"))
                }
            }

            // Start of upgrading guide example
            val color = Attribute.of("color", String::class.java)

            interface Parameters : TransformParameters {
                @get:InputFiles
                val redFiles: ConfigurableFileCollection
            }

            abstract class MakeGreen : TransformAction<Parameters> {
                @get:InputArtifact
                abstract val inputArtifact: Provider<FileSystemLocation>

                override fun transform(outputs: TransformOutputs) {
                    val input = inputArtifact.get().asFile
                    // Use the files declared on the parameters, rather than files
                    // reached in another way, like through a build service
                    val redNames = parameters.redFiles.files.map { it.name }
                    if (input.name + ".red" in redNames) {
                        input.copyTo(outputs.file(input.name + ".green"))
                    }
                }
            }

            val deps = configurations.dependencyScope("deps")
            val resolved = configurations.resolvable("resolved") {
                extendsFrom(deps)
                attributes.attribute(color, "blue")
            }

            dependencies {
                attributesSchema {
                    attribute(color)
                }
                deps("org:bar:1")
                registerTransform(MakeRed::class) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                }
                registerTransform(MakeGreen::class) {
                    from.attribute(color, "blue")
                    to.attribute(color, "green")
                    parameters {
                        // Declare the output of MakeRed as an input
                        redFiles.from(resolved.map {
                            it.incoming.artifactView {
                                attributes.attribute(color, "red")
                            }.files
                        })
                    }
                }
            }
            // End of upgrading guide example

            val inputs = configurations.dependencyScope("inputs")
            val inputsPath = configurations.resolvable("inputsPath") {
                extendsFrom(inputs)
                attributes.attribute(color, "blue")
            }

            dependencies {
                inputs("org:foo:1")
                inputs("org:bar:1")
            }

            abstract class PrintNames : DefaultTask() {
                @get:InputFiles
                abstract val files: ConfigurableFileCollection

                @TaskAction
                fun printNames() {
                    println("result = " + files.files.map { it.name })
                }
            }

            tasks.register<PrintNames>("printGreen") {
                files.from(inputsPath.map {
                    it.incoming.artifactView {
                        attributes.attribute(color, "green")
                    }.files
                })
            }
        """

        when:
        run("printGreen")

        then:
        // Only the artifact that the other configuration also contains is kept
        outputContains("result = [bar-1.txt.green]")
    }

}
