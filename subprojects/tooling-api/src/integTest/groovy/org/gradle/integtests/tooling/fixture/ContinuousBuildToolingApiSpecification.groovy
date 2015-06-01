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

package org.gradle.integtests.tooling.fixture

import org.gradle.integtests.fixtures.executer.*
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.AutoCleanup
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Requires(TestPrecondition.JDK7_OR_LATER)
@TargetGradleVersion(GradleVersions.SUPPORTS_CONTINUOUS)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_CANCELLATION)
abstract class ContinuousBuildToolingApiSpecification extends ToolingApiSpecification {
    @AutoCleanup("shutdown")
    ExecutorService executorService = Executors.newCachedThreadPool()
    ByteArrayOutputStream stderr
    ByteArrayOutputStream stdout
    Runnable cancelTask
    Future<?> buildExecutionFuture
    ExecutionResult result
    ExecutionFailure failure
    int buildTimeout = 10

    def cancellationTokenSource = GradleConnector.newCancellationTokenSource()

    TestFile setupJavaProject() {
        buildFile.text = "apply plugin: 'java'"
        projectDir.createDir('src/main/java')
    }

    def cleanup() {
        cancelTask?.run()
        if (buildExecutionFuture) {
            try {
                // wait for finish and throw exceptions that happened during execution
                buildExecutionFuture.get(buildTimeout, TimeUnit.SECONDS)
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    def runContinuousBuild(String... tasks) {
        stderr = new ByteArrayOutputStream(512)
        stdout = new ByteArrayOutputStream(512)
        withConnection { ProjectConnection connection ->
            BuildLauncher launcher = connection.newBuild().withArguments("--continuous").forTasks(tasks)
            launcher.withCancellationToken(cancellationTokenSource.token())
            customizeLauncher(launcher)
            launcher.setStandardOutput(stdout)
            launcher.setStandardError(stderr)
            launcher.run()
        }
    }

    void customizeLauncher(BuildLauncher launcher) {

    }

    void runBuild(String... tasks) {
        cancelTask = cancellationTokenSource.&cancel
        buildExecutionFuture = executorService.submit {
            runContinuousBuild(tasks)
        }
    }

    ExecutionResult succeeds(String... tasks) {
        executeBuild(tasks)
        if (result instanceof ExecutionFailure) {
            throw new UnexpectedBuildFailure("build was expected to succeed but failed")
        }
        failure = null
        result
    }

    ExecutionFailure fails(String... tasks) {
        executeBuild(tasks)
        if (!(result instanceof ExecutionFailure)) {
            throw new UnexpectedBuildFailure("build was expected to fail but succeeded")
        }
        failure = result as ExecutionFailure
        failure
    }

    private void executeBuild(String... tasks) {
        if (tasks) {
            runBuild(tasks)
        } else if (buildExecutionFuture.isDone()) {
            throw new UnexpectedBuildFailure("Tooling API build connection has exited")
        }
        if (buildExecutionFuture == null) {
            throw new UnexpectedBuildFailure("Tooling API build connection never started")
        }
        waitForBuild()
    }

    private void waitForBuild() {
        new PollingConditions(initialDelay: 0.5).within(buildTimeout) {
            assert stdout.toString().contains("Waiting for changes to input files of tasks...")
        }

        def out = stdout.toString()
        stdout.reset()
        def err = stderr.toString()
        stderr.reset()

        result = out.contains("BUILD SUCCESSFUL") ? new OutputScrapingExecutionResult(out, err) : new OutputScrapingExecutionFailure(out, err)
    }

    protected List<String> getExecutedTasks() {
        assertHasResult()
        result.executedTasks
    }

    private assertHasResult() {
        assert result != null: "result is null, you haven't run succeeds()"
    }

    protected Set<String> getSkippedTasks() {
        assertHasResult()
        result.skippedTasks
    }

    protected List<String> getNonSkippedTasks() {
        executedTasks - skippedTasks
    }

    protected void executedAndNotSkipped(String... tasks) {
        tasks.each {
            assert it in executedTasks
            assert !skippedTasks.contains(it)
        }
    }
}
