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
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.AutoCleanup

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Requires(TestPrecondition.JDK7_OR_LATER)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_RICH_PROGRESS_EVENTS)
@TargetGradleVersion(GradleVersions.SUPPORTS_CONTINUOUS)
class ContinuousBuildCancellationCrossVersionSpec extends ToolingApiSpecification {

    @AutoCleanup("shutdown")
    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    ByteArrayOutputStream stderr = new ByteArrayOutputStream(512)
    ByteArrayOutputStream stdout = new ByteArrayOutputStream(512)
    def cancellationTokenSource = GradleConnector.newCancellationTokenSource()

    def setupJavaProject() {
        buildFile.text = "apply plugin: 'java'"
    }

    def runContinuousBuild(ProgressListener progressListener, String task = "classes") {
        withConnection {
            newBuild().withArguments("--continuous").forTasks(task)
                .withCancellationToken(cancellationTokenSource.token())
                .addProgressListener(progressListener, [OperationType.GENERIC, OperationType.TASK] as Set)
                .setStandardOutput(stdout)
                .setStandardError(stderr)
                .run()
        }
    }

    // @Unroll // multiversion stuff is incompatible with unroll
    def "client can cancel while a continuous build is waiting for changes - after #delayms ms delay"(long delayms) {
        given:
        setupJavaProject()

        when:
        runContinuousBuild {
            if (it instanceof FinishEvent && it.descriptor.name == 'Running build') {
                delayms > 0 ?
                    scheduledExecutorService.schedule(cancellationTokenSource.&cancel, delayms, TimeUnit.MILLISECONDS) :
                    cancellationTokenSource.cancel()
            }
        }

        then:
        noExceptionThrown()

        where:
        delayms << [0L, 50L, 1500L, 5000L]
    }

    def "client can cancel during execution of a continuous build - before task execution has started"() {
        given:
        setupJavaProject()

        when:
        runContinuousBuild {
            if (it instanceof StartEvent && it.descriptor.name == 'Running build') {
                cancellationTokenSource.cancel()
            }
        }

        then:
        thrown BuildCancelledException
    }

    def "client can cancel during execution of a continuous build - just before the last task execution has started"() {
        given:
        setupJavaProject()

        when:
        runContinuousBuild {
            if (it instanceof TaskStartEvent && it.descriptor.taskPath == ":classes") {
                cancellationTokenSource.cancel()
            }
        }

        then:
        noExceptionThrown()
    }


    def "client can cancel during execution of a continuous build - before a task which isn't the last task"() {
        given:
        setupJavaProject()

        when:
        runContinuousBuild {
            if (it instanceof TaskStartEvent && it.descriptor.taskPath == ":compileJava") {
                cancellationTokenSource.cancel()
            }
        }

        then:
        thrown(BuildCancelledException)
    }

    def "logging does not include message to use ctrl-d to exit continuous mode"() {
        given:
        setupJavaProject()

        when:
        runContinuousBuild {
            if (it instanceof FinishEvent && it.descriptor.name == 'Running build') {
                scheduledExecutorService.schedule(cancellationTokenSource.&cancel, 2000L, TimeUnit.MILLISECONDS)
            }
        }
        def stdoutContent = stdout.toString()

        then:
        !stdoutContent.contains("ctrl+d to exit")
        stdoutContent.contains("Waiting for changes to input files of tasks...")
    }

}
