/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r21

import org.gradle.integtests.tooling.fixture.*
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.junit.Rule

@ToolingApiVersion("=2.1")
@TargetGradleVersion(">=2.1")
class R21CancellationCrossVersionSpec extends ToolingApiSpecification {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def setup() {
        settingsFile << '''
rootProject.name = 'cancelling'
'''
    }

    @TargetGradleVersion(">=2.2")
    def "can cancel build during configuration phase"() {
        toolingApi.requireIsolatedDaemons()
        def daemons = toolingApi.daemons

        settingsFile << '''
include 'sub'
rootProject.name = 'cancelling'
'''
        buildFile << """
import org.gradle.initialization.BuildCancellationToken
import java.util.concurrent.CountDownLatch

def cancellationToken = services.get(BuildCancellationToken.class)
def latch = new CountDownLatch(1)

cancellationToken.addCallback{
    latch.countDown()
}

new URL("${server.uri}").text
latch.await()
"""
        projectDir.file('sub/build.gradle') << """
throw new RuntimeException("should not run")
"""

        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        def error = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks(':first', ':sub:second')
                    .withCancellationToken(cancel.token())
                    .setStandardOutput(output)
                    .setStandardError(error)
            build.run(resultHandler)
            server.sync()
            daemons.daemon.assertBusy()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof GradleConnectionException
        resultHandler.failure.cause.message.contains('Build cancelled.')

        and:
        daemons.daemon.assertIdle()
    }

    def "can cancel build and skip some tasks"() {
        toolingApi.requireIsolatedDaemons()
        def daemons = toolingApi.daemons

        buildFile << """
import org.gradle.initialization.BuildCancellationToken
import java.util.concurrent.CountDownLatch

task hang {
    doLast {
        def cancellationToken = services.get(BuildCancellationToken.class)
        def latch = new CountDownLatch(1)

        cancellationToken.addCallback {
            latch.countDown()
        }

        new URL("${server.uri}").text
        latch.await()
    }
}

task notExecuted(dependsOn: hang) {
    doLast {
        throw new RuntimeException("should not run")
    }
}
"""
        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        def error = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('notExecuted')
                    .withCancellationToken(cancel.token())
                    .setStandardOutput(output)
                    .setStandardError(error)
            build.run(resultHandler)
            server.sync()
            daemons.daemon.assertBusy()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof GradleConnectionException
        resultHandler.failure.cause.message.contains('Build cancelled.')

        and:
        daemons.daemon.assertIdle()
    }

    def "does not fail when build completes within the cancellation timeout"() {
        toolingApi.requireIsolatedDaemons()
        def daemons = toolingApi.daemons

        buildFile << """
import org.gradle.initialization.BuildCancellationToken
import java.util.concurrent.CountDownLatch

task hang {
    doLast {
        def cancellationToken = services.get(BuildCancellationToken.class)
        def latch = new CountDownLatch(1)

        cancellationToken.addCallback {
            latch.countDown()
        }

        new URL("${server.uri}").text
        latch.await()
    }
}
"""
        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        def error = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('hang')
                    .withCancellationToken(cancel.token())
                    .setStandardOutput(output)
                    .setStandardError(error)
            build.run(resultHandler)
            server.sync()
            daemons.daemon.assertBusy()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        noExceptionThrown()

        and:
        daemons.daemon.assertIdle()
    }

    def "can cancel build through forced stop"() {
        toolingApi.requireIsolatedDaemons()
        def daemons = toolingApi.daemons

        buildFile << """
task hang {
    doLast {
        new URL("${server.uri}").text
    }
}
"""
        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('hang')
                    .withCancellationToken(cancel.token())
                    .setStandardOutput(output)
            build.run(resultHandler)
            server.waitFor()
            daemons.daemon.assertBusy()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof GradleConnectionException
        resultHandler.failure.cause.class.name == 'org.gradle.api.BuildCancelledException'
        resultHandler.failure.cause.message == 'Build cancelled.'

        and:
        daemons.daemon.stops()
    }

    def "can cancel action"() {
        toolingApi.requireIsolatedDaemons()
        def daemons = toolingApi.daemons

        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()

        buildFile << """
import org.gradle.initialization.BuildCancellationToken
import java.util.concurrent.CountDownLatch

def cancellationToken = services.get(BuildCancellationToken.class)
def latch = new CountDownLatch(1)

cancellationToken.addCallback {
    latch.countDown()
}

new URL("${server.uri}").text
latch.await()
"""

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.action(new HangingBuildAction())
            build.withCancellationToken(cancel.token())
                    .setStandardOutput(output)
            build.run(resultHandler)
            server.sync()
            daemons.daemon.assertBusy()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof GradleConnectionException

        and:
        daemons.daemon.assertIdle()
    }
}
