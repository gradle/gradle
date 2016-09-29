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

package org.gradle.integtests.tooling.r22

import org.gradle.integtests.tooling.fixture.*
import org.gradle.integtests.tooling.r18.BrokenAction
import org.gradle.integtests.tooling.r21.HangingBuildAction
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.junit.Rule

@ToolingApiVersion(">=2.2")
@TargetGradleVersion(">=2.1")
class CancellationCrossVersionSpec extends ToolingApiSpecification {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def setup() {
        settingsFile << '''
rootProject.name = 'cancelling'
'''
    }

    @TargetGradleVersion(">=2.2")
    def "can cancel build during settings phase"() {
        settingsFile << """
import org.gradle.initialization.BuildCancellationToken
import java.util.concurrent.CountDownLatch

def cancellationToken = gradle.services.get(BuildCancellationToken.class)
def latch = new CountDownLatch(1)

cancellationToken.addCallback {
    latch.countDown()
}

new URL("${server.uri}").text
latch.await()
"""
        buildFile << """
throw new RuntimeException("should not run")
"""

        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        def error = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks(':sub:broken')
                    .withCancellationToken(cancel.token())
                    .setStandardOutput(output)
                    .setStandardError(error)
            build.run(resultHandler)
            server.sync()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof BuildCancelledException
    }

    @TargetGradleVersion(">=2.2")
    def "can cancel build during configuration phase"() {
        file("gradle.properties") << "org.gradle.configureondemand=${configureOnDemand}"
        setupCancelInConfigurationBuild()

        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        def error = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks(':sub:broken')
                    .withCancellationToken(cancel.token())
                    .setStandardOutput(output)
                    .setStandardError(error)
            build.run(resultHandler)
            server.sync()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof BuildCancelledException

        where:
        configureOnDemand << [true, false]
    }

    @TargetGradleVersion(">=2.2")
    def "can cancel model creation during configuration phase"() {
        file("gradle.properties") << "org.gradle.configureondemand=${configureOnDemand}"
        setupCancelInConfigurationBuild()

        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        def error = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def model = connection.model(GradleProject)
            model.withCancellationToken(cancel.token())
                    .setStandardOutput(output)
                    .setStandardError(error)
            model.get(resultHandler)
            server.sync()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof BuildCancelledException

        where:
        configureOnDemand << [true, false]
    }

    @TargetGradleVersion(">=2.2")
    def "can cancel build action execution during configuration phase"() {
        file("gradle.properties") << "org.gradle.configureondemand=${configureOnDemand}"
        setupCancelInConfigurationBuild()

        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        def error = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def action = connection.action(new BrokenAction())
            action.withCancellationToken(cancel.token())
                    .setStandardOutput(output)
                    .setStandardError(error)
            action.run(resultHandler)
            server.sync()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof BuildCancelledException

        where:
        configureOnDemand << [true, false]
    }

    def "can cancel build and skip some tasks"() {
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
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof BuildCancelledException
    }

    def "does not fail when build completes within the cancellation timeout"() {
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
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        noExceptionThrown()
    }

    def "can cancel build through forced stop"() {
        // in-process call does not support forced stop
        toolingApi.requireDaemons()
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
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof BuildCancelledException
    }

    @TargetGradleVersion(">=2.2")
    def "can cancel model retrieval"() {
        settingsFile << '''
include 'sub'
rootProject.name = 'cancelling'
'''
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
        projectDir.file('sub/build.gradle') << """
throw new RuntimeException("should not run")
"""

        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        def error = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.model(GradleProject)
            build.withCancellationToken(cancel.token())
                    .setStandardOutput(output)
                    .setStandardError(error)
            build.get(resultHandler)
            server.sync()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof BuildCancelledException
    }

    def "can cancel action"() {
        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()

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

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.action(new HangingBuildAction())
            build.withCancellationToken(cancel.token())
                    .setStandardOutput(output)
            build.run(resultHandler)
            server.sync()
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.failure instanceof BuildCancelledException
    }

    def setupCancelInConfigurationBuild() {
        settingsFile << '''
include 'sub'
rootProject.name = 'cancelling'
'''
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

        projectDir.file('sub/build.gradle') << """
throw new RuntimeException("should not run")
"""
    }
}
