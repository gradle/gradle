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

import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.tooling.BuildException
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.exceptions.BuildCancelledException

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ToolingApiVersion(">=2.1")
@TargetGradleVersion(">=1.0-milestone-8")
class CancellationCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        // in-process call does not support cancelling (yet)
        toolingApi.isEmbedded = false
        settingsFile << '''
rootProject.name = 'cancelling'
'''
    }

    @TargetGradleVersion(">=2.1")
    def "can cancel build and skip some tasks"() {
        def marker = file("marker.txt")

        buildFile << """
task hang << {
    println "__waiting__"
    def marker = file('${marker.toURI()}')
    long timeout = System.currentTimeMillis() + 10000
    while (!marker.file && System.currentTimeMillis() < timeout) { Thread.sleep(200) }
    if (!marker.file) { throw new RuntimeException("Timeout waiting for marker file") }
    Thread.sleep(1000)
    println "__finished__"
}

task notExecuted(dependsOn: hang) << {
    println "__should_not_run__"
}
"""
        def cancel = new CancellationTokenSource()
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
            ConcurrentTestUtil.poll(10) { assert output.toString().contains("waiting") }
            marker.text = 'go!'
            cancel.cancel()
            resultHandler.finished()
        }
        then:
        output.toString().contains("__waiting__")
        output.toString().contains("__finished__")
        !output.toString().contains("__should_not_run__")
        new OutputScrapingExecutionResult(output.toString(), error.toString()).assertTasksExecuted(':hang')

        resultHandler.failure instanceof BuildException
        resultHandler.failure.cause.cause.message.contains('Build cancelled.')
    }

    @TargetGradleVersion(">=2.1")
    def "can cancel build"() {
        def marker = file("marker.txt")

        buildFile << """
task hang << {
    println "__waiting__"
    def marker = file('${marker.toURI()}')
    long timeout = System.currentTimeMillis() + 10000
    while (!marker.file && System.currentTimeMillis() < timeout) { Thread.sleep(200) }
    if (!marker.file) { throw new RuntimeException("Timeout waiting for marker file") }
    Thread.sleep(1000)
    println "__finished__"
}
"""
        def cancel = new CancellationTokenSource()
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
            ConcurrentTestUtil.poll(10) { assert output.toString().contains("waiting") }
            marker.text = 'go!'
            cancel.cancel()
            resultHandler.finished()
        }
        then:
        output.toString().contains("__waiting__")
        output.toString().contains("__finished__")
        new OutputScrapingExecutionResult(output.toString(), error.toString()).assertTasksExecuted(':hang')

        resultHandler.failure instanceof BuildException
        resultHandler.failure.cause.cause.message.contains('Build cancelled.')
    }

    @TargetGradleVersion(">=2.1")
    def "can cancel build through forced stop"() {
        def marker = file("marker.txt")

        buildFile << """
task hang << {
    println "__waiting__"
    def marker = file('${marker.toURI()}')
    long timeout = System.currentTimeMillis() + 12000
    while (!marker.file && System.currentTimeMillis() < timeout) { Thread.sleep(200) }
    if (!marker.file) { throw new RuntimeException("Timeout waiting for marker file") }
    Thread.sleep(1000)
    println "__finished__"
}
"""
        def cancel = new CancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('hang')
                    .withCancellationToken(cancel.token())
                    .setStandardOutput(output)
            build.run(resultHandler)
            ConcurrentTestUtil.poll(10) { assert output.toString().contains("waiting") }
            cancel.cancel()
            marker.text = 'go!'
            resultHandler.finished()
        }
        then:
        output.toString().contains("__waiting__")
        !output.toString().contains("__finished__")
        // TODO until we implement proper cancelling this depends on timing
        // resultHandler.failure.cause.class.name == BuildCancelledException.name || resultHandler.failure.cause.class.name == DaemonDisappearedException.name
        resultHandler.failure instanceof GradleConnectionException
    }

    @TargetGradleVersion("<2.1 >=1.0-milestone-8")
    def "cancel with older provider issues warning only"() {
        def marker = file("warning.txt")
        buildFile << """
task t << {
    println "waiting"
    def marker = file('${marker.toURI()}')
    long timeout = System.currentTimeMillis() + 10000
    while (!marker.file && System.currentTimeMillis() < timeout) { Thread.sleep(200) }
    if (!marker.file) { throw new RuntimeException("Timeout waiting for marker file") }
    println "finished"
}
"""
        def cancel = new CancellationTokenSource()
        def resultHandler = new TestResultHandler(false)
        def output = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('t')
                .withCancellationToken(cancel.token())
                .setStandardOutput(output)
            build.run(resultHandler)
            ConcurrentTestUtil.poll(10) { assert output.toString().contains("waiting") }
            cancel.cancel()
            marker.text = 'go!'
            resultHandler.finished()
        }

        then:
        output.toString().contains("does not support cancellation")
        resultHandler.failure == null
        output.toString().contains("finished")
    }

    @TargetGradleVersion(">=2.1")
    def "early cancel stops the build before beginning"() {
        buildFile << """
task hang << {
    throw new GradleException("should not run")
}
"""
        def cancel = new CancellationTokenSource()
        def resultHandler = new TestResultHandler()

        when:
        cancel.cancel()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('hang')
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            resultHandler.finished()
        }
        then:
        resultHandler.failure instanceof BuildCancelledException
    }

    def "can cancel model retrieval"() {
        // TODO
    }

    @TargetGradleVersion(">=2.1")
    def "early cancel stops model retrieval before beginning"() {
        def cancel = new CancellationTokenSource()
        def resultHandler = new TestResultHandler()

        when:
        cancel.cancel()
        withConnection { ProjectConnection connection ->
            def build = connection.model(SomeModel)
            build.withCancellationToken(cancel.token())
            build.get(resultHandler)
            resultHandler.finished()
        }
        then:
        resultHandler.failure instanceof BuildCancelledException
    }

    @TargetGradleVersion(">=2.1")
    def "can cancel action"() {
        def marker = file("marker.txt")
        def cancel = new CancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.action(new HangingBuildAction(marker.toURI()))
            build.withCancellationToken(cancel.token())
                    .setStandardOutput(output)
            build.run(resultHandler)
            ConcurrentTestUtil.poll(10) { assert output.toString().contains("waiting") }
            cancel.cancel()
            marker.text = 'go!'
            resultHandler.finished()
        }
        resultHandler.failure.printStackTrace()

        then:
        output.toString().contains("waiting")
        // TODO add when print 'finished' is preceeded with call to BuildController and we're able to cancel it
        // !output.toString().contains("finished")
        // TODO until we implement proper cancelling this depends on timing
        resultHandler.failure.cause.class.name == BuildCancelledException.name || resultHandler.failure.cause.class.name.contains("DaemonDisappearedException")
        resultHandler.failure instanceof GradleConnectionException
    }

    @TargetGradleVersion(">=2.1")
    def "early cancel stops the action before beginning"() {
        def cancel = new CancellationTokenSource()
        def resultHandler = new TestResultHandler()

        when:
        cancel.cancel()
        withConnection { ProjectConnection connection ->
            def build = connection.action(new HangingBuildAction(null))
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            resultHandler.finished()
        }
        then:
        resultHandler.failure instanceof BuildCancelledException
    }

    class TestResultHandler implements ResultHandler<Object> {
        final latch = new CountDownLatch(1)
        final boolean expectFailure
        def failure

        TestResultHandler() {
            this(true)
        }

        TestResultHandler(boolean expectFailure) {
            this.expectFailure = expectFailure
        }

        void onComplete(Object result) {
            latch.countDown()
        }

        void onFailure(GradleConnectionException failure) {
            this.failure = failure
            latch.countDown()
        }

        def finished() {
            latch.await(10, TimeUnit.SECONDS)
            assert (failure != null) == expectFailure
        }
    }

    class TestOutputStream extends OutputStream {
        final buffer = new ByteArrayOutputStream()

        @Override
        void write(int b) throws IOException {
            synchronized (buffer) {
                buffer.write(b)
            }
        }

        @Override
        String toString() {
            synchronized (buffer) {
                return buffer.toString()
            }
        }
    }

    interface SomeModel extends Serializable {}
}
