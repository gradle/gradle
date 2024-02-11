/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r86

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.StreamedValueListener
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import org.junit.Rule

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

@ToolingApiVersion(">=8.6")
@TargetGradleVersion(">=8.6")
class StreamingBuildActionCrossVersionTest extends ToolingApiSpecification {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        file("settings.gradle") << 'rootProject.name="hello-world"'
    }

    def "build action can stream values and client receives them in the same order"() {
        when:
        def models = new CopyOnWriteArrayList<Object>()
        def finished = new CountDownLatch(1)
        def listener = { model -> models.add(model) } as StreamedValueListener
        def handler = { model ->
            models.add(model)
            finished.countDown()
        } as ResultHandler

        withConnection {
            def builder = it.action(new ModelStreamingBuildAction())
            collectOutputs(builder)
            builder.setStreamedValueListener(listener)
            builder.run(handler)
            finished.await()
        }

        then:
        models.size() == 3

        and:
        GradleProject gradleProject = models.get(0)
        gradleProject.name == "hello-world"

        and:
        EclipseProject eclipseModel = models.get(1)
        eclipseModel.gradleProject.name == "hello-world"

        and:
        CustomModel result = models.get(2)
        result.value == 42
    }

    def "phased build action can stream values and the client receives them in the same order"() {
        when:
        def models = new CopyOnWriteArrayList<Object>()
        def listener = { model -> models.add(model) } as StreamedValueListener
        def handler = { model -> models.add(model) } as IntermediateResultHandler

        withConnection {
            def builder = it.action()
                .projectsLoaded(new CustomModelStreamingBuildAction(GradleProject, 1), handler)
                .buildFinished(new CustomModelStreamingBuildAction(EclipseProject, 2), handler)
                .build()
            collectOutputs(builder)
            builder.setStreamedValueListener(listener)
            builder.run()
        }

        then:
        models.size() == 4

        and:
        CustomModel model1 = models.get(0)
        model1.value == 1

        and:
        GradleProject gradleProject = models.get(1)
        gradleProject.name == "hello-world"

        and:
        CustomModel model2 = models.get(2)
        model2.value == 2

        and:
        EclipseProject eclipseModel = models.get(3)
        eclipseModel.gradleProject.name == "hello-world"
    }

    def "client application receives streamed value before build action completes"() {
        when:
        server.start()
        def request = server.expectAndBlock("action")
        def models = new CopyOnWriteArrayList<Object>()
        def modelReceived = new CountDownLatch(1)
        def finished = new CountDownLatch(1)
        def listener = { model ->
            models.add(model)
            modelReceived.countDown()
        } as StreamedValueListener
        def handler = { model ->
            models.add(model)
            finished.countDown()
        } as ResultHandler

        withConnection {
            def builder = it.action(new BlockingModelSendingBuildAction(server.uri("action")))
            collectOutputs(builder)
            builder.setStreamedValueListener(listener)
            builder.run(handler)

            modelReceived.await()
            request.waitForAllPendingCalls()
            request.releaseAll()
            finished.await()
        }

        then:
        models.size() == 2
        models[0] instanceof GradleProject
        models[1] instanceof CustomModel
    }

    def "listener is isolated when it fails with an exception"() {
        when:
        def listener = { throw new RuntimeException("broken") } as StreamedValueListener

        withConnection {
            def builder = it.action(new ModelStreamingBuildAction())
            collectOutputs(builder)
            builder.setStreamedValueListener(listener)
            builder.run()
        }

        then:

        def e = thrown(GradleConnectionException)
        e.cause.message == "broken"
        // Report that the build was successful, as the failure was on the client side
        assertHasConfigureSuccessfulLogging()
    }

    def "build fails when build action streams value when no listener is registered"() {
        when:
        withConnection {
            def builder = it.action(new ModelStreamingBuildAction())
            collectOutputs(builder)
            builder.run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.cause instanceof IllegalStateException
        e.cause.message == "No streaming model listener registered."
        // Report that the build was successful, as the failure was on the client side
        assertHasConfigureSuccessfulLogging()
    }

    @TargetGradleVersion(">=3.0 <8.6")
    def "streaming fails when build action is running in a Gradle version that does not support streaming"() {
        when:
        def listener = { } as StreamedValueListener

        withConnection {
            def builder = it.action(new ModelStreamingBuildAction())
            collectOutputs(builder)
            builder.setStreamedValueListener(listener)
            builder.run()
        }

        then:
        def e = thrown(BuildActionFailureException)
        e.cause instanceof UnsupportedVersionException
        e.cause.message == "Gradle version $targetVersion.version does not support streaming values to the client."
    }
}
