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

import com.google.common.base.Joiner
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.measure.MeasuredOperation

import java.lang.ProcessBuilder.Redirect

/**
 * A performance test session that runs Gradle from the command line.
 *
 * This deliberate does not use our integration test fixtures, because they are not optimized
 * for low overhead.
 */
@CompileStatic
class ForkingGradleSession implements GradleSession {

    final GradleInvocationSpec invocation
    private final IntegrationTestBuildContext integrationTestBuildContext

    private ProcessBuilder stop

    ForkingGradleSession(GradleInvocationSpec invocation, IntegrationTestBuildContext integrationTestBuildContext) {
        this.invocation = invocation
        this.integrationTestBuildContext = integrationTestBuildContext
    }

    @Override
    void prepare() {
        cleanup()
    }

    @Override
    Action<MeasuredOperation> runner(BuildExperimentInvocationInfo invocationInfo, InvocationCustomizer invocationCustomizer) {
        def invocation = invocationCustomizer ? invocationCustomizer.customize(invocationInfo, this.invocation) : this.invocation
        return { MeasuredOperation measuredOperation ->
            def cleanTasks = invocation.cleanTasks
            if (cleanTasks) {
                System.out.println("Cleaning up by running Gradle tasks: " + Joiner.on(" ").join(cleanTasks));
                run(invocationInfo, invocation, cleanTasks)
            }
            def tasksToRun = invocation.tasksToRun
            System.out.println("Measuring Gradle tasks: " + Joiner.on(" ").join(tasksToRun));
            DurationMeasurementImpl.measure(measuredOperation) {
                run(invocationInfo, invocation, tasksToRun)
            }
        } as Action<MeasuredOperation>
    }

    @Override
    void cleanup() {
        if (stop != null) {
            stop.start().waitFor()
            stop = null
        }
    }

    private void run(BuildExperimentInvocationInfo invocationInfo, GradleInvocationSpec invocation, List<String> tasks) {
        String jvmArgs = invocation.jvmOpts.collect { '\'' + it + '\'' }.join(' ')
        Map<String, String> env = [:]
        List<String> args = []
        if (OperatingSystem.current().isWindows()) {
            args << "cmd.exe" << "/C"
        }
        args << new File(invocation.gradleDistribution.gradleHomeDir, "bin/gradle").absolutePath
        args << "--gradle-user-home" << invocationInfo.gradleUserHome.absolutePath
        args << "--no-search-upward"
        args << "--stacktrace"
        if (invocation.useDaemon) {
            args << "--daemon"
            args << "-Dorg.gradle.jvmargs=${jvmArgs}".toString()
        } else {
            args << "--no-daemon"
            args << '-Dorg.gradle.jvmargs'
            env.put("GRADLE_OPTS", jvmArgs)
        }
        args += invocation.args

        ProcessBuilder run = newProcessBuilder(invocationInfo, args + tasks, env)
        stop = newProcessBuilder(invocationInfo, args + "--stop", env)

        def exitCode = run.start().waitFor()
        if (exitCode != 0 && !invocation.expectFailure) {
            throw new IllegalStateException("Build failed, see ${invocationInfo.buildLog} for details")
        }
    }

    private static ProcessBuilder newProcessBuilder(BuildExperimentInvocationInfo invocationInfo, List<String> args, Map<String, String> env) {
        def builder = new ProcessBuilder()
            .directory(invocationInfo.projectDir)
            .redirectOutput(Redirect.appendTo(invocationInfo.buildLog))
            .redirectError(Redirect.appendTo(invocationInfo.buildLog))
            .command(args)
        builder.environment().putAll(env)
        builder
    }
}
