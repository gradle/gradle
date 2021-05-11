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

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.internal.Pair
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildAction

trait ToolingApiSpec {
    abstract GradleExecuter getExecuter()

    ToolingApiBackedGradleExecuter getToolingApiExecutor() {
        return (ToolingApiBackedGradleExecuter) getExecuter()
    }

    abstract void setResult(ExecutionResult executionResult)

    abstract TestFile file(Object... path)

    void withSomeToolingModelBuilderInBuildSrc() {
        file("buildSrc/src/main/groovy/my/My.groovy") << """
            package my

            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import org.gradle.api.Project

            class MyModelBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == "${SomeToolingModel.class.name}"
                }
                Object buildAll(String modelName, Project project) {
                    return new MyModel("It works!")
                }
            }

            class MyModel implements java.io.Serializable {
                private final String message
                MyModel(String message) { this.message = message }
                String getMessage() { message }
            }
        """.stripIndent()
    }

    String someToolingModelBuilderRegistration() {
        """
        def registry = services.get(org.gradle.tooling.provider.model.ToolingModelBuilderRegistry)
        registry.register(new my.MyModelBuilder())
        """
    }

    SomeToolingModel fetchModel() {
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()
        def args = executer.allArgs
        args.remove("--no-daemon")

        def model = null
        toolingApiExecutor.usingToolingConnection(testDirectory) { connection ->
            model = connection.model(SomeToolingModel)
                .addJvmArguments(executer.jvmArgs)
                .withArguments(args)
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .get()
        }
        result = OutputScrapingExecutionResult.from(output.toString(), error.toString())
        return model
    }

    SomeToolingModel runBuildAction(BuildAction<SomeToolingModel> buildAction) {
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()

        def model = null
        toolingApiExecutor.usingToolingConnection(testDirectory) { connection ->
            model = connection.action(buildAction)
                .addJvmArguments(executer.jvmArgs)
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .run()
        }
        result = OutputScrapingExecutionResult.from(output.toString(), error.toString())
        return model
    }

    Pair<SomeToolingModel, SomeToolingModel> runPhasedBuildAction(BuildAction<SomeToolingModel> projectsLoadedAction, BuildAction<SomeToolingModel> modelAction) {
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()

        def projectsLoadedModel = null
        def buildModel = null
        toolingApiExecutor.usingToolingConnection(testDirectory) { connection ->
            connection.action()
                .projectsLoaded(projectsLoadedAction, { SomeToolingModel model ->
                    projectsLoadedModel = model
                })
                .buildFinished(modelAction, { SomeToolingModel model ->
                    buildModel = model
                })
                .build()
                .addJvmArguments(executer.jvmArgs)
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .run()
        }
        result = OutputScrapingExecutionResult.from(output.toString(), error.toString())
        return Pair.of(projectsLoadedModel, buildModel)
    }

    def runTestClasses(String... testClasses) {
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()

        toolingApiExecutor.usingToolingConnection(testDirectory) { connection ->
            connection.newTestLauncher()
                .withJvmTestClasses(testClasses)
                .addJvmArguments(executer.jvmArgs)
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .run()
        }

        result = OutputScrapingExecutionResult.from(output.toString(), error.toString())
    }
}
