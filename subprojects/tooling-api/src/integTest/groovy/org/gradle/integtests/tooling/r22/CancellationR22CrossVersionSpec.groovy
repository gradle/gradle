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

import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r21.HangingBuildAction
import org.gradle.integtests.tooling.r21.TestResultHandler
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

@ToolingApiVersion(">=2.2")
@TargetGradleVersion(">=1.0-milestone-8")
class CancellationR22CrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        // in-process call does not support cancelling (yet)
        toolingApi.isEmbedded = false
        settingsFile << '''
rootProject.name = 'cancelling'
'''
    }

    @TargetGradleVersion(">=2.2")
    def "can cancel build during configuration phase"() {
        def marker = file("marker.txt")
        settingsFile << '''
include 'sub'
rootProject.name = 'cancelling'
'''
        buildFile << """
import org.gradle.initialization.BuildCancellationToken;
import java.util.concurrent.CountDownLatch;

ext {
  latch = new CountDownLatch(1);
}

println "__waiting__ (configuring root project)"
def marker = file('${marker.toURI()}')
long timeout = System.currentTimeMillis() + 10000
while (!marker.file && System.currentTimeMillis() < timeout) { Thread.sleep(200) }
if (!marker.file) { throw new RuntimeException("Timeout waiting for marker file") }

task first(type: CancellingTask) {

    cancellationToken.addCallback(new Runnable() {
        public void run() {
            project.rootProject.latch.countDown();
        }
    });
    // this will block root project configuration until cancel request is delivered
    project.rootProject.latch.await();

    doFirst {
        println "__should_not_run__"
    }
}

class CancellingTask extends DefaultTask {
    public final BuildCancellationToken cancellationToken;

    public CancellingTask() {
        this.cancellationToken = getServices().get(BuildCancellationToken.class);
    }

    @TaskAction
    def noop() {
    }
}
"""
        projectDir.file('sub/build.gradle') << """
println '__should_not_run__ (configuring :sub)'
task second << {
    println "__should_not_run__"
}
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
            ConcurrentTestUtil.poll(10) { assert output.toString().contains("waiting") }
            marker.text = 'go!'
            cancel.cancel()
            resultHandler.finished(20)
        }

        then:
        output.toString().contains("__waiting__")
        !output.toString().contains("__should_not_run__")
        new OutputScrapingExecutionResult(output.toString(), error.toString()).assertTasksExecuted()

        resultHandler.failure instanceof BuildCancelledException
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

        resultHandler.failure instanceof BuildCancelledException
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
            ConcurrentTestUtil.poll(10) { assert output.toString().contains("waiting") }
            marker.text = 'go!'
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        output.toString().contains("__waiting__")
        output.toString().contains("__finished__")
        new OutputScrapingExecutionResult(output.toString(), error.toString()).assertTasksExecuted(':hang')

        resultHandler.failure instanceof BuildCancelledException
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
            ConcurrentTestUtil.poll(10) { assert output.toString().contains("waiting") }
            cancel.cancel()
            resultHandler.finished(20)
            marker.text = 'go!'
        }

        then:
        output.toString().contains("__waiting__")
        !output.toString().contains("__finished__")
        resultHandler.failure instanceof BuildCancelledException
    }

    @TargetGradleVersion(">=2.1")
    def "early cancel stops the build before beginning"() {
        buildFile << """
task hang << {
    throw new GradleException("should not run")
}
"""
        def cancel = GradleConnector.newCancellationTokenSource()
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
        def cancel = GradleConnector.newCancellationTokenSource()
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

    @TargetGradleVersion(">=2.2")
    def "can cancel action"() {
        def marker = file("marker.txt")
        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.action(new CancelledInControllerBuildAction(marker.toURI()))
            build.withCancellationToken(cancel.token())
                    .setStandardOutput(output)
            build.run(resultHandler)
            ConcurrentTestUtil.poll(10) { assert output.toString().contains("waiting") }
            marker.text = 'go!'
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        output.toString().contains("waiting")
        !output.toString().contains("finished")
        resultHandler.failure instanceof BuildCancelledException
        resultHandler.failure.cause.message == 'Could not build \'org.gradle.tooling.model.gradle.GradleBuild\' model. Build cancelled.'
    }

    @TargetGradleVersion(">=2.1")
    def "early cancel stops the action before beginning"() {
        def cancel = GradleConnector.newCancellationTokenSource()
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
