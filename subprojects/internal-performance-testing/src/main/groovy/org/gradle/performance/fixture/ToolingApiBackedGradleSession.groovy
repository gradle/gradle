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

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector

@CompileStatic
class ToolingApiBackedGradleSession implements GradleSession {

    final GradleInvocationSpec invocation

    private final GradleExecuterBackedSession executerBackedSession
    private ProjectConnection projectConnection

    ToolingApiBackedGradleSession(GradleInvocationSpec invocation) {
        this.invocation = invocation
        this.executerBackedSession = new GradleExecuterBackedSession(invocation, IntegrationTestBuildContext.INSTANCE)
    }

    @Override
    void prepare() {
        executerBackedSession.prepare()

        DefaultGradleConnector connector = GradleConnector.newConnector() as DefaultGradleConnector
        projectConnection = connector
                .daemonBaseDir(new File(invocation.workingDirectory, "daemon"))
                .forProjectDirectory(invocation.workingDirectory)
                .useInstallation(new PerformanceTestGradleDistribution(invocation.gradleDistribution, invocation.workingDirectory).gradleHomeDir)
                .useGradleUserHomeDir(new File(invocation.workingDirectory, "gradleUserHome"))
                .connect()
    }


    Action<MeasuredOperation> runner(final BuildExperimentInvocationInfo invocationInfo, InvocationCustomizer invocationCustomizer) {
        def invocation = invocationCustomizer ? invocationCustomizer.customize(invocationInfo, this.invocation) : this.invocation

        BuildLauncher buildLauncher = projectConnection.newBuild()
            .withArguments(invocation.args + ["-u"] as String[])
            .forTasks(invocation.tasksToRun as String[])
            .setJvmArguments(invocation.jvmOpts as String[])
            .setStandardOutput(System.out)
            .setStandardError(System.err)

        return { MeasuredOperation measuredOperation ->
            DurationMeasurementImpl.measure(measuredOperation, new Runnable() {
                @Override
                void run() {
                    buildLauncher.run()
                }
            })
        } as Action<MeasuredOperation>
    }

    @Override
    void cleanup() {
        projectConnection?.close()
        executerBackedSession.cleanup()
    }

}
