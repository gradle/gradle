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
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestDirectoryProvider

@CompileStatic
class GradleExecuterBackedSession implements GradleSession {

    final GradleInvocationSpec invocation

    private final TestDirectoryProvider testDirectoryProvider

    GradleExecuterBackedSession(GradleInvocationSpec invocation, TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider
        this.invocation = invocation
    }

    @Override
    void prepare() {
        cleanup()
    }


    Runnable runner(BuildExperimentInvocationInfo invocationInfo, InvocationCustomizer invocationCustomizer) {
        def runner = createExecuter(invocationInfo, invocationCustomizer, true)
        return {
            if (invocation.expectFailure) {
                runner.runWithFailure()
            } else {
                runner.run()
            }
        }
    }

    @Override
    void cleanup() {
        createExecuter(null, null, false).withTasks().withArgument("--stop").run()
    }

    private GradleExecuter createExecuter(BuildExperimentInvocationInfo invocationInfo, InvocationCustomizer invocationCustomizer, boolean withGradleOpts) {
        def invocation = invocationCustomizer ? invocationCustomizer.customize(invocationInfo, this.invocation) : this.invocation

        def executer = invocation.gradleDistribution.executer(testDirectoryProvider).
            requireOwnGradleUserHomeDir().
            requireGradleDistribution().
            requireIsolatedDaemons().
            expectDeprecationWarning().
            withStackTraceChecksDisabled().
            withArgument('-u').
            inDirectory(invocation.workingDirectory).
            withTasks(invocation.tasksToRun)

        if (withGradleOpts) {
            executer.withBuildJvmOpts('-XX:+PerfDisableSharedMem') // reduce possible jitter caused by slow /tmp
            executer.withBuildJvmOpts(invocation.jvmOpts)
        }

        invocation.args.each { executer.withArgument(it) }

        if (invocation.useDaemon) {
            executer.requireDaemon()
        }

        executer
    }
}
