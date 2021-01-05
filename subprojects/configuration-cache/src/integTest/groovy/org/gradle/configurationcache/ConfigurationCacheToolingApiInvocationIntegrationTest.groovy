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

import org.gradle.api.Action
import org.gradle.configurationcache.fixtures.SomeToolingModelBuildAction
import org.gradle.configurationcache.fixtures.SomeToolingModel
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import spock.lang.Ignore

@Ignore("https://github.com/gradle/gradle-private/issues/3252")
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

        expect:
        usingToolingConnection(testDirectory) { connection ->
            2.times { runCount ->
                def output = new ByteArrayOutputStream()
                connection.newTestLauncher()
                    .withJvmTestClasses("my.MyTest")
                    .addJvmArguments(executer.jvmArgs)
                    .setStandardOutput(output)
                    .setStandardError(System.err)
                    .run()
                if (runCount == 0) {
                    assert output.toString().contains("script log statement")
                } else {
                    assert !output.toString().contains("script log statement")
                }
            }
        }
    }

    def "configuration cache is disabled for direct model requests"() {

        given:
        withConfigurationCacheEnabledInGradleProperties()
        buildWithSomeToolingModelAndScriptLogStatement()

        expect:
        usingToolingConnection(testDirectory) { connection ->
            2.times {
                def output = new ByteArrayOutputStream()
                def model = connection.model(SomeToolingModel)
                    .addJvmArguments(executer.jvmArgs)
                    .setStandardOutput(output)
                    .setStandardError(System.err)
                    .get()
                assert model.message == "It works!"
                assert output.toString().contains("script log statement")
            }
        }
    }

    def "configuration cache is disabled for client provided build actions"() {

        given:
        withConfigurationCacheEnabledInGradleProperties()
        buildWithSomeToolingModelAndScriptLogStatement()

        expect:
        usingToolingConnection(testDirectory) { connection ->
            2.times {
                def output = new ByteArrayOutputStream()
                def model = connection.action(new SomeToolingModelBuildAction())
                    .addJvmArguments(executer.jvmArgs)
                    .setStandardOutput(output)
                    .setStandardError(System.err)
                    .run()
                assert model.message == "It works!"
                assert output.toString().contains("script log statement")
            }
        }
    }

    def "configuration cache is disabled for client provided phased build actions"() {

        given:
        withConfigurationCacheEnabledInGradleProperties()
        buildWithSomeToolingModelAndScriptLogStatement()

        expect:
        usingToolingConnection(testDirectory) { connection ->
            2.times {
                def output = new ByteArrayOutputStream()
                String projectsLoaded = null
                String buildFinished = null
                connection.action()
                    .projectsLoaded(new SomeToolingModelBuildAction(), { SomeToolingModel model ->
                        projectsLoaded = model.message
                    })
                    .buildFinished(new SomeToolingModelBuildAction(), { SomeToolingModel model ->
                        buildFinished = model.message
                    })
                    .build()
                    .addJvmArguments(executer.jvmArgs)
                    .setStandardOutput(output)
                    .setStandardError(System.err)
                    .run()
                assert projectsLoaded == "It works!"
                assert buildFinished == "It works!"
                assert output.toString().contains("script log statement")
            }
        }
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

    private static void usingToolingConnection(File workingDir, Action<ProjectConnection> action) {
        def context = new IntegrationTestBuildContext()
        def connector = GradleConnector
            .newConnector()
            .forProjectDirectory(workingDir)
            .useGradleUserHomeDir(context.gradleUserHomeDir)
            .searchUpwards(false)
        if (GradleContextualExecuter.embedded) {
            connector.embedded(true).useClasspathDistribution()
        } else {
            connector.embedded(false).useInstallation(context.gradleHomeDir)
        }
        ProjectConnection connection = connector.connect()
        try {
            action.execute(connection)
        } finally {
            connection.close()
            if (GradleContextualExecuter.embedded) {
                System.clearProperty(StartParameterBuildOptions.ConfigurationCacheOption.PROPERTY_NAME)
            }
        }
    }

    static class ToolingApiBackedGradleExecuter extends AbstractGradleExecuter {
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
                    .setStandardOutput(output)
                    .setStandardError(error)
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
