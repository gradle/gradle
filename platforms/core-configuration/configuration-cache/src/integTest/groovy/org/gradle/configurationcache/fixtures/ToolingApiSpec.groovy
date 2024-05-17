/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.fixtures

import groovy.transform.SelfType
import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.Property
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.internal.Pair
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.BuildException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject

@SelfType(AbstractIntegrationSpec)
trait ToolingApiSpec {
    ToolingApiBackedGradleExecuter getToolingApiExecutor() {
        return (ToolingApiBackedGradleExecuter) getExecuter()
    }

    void withSomeToolingModelBuilderPluginInBuildSrc(String builderContent = "") {
        withSomeToolingModelBuilderPluginInChildBuild("buildSrc", builderContent)
    }

    void withSomeToolingModelBuilderPluginInChildBuild(String childBuildName, String builderContent = "") {
        addPluginBuildScript(childBuildName)
        addModelImplementation(childBuildName)

        addModelBuilderImplementation(childBuildName, """
            $builderContent
            def message = project.myExtension.message.get()
            return new MyModel(message)
        """)

        file("$childBuildName/src/main/groovy/my/MyExtension.groovy") << """
            import ${Property.name}

            interface MyExtension {
                Property<String> getMessage()
            }
        """

        addPluginImplementation(childBuildName, """
            def model = project.extensions.create("myExtension", MyExtension)
            model.message = "It works from project \${project.identityPath}"
        """)
    }

    void withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc() {
        addPluginBuildScript("buildSrc")
        addModelImplementation("buildSrc")

        addModelBuilderImplementation("buildSrc", """
            def message = "project \${project.path} classpath = \${project.configurations.implementation.files.size()}"
            return new MyModel(message)
        """)

        addPluginImplementation("buildSrc", """
            def implementation = project.configurations.create("implementation")
            implementation.attributes.attribute(${Attribute.name}.of("thing", String), "custom")
            def artifact = project.layout.buildDirectory.file("out.txt")
            implementation.outgoing.artifact(artifact)
        """)
    }

    void withSomeNullableToolingModelBuilderPluginInBuildSrc() {
        addPluginBuildScript("buildSrc")

        addModelBuilderImplementation("buildSrc", """
            return null
        """)

        addPluginImplementation("buildSrc")
    }

    private void addModelImplementation(String targetBuildName) {
        file("$targetBuildName/src/main/groovy/my/MyModel.groovy") << """
            package my

            class MyModel implements java.io.Serializable {
                private final String message
                MyModel(String message) { this.message = message }
                String getMessage() { message }
                String toString() { message }
            }
        """.stripIndent()
    }

    private void addModelBuilderImplementation(String targetBuildName, String content) {
        file("$targetBuildName/src/main/groovy/my/MyModelBuilder.groovy") << """
            package my

            import ${ToolingModelBuilder.name}
            import ${Project.name}

            class MyModelBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == "${SomeToolingModel.class.name}"
                }
                Object buildAll(String modelName, Project project) {
                    println("creating model for \$project")
                    $content
                }
            }
        """.stripIndent()
    }

    private void addPluginImplementation(String targetBuildName, String content = "") {
        file("$targetBuildName/src/main/groovy/my/MyPlugin.groovy") << """
            package my

            import ${Project.name}
            import ${Plugin.name}
            import ${Inject.name}
            import ${ToolingModelBuilderRegistry.name}

            abstract class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    $content
                    registry.register(new my.MyModelBuilder())
                }

                @Inject
                abstract ToolingModelBuilderRegistry getRegistry()
            }
        """.stripIndent()
    }

    void addPluginBuildScript(
        String targetBuildName,
        String pluginId = 'my.plugin',
        String implClass = 'my.MyPlugin'
    ) {
        file("$targetBuildName/build.gradle") << """
            plugins {
                id("groovy-gradle-plugin")
            }
            gradlePlugin {
                plugins {
                    test {
                        id = "$pluginId"
                        implementationClass = "$implClass"
                    }
                }
            }
        """
    }

    def <T> T fetchModel(Class<T> type = SomeToolingModel.class, String... tasks = null) {
        def model = null
        result = toolingApiExecutor.runBuildWithToolingConnection { connection ->
            def output = new ByteArrayOutputStream()
            def error = new ByteArrayOutputStream()
            def args = executer.allArgs
            args.remove("--no-daemon")

            model = connection.model(type)
                .forTasks(tasks)
                .withArguments(args)
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .get()
            OutputScrapingExecutionResult.from(output.toString(), error.toString())
        }
        return model
    }

    void fetchModelFails() {
        failure = toolingApiExecutor.runFailingBuildWithToolingConnection { connection ->
            def output = new ByteArrayOutputStream()
            def error = new ByteArrayOutputStream()
            def failure
            try {
                def args = executer.allArgs
                args.remove("--no-daemon")

                connection.model(SomeToolingModel)
                    .withArguments(args)
                    .setStandardOutput(new TeeOutputStream(output, System.out))
                    .setStandardError(new TeeOutputStream(error, System.err))
                    .get()
                throw new IllegalStateException("Expected build to fail but it did not.")
            } catch (BuildException t) {
                failure = OutputScrapingExecutionFailure.from(output.toString(), error.toString())
            }
            failure
        }
    }

    def <T> T runBuildAction(BuildAction<T> buildAction) {
        def model = null
        result = toolingApiExecutor.runBuildWithToolingConnection { connection ->
            def output = new ByteArrayOutputStream()
            def error = new ByteArrayOutputStream()
            def args = executer.allArgs.tap { remove("--no-daemon") }

            model = connection.action(buildAction)
                .withArguments(args)
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .run()
            OutputScrapingExecutionResult.from(output.toString(), error.toString())
        }
        return model
    }

    def <T, S> Pair<T, S> runPhasedBuildAction(BuildAction<T> projectsLoadedAction, BuildAction<S> modelAction, @DelegatesTo(BuildActionExecuter) Closure config = {}) {
        T projectsLoadedModel = null
        S buildModel = null
        result = toolingApiExecutor.runBuildWithToolingConnection { ProjectConnection connection ->
            def output = new ByteArrayOutputStream()
            def error = new ByteArrayOutputStream()
            def args = executer.allArgs
            args.remove("--no-daemon")


            def actionExecuter = connection.action()
                .projectsLoaded(projectsLoadedAction, { Object model ->
                    projectsLoadedModel = model
                }).buildFinished(modelAction, { Object model ->
                buildModel = model
            }).build()
            config.delegate = actionExecuter
            config.call()
            actionExecuter
                .withArguments(args)
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .run()
            OutputScrapingExecutionResult.from(output.toString(), error.toString())
        }
        return Pair.of(projectsLoadedModel, buildModel)
    }

    def runTestClasses(String... testClasses) {
        result = toolingApiExecutor.runBuildWithToolingConnection { connection ->
            def output = new ByteArrayOutputStream()
            def error = new ByteArrayOutputStream()
            def args = executer.allArgs
            args.remove("--no-daemon")

            connection.newTestLauncher()
                .withJvmTestClasses(testClasses)
                .addJvmArguments(executer.jvmArgs)
                .withArguments(args)
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .run()
            OutputScrapingExecutionResult.from(output.toString(), error.toString())
        }
    }
}
