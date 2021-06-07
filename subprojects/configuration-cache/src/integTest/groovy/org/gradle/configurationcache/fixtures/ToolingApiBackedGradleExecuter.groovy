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
import org.gradle.api.Action
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.tooling.ProjectConnection

class ToolingApiBackedGradleExecuter extends AbstractGradleExecuter {
    final List<String> jvmArgs = []

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

    void usingToolingConnection(File workingDir, Action<ProjectConnection> action) {
        def toolingApi = new ToolingApi(distribution, testDirectoryProvider)
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
                System.clearProperty(StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME)
            }
        }
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        throw new UnsupportedOperationException("not implemented yet")
    }
}
