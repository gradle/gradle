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

package org.gradle.instantexecution

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

class InstantExecutionToolingApiInvocationIntegrationTest extends AbstractInstantExecutionIntegrationTest {
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
        instantRun("assemble")

        then:
        outputContains("Configuration cache is an incubating feature.")

        when:
        instantRun("assemble")

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
            def args = allArgs
            args.remove("--no-daemon")

            def connection = connector.connect()
            try {
                connection.newBuild()
                    .addJvmArguments(jvmArgs)
                    .withArguments(args)
                    .setStandardOutput(output)
                    .setStandardError(error)
                    .run()
            } finally {
                connection.close()
                if (GradleContextualExecuter.embedded) {
                    System.clearProperty(StartParameterBuildOptions.ConfigurationCacheOption.PROPERTY_NAME)
                }
            }
            return OutputScrapingExecutionResult.from(output.toString(), error.toString())
        }

        @Override
        protected ExecutionFailure doRunWithFailure() {
            throw new UnsupportedOperationException("not implemented yet")
        }
    }
}
