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

    def setupCancelInConfigurationBuild() {
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

    void buildWasCancelled(TestResultHandler resultHandler, String failureMessage = 'Could not execute build using Gradle') {
        resultHandler.assertFailedWith(BuildCancelledException)
        assert resultHandler.failure.message.startsWith(failureMessage)
        assert resultHandler.failure.cause.message == "Build cancelled."
        def failure = OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString())
        failure.assertHasDescription('Build cancelled.')
        assertHasBuildFailedLogging()
    }

    void configureWasCancelled(TestResultHandler resultHandler, String failureMessage) {
        resultHandler.assertFailedWith(BuildCancelledException)
        assert resultHandler.failure.message.startsWith(failureMessage)
        if (targetDist.toolingApiHasCauseOnCancel) {
            assert resultHandler.failure.cause.message == "Build cancelled."
        }
        if (targetDist.toolingApiLogsFailureOnCancel) {
            def failure = OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString())
            failure.assertHasDescription('Build cancelled.')
            assertHasConfigureFailedLogging()
        }
    }

    void taskWasCancelled(TestResultHandler resultHandler, String taskPath) {
        resultHandler.assertFailedWith(BuildCancelledException)
        assert resultHandler.failure.message.startsWith("Could not execute build using Gradle")
        if (targetDist.toolingApiRetainsOriginalFailureOnCancel) {
            assert resultHandler.failure.cause.message == "Execution failed for task '${taskPath}'." // wrapper exception, could probably suppress this
            assert resultHandler.failure.cause.cause.message == "Execution failed for task '${taskPath}'."
            assert resultHandler.failure.cause.cause.cause.message == "Build cancelled during executing task '${taskPath}'"
            def failure = OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString())
            failure.assertHasDescription("Execution failed for task '${taskPath}'.")
            failure.assertHasCause("Build cancelled during executing task '${taskPath}'")
        } else {
            def failure = OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString())
            failure.assertHasDescription("Build cancelled")
        }
        assertHasBuildFailedLogging()
    }
}
