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
import org.gradle.configurationcache.fixtures.SomeToolingBuildAction
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
        """

        when:
        configurationCacheRun("assemble")

        then:
        outputContains("Configuration cache is an incubating feature.")

        when:
        configurationCacheRun("assemble")

        then:
        outputContains("Configuration cache is an incubating feature.")
        outputContains("Reusing configuration cache.")
    }

    def "can enable configuration cache using system property in build arguments"() {
        buildFile << """
            plugins {
                id("java")
            }
        """

        when:
        run("assemble", ENABLE_SYS_PROP)

        then:
        outputContains("Configuration cache is an incubating feature.")

        when:
        run("assemble", ENABLE_SYS_PROP)

        then:
        outputContains("Configuration cache is an incubating feature.")
        outputContains("Reusing configuration cache.")
    }

    def "can enable configuration cache using system property in build JVM arguments"() {
        buildFile << """
            plugins {
                id("java")
            }
        """

        when:
        executer.withJvmArgs(ENABLE_SYS_PROP)
        run("assemble")

        then:
        outputContains("Configuration cache is an incubating feature.")

        when:
        executer.withJvmArgs(ENABLE_SYS_PROP)
        run("assemble")

        then:
        outputContains("Configuration cache is an incubating feature.")
        outputContains("Reusing configuration cache.")
    }

    def "configuration cache is disabled for model building actions"() {

        given:
        file("buildSrc/src/main/groovy/my/My.groovy") << """
            package my

            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import org.gradle.api.Project

            class MyModelBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == "java.lang.String"
                }
                Object buildAll(String modelName, Project project) {
                    return "It works!"
                }
            }
        """.stripIndent()
        buildFile << """
            plugins {
                id("java")
            }
            def registry = services.get(org.gradle.tooling.provider.model.ToolingModelBuilderRegistry)
            registry.register(new my.MyModelBuilder())
        """
        file("gradle.properties") << """
            $ENABLE_GRADLE_PROP=true
        """.stripIndent()

        expect:
        usingToolingConnection(testDirectory) { connection ->
            2.times {
                def output = new ByteArrayOutputStream()
                def model = connection.action(new SomeToolingBuildAction())
                    .addJvmArguments(executer.jvmArgs)
                    .forTasks("assemble")
                    .setStandardOutput(output)
                    .setStandardError(System.err)
                    .run()
                assert model == "It works!"
                assert !output.toString().contains("Configuration cache is an incubating feature.")
            }
        }
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
