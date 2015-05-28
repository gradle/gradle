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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.fixtures.executer.GradleVersions
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnector
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.AutoCleanup

import java.util.concurrent.*

@Requires(TestPrecondition.JDK7_OR_LATER)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_RICH_PROGRESS_EVENTS)
@TargetGradleVersion(GradleVersions.SUPPORTS_CONTINUOUS)
class ContinuousBuildCancellationCrossVersionSpec extends ToolingApiSpecification {

    @AutoCleanup("shutdown")
    ExecutorService executorService = Executors.newSingleThreadExecutor()
    ByteArrayOutputStream stderr = new ByteArrayOutputStream(512)
    ByteArrayOutputStream stdout = new ByteArrayOutputStream(512)
    def cancellationTokenSource = GradleConnector.newCancellationTokenSource()

    @Rule
    CyclicBarrierHttpServer cyclicBarrierHttpServer = new CyclicBarrierHttpServer()

    def setupJavaProject() {
        buildFile.text = "apply plugin: 'java'"
    }

    def runAsyncContinuousBuild(String task = "classes") {
        executorService.submit({
            withConnection {
                newBuild().withArguments("--continuous").forTasks(task)
                    .withCancellationToken(cancellationTokenSource.token())
                    .setStandardOutput(stdout)
                    .setStandardError(stderr)
                    .run()
            }
        } as Callable)
    }

    // @Unroll // multiversion stuff is incompatible with unroll
    def "client can cancel while a continuous build is waiting for changes - after #delayms ms delay"(long delayms) {
        given:
        setupJavaProject()
        buildFile << """
gradle.buildFinished {
    new URL("${cyclicBarrierHttpServer.uri}").text
}
"""

        when:
        def buildFuture = runAsyncContinuousBuild()
        if (delayms == 0) {
            cyclicBarrierHttpServer.waitFor()
            cancellationTokenSource.cancel()
            cyclicBarrierHttpServer.release()
        } else {
            cyclicBarrierHttpServer.sync()
            sleep(delayms)
            cancellationTokenSource.cancel()
        }
        buildFuture.get(2000, TimeUnit.MILLISECONDS)

        then:
        noExceptionThrown()

        where:
        delayms << [0L, 1L, 1000L, 2000L]
    }

    def "client can cancel during execution of a continuous build - before task execution has started"() {
        given:
        setupJavaProject()
        buildFile << """
gradle.taskGraph.whenReady {
    new URL("${cyclicBarrierHttpServer.uri}").text
}
"""

        when:
        def buildFuture = runAsyncContinuousBuild()
        cyclicBarrierHttpServer.waitFor()
        cancellationTokenSource.cancel()
        cyclicBarrierHttpServer.release()
        buildFuture.get(2000, TimeUnit.MILLISECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof BuildCancelledException
    }

    def "client can cancel during execution of a continuous build - just before the last task execution has started"() {
        given:
        setupJavaProject()
        buildFile << """
gradle.taskGraph.beforeTask { Task task ->
    if(task.path == ':classes') {
        new URL("${cyclicBarrierHttpServer.uri}").text
    }
}
"""

        when:
        def buildFuture = runAsyncContinuousBuild()
        cyclicBarrierHttpServer.waitFor()
        cancellationTokenSource.cancel()
        cyclicBarrierHttpServer.release()
        buildFuture.get(2000, TimeUnit.MILLISECONDS)

        then:
        noExceptionThrown()
    }

    def "client can cancel during execution of a continuous build - before a task which isn't the last task"() {
        given:
        setupJavaProject()
        buildFile << """
gradle.taskGraph.beforeTask { Task task ->
    if(task.path == ':compileJava') {
        new URL("${cyclicBarrierHttpServer.uri}").text
    }
}
"""

        when:
        def buildFuture = runAsyncContinuousBuild()
        cyclicBarrierHttpServer.waitFor()
        cancellationTokenSource.cancel()
        cyclicBarrierHttpServer.release()
        buildFuture.get(2000, TimeUnit.MILLISECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof BuildCancelledException
    }

    def "logging does not include message to use ctrl-d to exit"() {
        given:
        setupJavaProject()
        buildFile << """
gradle.buildFinished {
    new URL("${cyclicBarrierHttpServer.uri}").text
}
"""

        when:
        def buildFuture = runAsyncContinuousBuild()
        cyclicBarrierHttpServer.sync()
        Thread.sleep(2000)
        cancellationTokenSource.cancel()
        buildFuture.get(2000, TimeUnit.MILLISECONDS)
        def stdoutContent = stdout.toString()

        then:
        !stdoutContent.contains("ctrl+d to exit")
        stdoutContent.contains("Waiting for changes to input files of tasks...")
    }
}
