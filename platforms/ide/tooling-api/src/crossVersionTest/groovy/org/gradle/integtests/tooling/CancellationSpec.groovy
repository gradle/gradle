/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.initialization.BuildCancellationToken
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.BuildCancelledException
import org.gradle.util.GradleVersion
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher
import org.junit.Rule

import java.util.concurrent.CountDownLatch

abstract class CancellationSpec extends ToolingApiSpecification {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
        settingsFile << '''
rootProject.name = 'cancelling'
'''
    }

    void setupCancelInSettingsBuild() {
        settingsFile << waitForCancel()
        buildFile << """
throw new RuntimeException("should not run")
"""
    }

    void setupCancelInConfigurationBuild() {
        settingsFile << '''
include 'sub'
rootProject.name = 'cancelling'
'''
        buildFile << waitForCancel()

        projectDir.file('sub/build.gradle') << """
throw new RuntimeException("should not run")
"""
    }

    String waitForCancel() {
        return """
// Need to block until the cancel request has been received by the build process before proceeding

def cancellationToken = gradle.services.get(${BuildCancellationToken.name}.class)
def latch = new ${CountDownLatch.name}(1)

cancellationToken.addCallback {
    latch.countDown()
}

// Signal to test that callback has been registered
${server.callFromBuild("registered")}
// Wait until cancel request received by build process
latch.await()
"""

    }

    void buildWasCancelled(TestResultHandler resultHandler, String failureMessage = 'Could not execute build using') {
        resultHandler.assertFailedWith(BuildCancelledException)
        assert resultHandler.failure.message.startsWith(failureMessage)

        if (targetIsGradle51OrLater()) {
            verifyBuildCancelledExceptionMessage(resultHandler)
        }

        def failure = OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString())
        failure.assertHasDescription('Build cancelled.')
        assertHasBuildFailedLogging()
    }

    private void targetIsGradle51OrLater() {
        targetVersion >= GradleVersion.version('5.1')
    }

    void configureWasCancelled(TestResultHandler resultHandler, String failureMessage) {
        resultHandler.assertFailedWith(BuildCancelledException)
        assert resultHandler.failure.message.startsWith(failureMessage)

        // Verify there is a cause that explains that the build was cancelled (and where).
        // Some versions do not included this
        if (targetDist.toolingApiHasCauseOnCancel) {
            verifyBuildCancelledExceptionMessage(resultHandler)
        }

        // Verify that there is some logging output that explains that the build was cancelled.
        // Some versions do not log anything on build cancellation
        if (targetDist.toolingApiLogsFailureOnCancel) {
            def failure = OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString())
            failure.assertHasDescription('Build cancelled.')
            assertHasConfigureFailedLogging()
        }
    }

    private static void verifyBuildCancelledExceptionMessage(TestResultHandler resultHandler) {
        // https://github.com/gradle/gradle-private/issues/1760
        assert resultHandler.failure.cause.message in ["Build cancelled.", "Daemon was stopped to handle build cancel request."]
    }

    void taskWasCancelled(TestResultHandler resultHandler, String taskPath) {
        resultHandler.assertFailedWith(BuildCancelledException)
        assert resultHandler.failure.message.startsWith("Could not execute build using")

        if (targetDist.toolingApiRetainsOriginalFailureOnCancel) {
            if (targetDist.toolingApiDoesNotAddCausesOnTaskCancel) {
                // Verify the cause exception gives some context about the cancellation
                // Some versions either do not included this, or provide a pointless wrapper, or include multiple 'build cancelled' failures
                assert resultHandler.failure.cause.message == "Execution failed for task '${taskPath}'." // wrapper exception, could probably suppress this
                assert resultHandler.failure.cause.cause.message == "Execution failed for task '${taskPath}'."
                assert cancelledMessageMatcher(taskPath).matches(resultHandler.failure.cause.cause.cause.message)
            }

            // Verify that there is some logging output that explains that the build was cancelled
            def failure = OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString())
            failure.assertHasDescription("Execution failed for task '${taskPath}'.")
            failure.assertThatCause(cancelledMessageMatcher(taskPath))
        } else {
            // Verify that there is some logging output that explains that the build was cancelled, for versions that do not include any context in the message
            def failure = OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString())
            failure.assertHasDescription("Build cancelled")
        }
        assertHasBuildFailedLogging()
    }

    private Matcher<String> cancelledMessageMatcher(String taskPath) {
        CoreMatchers.anyOf(CoreMatchers.startsWith("Build cancelled while executing task '${taskPath}'"), CoreMatchers.startsWith("Build cancelled during executing task '${taskPath}'"))
    }
}
