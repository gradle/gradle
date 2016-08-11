/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.connection.ModelResults
import org.gradle.tooling.model.eclipse.EclipseProject
import org.junit.Rule
/**
 * Tests cancellation of model requests in a composite build.
 */
@TargetGradleVersion(">=2.1")
class CancellationCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def cancellationHookText(File cancelledFile, File executedAfterCancellingFile) {
        """
        import org.gradle.initialization.BuildCancellationToken
        import java.util.concurrent.CountDownLatch

        if (!project.hasProperty('waitForCancellation')) { return } // Ignore this stuff when we're creating the composite context

        def cancellationToken = services.get(BuildCancellationToken.class)

        def cancelledFile = file('${cancelledFile.toURI()}')
        if (cancelledFile.exists()) {
           file('${executedAfterCancellingFile.toURI()}') << "executed \${project.name} token cancelled:\${cancellationToken.isCancellationRequested()}\\n"
           throw new RuntimeException("Build should not get executed since composite has been cancelled.")
        }

        def latch = new CountDownLatch(1)

        cancellationToken.addCallback {
            latch.countDown()
        }
        """
    }

    def cancellationBlockingText(File participantCancelledFile) {
        """
        println "Connecting to server..."
        new URL('${server.uri}').text
        latch.await()
        file('${participantCancelledFile.toURI()}') << "participant \${project.name} cancelled\\n"
        """
    }

    def "can cancel model operation while a participant request is being processed"() {
        given:
        def cancelledFile = file("cancelled")
        def executedAfterCancellingFile = file("executed")
        def participantCancelledFile = file("participant_cancelled")
        def buildFileText = cancellationHookText(cancelledFile, executedAfterCancellingFile) + cancellationBlockingText(participantCancelledFile)
        def build1 = populate("build-1") {
            buildFile << buildFileText
        }
        def build2 = populate("build-2") {
            buildFile << buildFileText
        }
        def build3 = populate("build-3") {
            buildFile << buildFileText
        }
        when:
        def cancellationToken = GradleConnector.newCancellationTokenSource()
        def resultHandler = new ResultCollector()
        withCompositeConnection([build1, build2, build3]) { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.withCancellationToken(cancellationToken.token())
            modelBuilder.withArguments("-PwaitForCancellation")
            // async ask for results
            modelBuilder.get(resultHandler)
            // wait for model requests to start
            server.sync()
            // make sure no new builds get executed
            cancelledFile.text = "cancelled"
            // cancel operation
            cancellationToken.cancel()
        }

        then:
        // overall operation "succeeded"
        resultHandler.failure == null
        resultHandler.result.size() == 3
        // each individual request failed
        resultHandler.result.each { result ->
            assertFailureHasCause(result.failure, BuildCancelledException)
        }
        // participant should be properly cancelled
        participantCancelledFile.exists()
        // no new builds should have been executed after cancelling
        !executedAfterCancellingFile.exists()
    }

    def "check that no participant model requests are started at all when token is initially cancelled"() {
        given:
        def executedAfterCancellingFile = file("executed")
        def buildFileText = """
        file("${executedAfterCancellingFile.toURI()}").text = << "executed \${project.name}\\n"
        throw new RuntimeException("Build should not get executed")
"""
        def build1 = populate("build-1") {
            buildFile << buildFileText
        }
        def build2 = populate("build-2") {
            buildFile << buildFileText
        }
        when:
        def cancellationToken = GradleConnector.newCancellationTokenSource()
        def resultHandler = new ResultCollector()
        cancellationToken.cancel()
        withCompositeConnection([build1, build2]) { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.withCancellationToken(cancellationToken.token())
            modelBuilder.get(resultHandler)
        }

        then:
        resultHandler.failure instanceof BuildCancelledException
        resultHandler.result == null
        !executedAfterCancellingFile.exists()
    }

    def "check that no participant tasks are started at all when token is initially cancelled"() {
        given:
        def executedAfterCancellingFile = file("executed")
        def buildFileText = """
        file("${executedAfterCancellingFile.toURI()}").text = << "executed \${project.name}\\n"
        throw new RuntimeException("Build should not get executed")
"""
        def build1 = populate("build-1") {
            buildFile << buildFileText
            buildFile << "task run {}"
        }
        def build2 = populate("build-2") {
            buildFile << buildFileText
        }
        when:
        def cancellationToken = GradleConnector.newCancellationTokenSource()
        def resultHandler = new ResultCollector()
        cancellationToken.cancel()
        withCompositeConnection([build1, build2]) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(build1, "run")
            buildLauncher.withCancellationToken(cancellationToken.token())
            buildLauncher.run(resultHandler)
        }

        then:
        resultHandler.failure instanceof BuildCancelledException
        resultHandler.result == null
        !executedAfterCancellingFile.exists()
    }

    static class ResultCollector implements ResultHandler {
        ModelResults result
        GradleConnectionException failure

        @Override
        void onComplete(Object result) {
            this.result = result
        }

        @Override
        void onFailure(GradleConnectionException failure) {
            this.failure = failure
        }
    }
}
