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

package org.gradle.launcher.continuous
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.*
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import org.spockframework.runtime.SpockTimeoutError
import spock.util.concurrent.PollingConditions

@Requires(TestPrecondition.JDK7_OR_LATER)
abstract public class AbstractContinuousIntegrationTest extends AbstractIntegrationSpec {

    private static final int WAIT_FOR_WATCHING_TIMEOUT_SECONDS = 30

    GradleHandle gradle

    private int standardOutputBuildMarker = 0
    private int errorOutputBuildMarker = 0

    int buildTimeout = WAIT_FOR_WATCHING_TIMEOUT_SECONDS

    public void turnOnDebug() {
        executer.withDebug(true)
        executer.withArgument("--no-daemon")
        buildTimeout = buildTimeout*100
    }

    public void cleanup() {
        stopGradle()
    }

    @Override
    protected ExecutionResult succeeds(String... tasks) {
        if (tasks) {
            runBuild(tasks)
        }
        if (gradle==null) {
            throw new UnexpectedBuildFailure("Gradle never started")
        }
        waitForBuild()
        if (result instanceof ExecutionFailure) {
            throw new UnexpectedBuildFailure("build was expected to succeed but failed")
        }
        result
    }

    ExecutionFailure fails(String... tasks) {
        if (tasks) {
            runBuild(tasks)
        }
        waitForBuild()
        if (!(result instanceof ExecutionFailure)) {
            throw new UnexpectedBuildFailure("build was expected to fail but succeeded")
        }
        failure = result as ExecutionFailure
        failure
    }

    private void runBuild(String... tasks) {
        stopGradle()
        standardOutputBuildMarker = 0
        errorOutputBuildMarker = 0
        gradle = executer.withTasks(tasks).withArgument("--watch").start()

    }

    private void waitForBuild() {
        // TODO: change this to 'tick' on any output change rather than waiting for the hold build to complete
        //       to be more adaptable to slow build environments without using huge timeouts
        new PollingConditions(initialDelay: 0.5).within(buildTimeout) {
            assert gradle.isRunning()
            assert buildOutputSoFar().endsWith(TextUtil.toPlatformLineSeparators("Waiting for a trigger. To exit 'continuous mode', use Ctrl+C.\n"))
        }

        def out = buildOutputSoFar()
        def err = gradle.errorOutput.substring(errorOutputBuildMarker)
        standardOutputBuildMarker = gradle.standardOutput.length()
        errorOutputBuildMarker = gradle.errorOutput.length()

        //noinspection GroovyConditionalWithIdenticalBranches
        result = out.contains("BUILD SUCCESSFUL") ? new OutputScrapingExecutionResult(out, err) : new OutputScrapingExecutionFailure(out, err)
    }

    void stopGradle() {
        gradle?.abort()
    }

    void noBuildTriggered(int waitSeconds = 3) {
        // TODO - change this general strategy to positively detect changes we are ignoring instead of asserting that a build doesn't happen in some time frame
        try {
            new PollingConditions(initialDelay: 0.5).within(waitSeconds) {
                assert !buildOutputSoFar().empty
            }
            throw new AssertionError("Expected build not to start, but started with output: " + buildOutputSoFar())
        } catch (SpockTimeoutError e) {
            // ok, what we want
        }
    }

    // should be private, but is accessed by closures in this class
    protected String buildOutputSoFar() {
        gradle.standardOutput.substring(standardOutputBuildMarker)
    }
}
