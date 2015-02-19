/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

class ToolingApiBackedGradleSession implements GradleSession {

    final GradleInvocationSpec invocation

    private final TestDirectoryProvider testDirectoryProvider
    private final GradleExecuterBackedSession executerBackedSession
    private ProjectConnection projectConnection
    private BuildLauncher buildLauncher

    ToolingApiBackedGradleSession(GradleInvocationSpec invocation, TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider
        this.invocation = invocation
        this.executerBackedSession = new GradleExecuterBackedSession(invocation, testDirectoryProvider)
    }

    @Override
    void prepare() {
        executerBackedSession.prepare()
        projectConnection = GradleConnector.newConnector()
                .forProjectDirectory(invocation.workingDirectory)
                .useInstallation(invocation.gradleDistribution.gradleHomeDir)
                .connect()

        buildLauncher = projectConnection.newBuild()
                .withArguments(invocation.args + ["-u"] as String[])
                .forTasks(invocation.tasksToRun as String[])
                .setJvmArguments(invocation.gradleOpts as String[])
                .setStandardOutput(System.out)
                .setStandardError(System.err)
    }

    @Override
    void run() {
        buildLauncher.run()
    }

    @Override
    void cleanup() {
        projectConnection?.close()
        executerBackedSession.cleanup()
    }

}
