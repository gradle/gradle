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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.ProjectConnection
import org.hamcrest.Matcher
import org.junit.Assume
import org.junit.Rule
import spock.lang.Retry
import spock.lang.Timeout

import static org.gradle.integtests.fixtures.RetryConditions.onBuildTimeout
import static org.hamcrest.CoreMatchers.anyOf
import static org.hamcrest.CoreMatchers.containsString
import static spock.lang.Retry.Mode.SETUP_FEATURE_CLEANUP

@Timeout(180)
@Retry(condition = { onBuildTimeout(instance, failure) }, mode = SETUP_FEATURE_CLEANUP, count = 2)
abstract class ContinuousBuildToolingApiSpecification extends ToolingApiSpecification {

    public static final String WAITING_MESSAGE = "Waiting for changes to input files of tasks..."
    public static final String BUILD_CANCELLED = "Build cancelled."
    public static final String BUILD_CANCELLED_AND_STOPPED = "the build was canceled"

    private static final boolean OS_IS_WINDOWS = OperatingSystem.current().isWindows()

    ExecutionResult result
    ExecutionFailure failure

    int buildTimeout = 30

    @Rule
    GradleBuildCancellation cancellationTokenSource
    TestResultHandler buildResult
    TestFile sourceDir

    ProjectConnection projectConnection


    def setup() {
        Assume.assumeTrue("Unsupported for the embedded runner", !GradleContextualExecuter.embedded)
        buildFile.text = "apply plugin: 'java'\n"
        sourceDir = file("src/main/java")
    }

    @Override
    <T> T withConnection(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        super.withConnection {
            projectConnection = it
            try {
                it.with(cl)
            } finally {
                projectConnection = null
            }
        }
    }

    public <T> T runBuild(List<String> tasks = ["build"], Closure<T> underBuild) {
        if (projectConnection) {
            buildResult = new TestResultHandler()

            cancellationTokenSource.withCancellation { CancellationToken token ->
                // this is here to ensure that the lastModified() timestamps actually change in between builds.
                // if the build is very fast, the timestamp of the file will not change and the JDK file watch service won't see the change.
                def initScript = file("init.gradle")
                initScript.text = """
                    |import java.lang.management.ManagementFactory

                    |gradle.rootProject {
                    |    try {
                    |        gradle.rootProject.buildDir.mkdir()
                    |        new File(gradle.rootProject.buildDir, "build.pid").text = ManagementFactory.getRuntimeMXBean().getName().split("@")[0]
                    |    } catch (Throwable t) {
                    |    }
                    |}

                    |def startAt = System.nanoTime()
                    |gradle.buildFinished {
                    |    long sinceStart = (System.nanoTime() - startAt) / 1000000L
                    |    if (sinceStart > 0 && sinceStart < 2000) {
                    |      sleep(2000 - sinceStart)
                    |    }
                    |}
                """.stripMargin()

                BuildLauncher launcher = projectConnection.newBuild()
                    .withArguments("--continuous", "-I", initScript.absolutePath)
                    .forTasks(tasks as String[])
                    .withCancellationToken(token)

                collectOutputs(launcher)

                customizeLauncher(launcher)

                launcher.run(buildResult)
                T t = underBuild.call()
                cancellationTokenSource.cancel()
                buildResult.finished(buildTimeout)
                t
            }
        } else {
            withConnection { runBuild(tasks, underBuild) }
        }
    }

    void customizeLauncher(BuildLauncher launcher) {

    }

    ExecutionResult succeeds() {
        waitForBuild()
        if (result instanceof ExecutionFailure) {
            throw new UnexpectedBuildFailure("build was expected to succeed but failed")
        }
        failure = null
        result
    }

    ExecutionFailure fails() {
        waitForBuild()
        if (!(result instanceof ExecutionFailure)) {
            throw new UnexpectedBuildFailure("build was expected to fail but succeeded")
        }
        failure = result as ExecutionFailure
        failure
    }

    private void waitForBuild() {
        long t0 = System.currentTimeMillis()
        ExecutionOutput executionOutput = waitUntilOutputContains containsString(WAITING_MESSAGE)
        println("Wait finishes: ${System.currentTimeMillis() - t0} ms")
        result = OutputScrapingExecutionResult.from(executionOutput.stdout, executionOutput.stderr)

        // Wait for extra 10s to wait for unexpected file change events to finish
        // https://github.com/gradle/gradle-private/issues/2976
        Thread.sleep(10 * 1000)
    }

    private ExecutionOutput waitUntilOutputContains(Matcher<String> expectedMatcher) {
        boolean success = false
        long pollingStartNanos = System.nanoTime()
        try {
            ConcurrentTestUtil.poll(buildTimeout, 0.5) {
                def out = stdout.toString()
                assert expectedMatcher.matches(out)
            }
            success = true
        } catch (Throwable t) {
            throw new RuntimeException("Timeout waiting for build to complete.", t)
        } finally {
            if (!success) {
                println "Polling lasted ${(long) ((System.nanoTime() - pollingStartNanos) / 1000000L)} ms measured with monotonic clock"
                requestJstackForBuildProcess()
            }
        }

        def executionOutput = new ExecutionOutput(stdout.toString(), stderr.toString())
        stdout.reset()
        stderr.reset()
        return executionOutput
    }

    def requestJstackForBuildProcess() {
        def pidFile = file("build/build.pid")
        if (pidFile.exists()) {
            def pid = pidFile.text
            def jdkBinDir = new File(System.getProperty("java.home"), "../bin").canonicalFile
            if (jdkBinDir.isDirectory() && new File(jdkBinDir, "jstack").exists()) {
                println "--------------------------------------------------"
                def jstackOutput = ["${jdkBinDir}/jstack", pid].execute().text
                println jstackOutput
                println "--------------------------------------------------"
            }
        }
    }

    boolean cancel() {
        cancellationTokenSource.cancel()
        waitUntilOutputContains anyOf(containsString(BUILD_CANCELLED), containsString(BUILD_CANCELLED_AND_STOPPED))
        true
    }

    void waitBeforeModification(File file) {
        long waitMillis = 100L
        if (OS_IS_WINDOWS && file.exists()) {
            // ensure that file modification time changes on windows
            long fileAge = System.currentTimeMillis() - file.lastModified()
            if (fileAge > 0L && fileAge < 900L) {
                waitMillis = 1000L - fileAge
            }
        }
        sleep(waitMillis)
    }

    class ExecutionOutput {
        String stdout
        String stderr

        ExecutionOutput(String stdout, String stderr) {
            this.stdout = stdout
            this.stderr = stderr
        }
    }
}
