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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.executer.*
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.AutoCleanup
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

@Timeout(60)
@Requires(TestPrecondition.JDK7_OR_LATER)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_RICH_PROGRESS_EVENTS)
@TargetGradleVersion(GradleVersions.SUPPORTS_CONTINUOUS)
class ContinuousBuildCrossVersionSpec extends ToolingApiSpecification {

    @AutoCleanup("shutdown")
    ExecutorService executorService = Executors.newCachedThreadPool()
    ByteArrayOutputStream stderr
    ByteArrayOutputStream stdout
    Runnable cancelTask
    Future<?> buildExecutionFuture
    ExecutionResult result
    ExecutionFailure failure
    ProgressListener progressListener
    int buildTimeout = 10

    def cancellationTokenSource = GradleConnector.newCancellationTokenSource()

    TestFile setupJavaProject() {
        buildFile.text = "apply plugin: 'java'"
        projectDir.createDir('src/main/java')
    }

    def cleanup() {
        cancelTask?.run()
    }

    def runContinuousBuild(String... tasks) {
        stderr = new ByteArrayOutputStream(512)
        stdout = new ByteArrayOutputStream(512)
        withConnection { ProjectConnection connection ->
            BuildLauncher launcher = connection.newBuild().withArguments("--continuous").forTasks(tasks)
            launcher.withCancellationToken(cancellationTokenSource.token())
            if (progressListener) {
                launcher.addProgressListener(progressListener, [OperationType.GENERIC, OperationType.TASK] as Set)
            }
            launcher.setStandardOutput(stdout)
            launcher.setStandardError(stderr)
            launcher.run()
        }
    }

    void runBuild(String... tasks) {
        cancelTask = cancellationTokenSource.&cancel
        buildExecutionFuture = executorService.submit {
            runContinuousBuild(tasks)
        }
    }

    ExecutionResult succeeds(String... tasks) {
        executeBuild(tasks)
        if (result instanceof ExecutionFailure) {
            throw new UnexpectedBuildFailure("build was expected to succeed but failed")
        }
        failure = null
        result
    }

    ExecutionFailure fails(String... tasks) {
        executeBuild(tasks)
        if (!(result instanceof ExecutionFailure)) {
            throw new UnexpectedBuildFailure("build was expected to fail but succeeded")
        }
        failure = result as ExecutionFailure
        failure
    }

    private void executeBuild(String... tasks) {
        if (tasks) {
            runBuild(tasks)
        } else if (buildExecutionFuture.isDone()) {
            throw new UnexpectedBuildFailure("Tooling API build connection has exited")
        }
        if (buildExecutionFuture == null) {
            throw new UnexpectedBuildFailure("Tooling API build connection never started")
        }
        waitForBuild()
    }

    private void waitForBuild() {
        new PollingConditions(initialDelay: 0.5).within(buildTimeout) {
            assert stdout.toString().contains("Waiting for changes to input files of tasks...")
        }

        def out = stdout.toString()
        stdout.reset()
        def err = stderr.toString()
        stderr.reset()

        result = out.contains("BUILD SUCCESSFUL") ? new OutputScrapingExecutionResult(out, err) : new OutputScrapingExecutionFailure(out, err)
    }

    protected List<String> getExecutedTasks() {
        assertHasResult()
        result.executedTasks
    }

    private assertHasResult() {
        assert result != null: "result is null, you haven't run succeeds()"
    }

    protected Set<String> getSkippedTasks() {
        assertHasResult()
        result.skippedTasks
    }

    protected List<String> getNonSkippedTasks() {
        executedTasks - skippedTasks
    }

    protected void executedAndNotSkipped(String... tasks) {
        tasks.each {
            assert it in executedTasks
            assert !skippedTasks.contains(it)
        }
    }

    def "client executes continuous build that succeeds, then responds to input changes and succeeds"() {
        given:
        def javaSrcDir = setupJavaProject()
        def javaSrcFile = javaSrcDir.file("Thing.java")
        javaSrcFile << 'public class Thing {}'

        when:
        succeeds('build')

        then:
        executedAndNotSkipped ":compileJava", ":build"

        when:
        javaSrcFile.text = 'public class Thing { public static final int FOO=1; }'

        then:
        succeeds()
        executedAndNotSkipped ":compileJava", ":build"
    }

    def "client executes continuous build that succeeds, then responds to input changes and fails, then … and succeeds"() {
        given:
        def javaSrcDir = setupJavaProject()
        def javaSrcFile = javaSrcDir.file("Thing.java")
        javaSrcFile << 'public class Thing {}'

        when:
        succeeds('build')

        then:
        executedAndNotSkipped ":compileJava", ":build"

        when:
        javaSrcFile.text = 'public class Thing { *******'

        then:
        fails()

        when:
        javaSrcFile.text = 'public class Thing {} '

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }

    def "client executes continuous build that fails, then responds to input changes and succeeds"() {
        given:
        def javaSrcDir = setupJavaProject()
        def javaSrcFile = javaSrcDir.file("Thing.java")
        javaSrcFile << 'public class Thing {'

        when:
        fails('build')

        then:
        noExceptionThrown()

        when:
        javaSrcFile.text = 'public class Thing {} '

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }

    def "client can receive appropriate logging and progress events for subsequent builds"() {
        given:
        def javaSrcDir = setupJavaProject()
        def javaSrcFile = javaSrcDir.file("Thing.java")
        javaSrcFile << 'public class Thing {}'
        AtomicInteger buildCounter = new AtomicInteger(0)
        AtomicInteger eventCounter = new AtomicInteger(0)
        int lastEventCounter
        progressListener = {
            eventCounter.incrementAndGet()
            if (it instanceof FinishEvent && it.descriptor.name == 'Running build') {
                buildCounter.incrementAndGet()
            }
        }

        when:
        succeeds('build')

        then:
        eventCounter.get() > 0
        buildCounter.get() == 1

        when:
        lastEventCounter = eventCounter.get()
        javaSrcFile.text = 'public class Thing { public static final int FOO=1; }'

        then:
        succeeds()
        eventCounter.get() > lastEventCounter
        buildCounter.get() == 2

        when:
        lastEventCounter = eventCounter.get()
        javaSrcFile.text = 'public class Thing {}'

        then:
        succeeds()
        eventCounter.get() > lastEventCounter
        buildCounter.get() == 3
    }

    @NotYetImplemented
    def "client can request continuous mode when building a model, but request is effectively ignored"() {
        given:
        stderr = new ByteArrayOutputStream(512)
        stdout = new ByteArrayOutputStream(512)

        when:
        BuildEnvironment buildEnvironment = withConnection { ProjectConnection connection ->
            connection.model(BuildEnvironment.class)
                .withArguments("--continuous")
                .setStandardOutput(stdout)
                .setStandardError(stderr)
                .get()
        }

        then:
        !stdout.toString().toLowerCase().contains("continuous")
        buildEnvironment.gradle.gradleVersion != null

        when:
        stdout.reset()
        stderr.reset()
        GradleProject gradleProject = withConnection { ProjectConnection connection ->
            connection.model(GradleProject.class)
                .withArguments("--continuous")
                .setStandardOutput(stdout)
                .setStandardError(stderr)
                .get()
        }

        then:
        !stdout.toString().toLowerCase().contains("continuous")
        gradleProject.buildDirectory != null
    }
}
