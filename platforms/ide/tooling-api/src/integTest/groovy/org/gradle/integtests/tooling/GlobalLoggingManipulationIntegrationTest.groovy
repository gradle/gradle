/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RedirectStdIn
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.BlockingResultHandler
import org.gradle.tooling.model.GradleProject
import org.junit.Rule

import java.util.logging.LogManager

import static java.util.logging.Level.OFF

@RedirectStdIn
@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "because toolingApi.requireIsolatedToolingApi()")
class GlobalLoggingManipulationIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer sync = new BlockingHttpServer()
    final ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)

    def setup() {
        toolingApi.requireIsolatedToolingApi()
        sync.start()
        settingsFile.touch()
    }

    def cleanup() {
        toolingApi.close()
    }

    def "tooling api restores standard streams at end of the build"() {
        given:
        def outInstance = System.out
        def errInstance = System.err
        def inInstance = System.in

        buildFile << """
            task hey
        """

        when:
        GradleProject model = toolingApi.withConnection { ProjectConnection connection -> connection.getModel(GradleProject.class) }

        then:
        model.tasks.find { it.name == 'hey' }
        System.out.is(outInstance)
        System.err.is(errInstance)
        System.in.is(inInstance)
    }

    def "tooling api does not replace standard streams while build is running in daemon"() {
        given:
        toolingApi.requireDaemons()
        def outInstance = System.out
        def errInstance = System.err
        def inInstance = System.in
        def handle = sync.expectAndBlock("waiting")

        buildFile << """
            ${sync.callFromBuild("waiting")}
            task hey
        """

        when:
        GradleProject model = toolingApi.withConnection { ProjectConnection connection ->
            def handler = new BlockingResultHandler<GradleProject>(GradleProject)
            def builder = connection.model(GradleProject)
            builder.standardOutput = outInstance
            builder.standardError = errInstance
            builder.get(handler)
            handle.waitForAllPendingCalls()
            assert System.out.is(outInstance)
            assert System.err.is(errInstance)
            assert System.in.is(inInstance)
            handle.releaseAll()
            handler.result
        }

        then:
        model.tasks.find { it.name == 'hey' }
        System.out.is(outInstance)
        System.err.is(errInstance)
        System.in.is(inInstance)
    }

    static class FailingInputStream extends InputStream implements GroovyInterceptable {

        int read() {
            throw new RuntimeException("Input stream should not be consumed");
        }

        def invokeMethod(String name, args) {
            throw new RuntimeException("Input stream should not be consumed");
        }
    }

    def "tooling api should never consume the std in"() {
        given:
        System.in = new FailingInputStream()
        buildFile << "task hey"

        when:
        toolingApi.withConnection { connection -> connection.newBuild().run() }

        then:
        noExceptionThrown()
    }

    def "tooling api restores java logging at end of build"() {
        //(SF) checking if the logger level was not overridden.
        //this gives some confidence that the LogManager was not reset
        given:
        LogManager.getLogManager().getLogger("").setLevel(OFF);
        buildFile << "task hey"

        when:
        assertJavaUtilLoggingNotModified()
        GradleProject model = toolingApi.withConnection { ProjectConnection connection -> connection.getModel(GradleProject.class) }

        then:
        model.tasks.find { it.name == 'hey' }
        assertJavaUtilLoggingNotModified()
    }

    def "tooling api does not configure java logging while build is running in daemon"() {
        //(SF) checking if the logger level was not overridden.
        //this gives some confidence that the LogManager was not reset
        given:
        toolingApi.requireDaemons()
        def handle = sync.expectAndBlock("waiting")

        LogManager.getLogManager().getLogger("").setLevel(OFF);
        buildFile << """
            ${sync.callFromBuild("waiting")}
            task hey
        """

        when:
        assertJavaUtilLoggingNotModified()
        GradleProject model = toolingApi.withConnection { ProjectConnection connection ->
            def handler = new BlockingResultHandler<GradleProject>(GradleProject)
            def builder = connection.model(GradleProject)
            builder.standardOutput = System.out
            builder.standardError = System.err
            builder.get(handler)
            handle.waitForAllPendingCalls()
            assertJavaUtilLoggingNotModified()
            handle.releaseAll()
            handler.result
        }

        then:
        model.tasks.find { it.name == 'hey' }
        assertJavaUtilLoggingNotModified()
    }

    void assertJavaUtilLoggingNotModified() {
        assert OFF == LogManager.getLogManager().getLogger("").level
    }
}
