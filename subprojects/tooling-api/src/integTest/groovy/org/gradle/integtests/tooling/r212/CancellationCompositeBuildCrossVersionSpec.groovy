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

package org.gradle.integtests.tooling.r212
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.model.eclipse.EclipseProject
import org.junit.Rule

/**
 * Tests cancellation of model requests in a composite build.
 */
class CancellationCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def "can cancel model operation while a participant request is being processed"() {
        given:
        def cancelledFile = file("cancelled")
        def executedAfterCancellingFile = file("executed")
        def participantCancelledFile = file("participant_cancelled")
        def buildFileText = """
        import org.gradle.initialization.BuildCancellationToken
        import java.util.concurrent.CountDownLatch

        def cancellationToken = services.get(BuildCancellationToken.class)

        def cancelledFile = new File('${cancelledFile.absolutePath}')
        if(cancelledFile.exists()) {
           new File('${executedAfterCancellingFile.absolutePath}') << "executed \${project.name} token cancelled:\${cancellationToken.isCancellationRequested()}\\n"
           throw new RuntimeException("Build should not get executed since composite has been cancelled.")
        }

        def latch = new CountDownLatch(1)

        cancellationToken.addCallback {
            latch.countDown()
        }

        println "Connecting to server..."
        new URL('${server.uri}').text
        latch.await()
        new File('${participantCancelledFile.absolutePath}') << "participant \${project.name} cancelled\\n"
"""
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
        resultHandler.result instanceof BuildCancelledException
        // participant should be properly cancelled
        participantCancelledFile.exists()
        // no new builds should have been executed after cancelling
        !executedAfterCancellingFile.exists()
    }

    def "check that no participant requests are started at all when token is initially cancelled"() {
        given:
        def executedAfterCancellingFile = file("executed")
        def buildFileText = """
        new File("${executedAfterCancellingFile.absolutePath}").text = << "executed \${project.name}\\n"
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
        resultHandler.result instanceof BuildCancelledException
        !executedAfterCancellingFile.exists()
    }


    static class ResultCollector implements ResultHandler {
        def result

        @Override
        void onComplete(Object result) {
            this.result = result
        }

        @Override
        void onFailure(GradleConnectionException failure) {
            this.result = failure
        }
    }
}
