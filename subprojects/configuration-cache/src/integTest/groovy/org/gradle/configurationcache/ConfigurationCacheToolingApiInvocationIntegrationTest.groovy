/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.configurationcache

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.Action
import org.gradle.configurationcache.fixtures.SomeToolingModel
import org.gradle.configurationcache.fixtures.SomeToolingModelBuildAction
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.internal.Pair
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.tooling.BuildAction
import org.gradle.tooling.ProjectConnection

class ConfigurationCacheToolingApiInvocationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    @Override
    GradleExecuter createExecuter() {
        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
    }

    def "can run tasks via tooling API when configuration cache is enabled"() {
        buildFile << """
            plugins {
                id("java")
            }
            println("script log statement")
        """

        when:
        configurationCacheRun("assemble")

        then:
        outputContains("script log statement")

        when:
        configurationCacheRun("assemble")

        then:
        outputDoesNotContain("script log statement")
    }

    def "can enable configuration cache using gradle property in gradle.properties"() {
        withConfigurationCacheEnabledInGradleProperties()
        buildFile << """
            plugins {
                id("java")
            }
            println("script log statement")
        """

        when:
        run("assemble")

        then:
        outputContains("script log statement")

        when:
        run("assemble")

        then:
        outputDoesNotContain("script log statement")
    }

    def "can enable configuration cache using system property in build arguments"() {
        buildFile << """
            plugins {
                id("java")
            }
            println("script log statement")
        """

        when:
        run("assemble", ENABLE_SYS_PROP)

        then:
        outputContains("script log statement")

        when:
        run("assemble", ENABLE_SYS_PROP)

        then:
        outputDoesNotContain("script log statement")
    }

    def "can enable configuration cache using system property in build JVM arguments"() {
        buildFile << """
            plugins {
                id("java")
            }
            println("script log statement")
        """

        when:
        executer.withJvmArgs(ENABLE_SYS_PROP)
        run("assemble")

        then:
        outputContains("script log statement")

        when:
        executer.withJvmArgs(ENABLE_SYS_PROP)
        run("assemble")

        then:
        outputDoesNotContain("script log statement")
    }

    def "can use test launcher tooling api"() {

        given:
        withConfigurationCacheEnabledInGradleProperties()
        buildFile << """
            plugins {
                id("java")
            }
            ${mavenCentralRepository()}
            dependencies { testImplementation("junit:junit:4.13") }
            println("script log statement")
        """
        file("src/test/java/my/MyTest.java") << """
            package my;
            import org.junit.Test;
            public class MyTest {
                @Test public void test() {}
            }
        """

        when:
        runTestClasses("my.MyTest")

        then:
        outputContains("script log statement")

        when:
        runTestClasses("my.MyTest")

        then:
        outputDoesNotContain("script log statement")
    }

    def "configuration cache is disabled for direct model requests"() {
        given:
        withConfigurationCacheEnabledInGradleProperties()
        buildWithSomeToolingModelAndScriptLogStatement()

        when:
        def model = fetchModel()

        then:
        model.message == "It works!"
        outputContains("script log statement")

        when:
        def model2 = fetchModel()

        then:
        model2.message == "It works!"
        outputContains("script log statement")
    }

    def "configuration cache is disabled for client provided build actions"() {
        given:
        withConfigurationCacheEnabledInGradleProperties()
        buildWithSomeToolingModelAndScriptLogStatement()

        when:
        def model = runBuildAction(new SomeToolingModelBuildAction())

        then:
        model.message == "It works!"
        outputContains("script log statement")

        when:
        def model2 = runBuildAction(new SomeToolingModelBuildAction())

        then:
        model2.message == "It works!"
        outputContains("script log statement")
    }

    def "configuration cache is disabled for client provided phased build actions"() {
        given:
        withConfigurationCacheEnabledInGradleProperties()
        buildWithSomeToolingModelAndScriptLogStatement()

        when:
        def model = runPhasedBuildAction(new SomeToolingModelBuildAction(), new SomeToolingModelBuildAction())

        then:
        model.left.message == "It works!"
        model.right.message == "It works!"
        outputContains("script log statement")

        when:
        def model2 = runPhasedBuildAction(new SomeToolingModelBuildAction(), new SomeToolingModelBuildAction())

        then:
        model2.left.message == "It works!"
        model2.right.message == "It works!"
        outputContains("script log statement")
    }

    private void withConfigurationCacheEnabledInGradleProperties() {
        file("gradle.properties").text = ENABLE_GRADLE_PROP
    }

    private void buildWithSomeToolingModelAndScriptLogStatement() {
        withSomeToolingModelBuilderInBuildSrc()
        buildFile << """
            plugins {
                id("java")
            }
            println("script log statement")
            ${someToolingModelBuilderRegistration()}
        """
    }

    private void withSomeToolingModelBuilderInBuildSrc() {
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

    private static String someToolingModelBuilderRegistration() {
        """
        def registry = services.get(org.gradle.tooling.provider.model.ToolingModelBuilderRegistry)
        registry.register(new my.MyModelBuilder())
        """
    }

    SomeToolingModel fetchModel() {
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()

        def model = null
        usingToolingConnection(testDirectory) { connection ->
            model = connection.model(SomeToolingModel)
                .addJvmArguments(executer.jvmArgs)
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
        usingToolingConnection(testDirectory) { connection ->
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
        usingToolingConnection(testDirectory) { connection ->
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

        usingToolingConnection(testDirectory) { connection ->
            connection.newTestLauncher()
                .withJvmTestClasses(testClasses)
                .addJvmArguments(executer.jvmArgs)
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .run()
        }

        result = OutputScrapingExecutionResult.from(output.toString(), error.toString())
    }

    private void usingToolingConnection(File workingDir, Action<ProjectConnection> action) {
        def toolingApi = new ToolingApi(distribution, temporaryFolder)
        toolingApi.withConnector {
            it.forProjectDirectory(workingDir)
        }
        try {
            toolingApi.withConnection {
                action.execute(it)
            }
        } finally {
            if (GradleContextualExecuter.embedded) {
                System.clearProperty(StartParameterBuildOptions.ConfigurationCacheOption.PROPERTY_NAME)
            }
        }
    }

    class ToolingApiBackedGradleExecuter extends AbstractGradleExecuter {
        private final List<String> jvmArgs = []

        ToolingApiBackedGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
            super(distribution, testDirectoryProvider)
        }

        void withJvmArgs(String... args) {
            jvmArgs.addAll(args)
        }

        @Override
        void assertCanExecute() throws AssertionError {
        }

        @Override
        protected ExecutionResult doRun() {
            def output = new ByteArrayOutputStream()
            def error = new ByteArrayOutputStream()
            def args = allArgs
            args.remove("--no-daemon")
            usingToolingConnection(workingDir) { connection ->
                connection.newBuild()
                    .addJvmArguments(jvmArgs)
                    .withArguments(args)
                    .setStandardOutput(new TeeOutputStream(output, System.out))
                    .setStandardError(new TeeOutputStream(error, System.err))
                    .run()
            }
            return OutputScrapingExecutionResult.from(output.toString(), error.toString())
        }

        @Override
        protected ExecutionFailure doRunWithFailure() {
            throw new UnsupportedOperationException("not implemented yet")
        }
    }
}
