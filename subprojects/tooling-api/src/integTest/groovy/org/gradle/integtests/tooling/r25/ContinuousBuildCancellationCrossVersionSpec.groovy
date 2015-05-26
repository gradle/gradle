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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.*
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.internal.consumer.DefaultCancellationTokenSource
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.AutoCleanup

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Requires(TestPrecondition.JDK7_OR_LATER)
@ToolingApiVersion("current")
@TargetGradleVersion("current")
class ContinuousBuildCancellationCrossVersionSpec extends ToolingApiSpecification {
    @AutoCleanup("shutdown")
    ScheduledExecutorService scheduledExecutorService =  Executors.newSingleThreadScheduledExecutor()
    ByteArrayOutputStream stderr = new ByteArrayOutputStream(512)
    ByteArrayOutputStream stdout = new ByteArrayOutputStream(512)

    def setupJavaProject() {
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
'''
        def javaSrcDir = projectDir.createDir('src/main/java')
        javaSrcDir
    }

    def runContinuousBuild(DefaultCancellationTokenSource cancellationTokenSource, ProgressListener progressListener, String task = "classes") {
        withConnection { ProjectConnection connection ->
            BuildLauncher launcher = connection.newBuild().withArguments("--continuous").forTasks(task)
            launcher.withCancellationToken(cancellationTokenSource.token())
            launcher.addProgressListener(progressListener, [OperationType.GENERIC, OperationType.TASK] as Set)
            launcher.setStandardOutput(stdout)
            launcher.setStandardError(stderr)
            launcher.run()
        }
    }

    def "client can cancel while a continuous build is waiting for changes - after #delayms ms delay"(long delayms) {
        given:
        setupJavaProject()
        when:
        DefaultCancellationTokenSource cancellationTokenSource = new DefaultCancellationTokenSource()
        Runnable cancelTask = new Runnable() {
            @Override
            void run() {
                cancellationTokenSource.cancel()
            }
        }
        ProgressListener progressListener = new ProgressListener() {
            @Override
            void statusChanged(ProgressEvent event) {
                if(event instanceof FinishEvent && event.descriptor.name == 'Running build') {
                    if(delayms > 0) {
                        scheduledExecutorService.schedule(cancelTask, delayms, TimeUnit.MILLISECONDS)
                    } else {
                        cancelTask.run()
                    }
                }
            }
        }
        runContinuousBuild(cancellationTokenSource, progressListener)
        then:
        noExceptionThrown()
        where:
        delayms << [0L, 50L, 1500L, 5000L]
    }

    def "client can cancel during execution of a continuous build - before task execution has started"() {
        given:
        setupJavaProject()
        when:
        DefaultCancellationTokenSource cancellationTokenSource = new DefaultCancellationTokenSource()
        ProgressListener progressListener = new ProgressListener() {
            @Override
            void statusChanged(ProgressEvent event) {
                if(event instanceof StartEvent && event.descriptor.name == 'Running build') {
                    cancellationTokenSource.cancel()
                }
            }
        }
        runContinuousBuild(cancellationTokenSource, progressListener)
        then:
        thrown BuildCancelledException
    }

    def "client can cancel during execution of a continuous build - just before the last task execution has started"() {
        given:
        setupJavaProject()
        when:
        DefaultCancellationTokenSource cancellationTokenSource = new DefaultCancellationTokenSource()
        ProgressListener progressListener = new ProgressListener() {
            @Override
            void statusChanged(ProgressEvent event) {
                if(event instanceof TaskStartEvent && event.descriptor.taskPath==":classes") {
                    cancellationTokenSource.cancel()
                }
            }
        }
        runContinuousBuild(cancellationTokenSource, progressListener)
        then:
        noExceptionThrown()
    }


    def "client can cancel during execution of a continuous build - before a task which isn't the last task"() {
        given:
        setupJavaProject()
        when:
        DefaultCancellationTokenSource cancellationTokenSource = new DefaultCancellationTokenSource()
        ProgressListener progressListener = new ProgressListener() {
            @Override
            void statusChanged(ProgressEvent event) {
                if(event instanceof TaskStartEvent && event.descriptor.taskPath==":compileJava") {
                    cancellationTokenSource.cancel()
                }
            }
        }
        runContinuousBuild(cancellationTokenSource, progressListener)
        then:
        thrown(BuildCancelledException)
    }

    def "logging does not include message to use ctrl-d to exit continuous mode"() {
        given:
        setupJavaProject()
        when:
        DefaultCancellationTokenSource cancellationTokenSource = new DefaultCancellationTokenSource()
        Runnable cancelTask = new Runnable() {
            @Override
            void run() {
                cancellationTokenSource.cancel()
            }
        }
        ProgressListener progressListener = new ProgressListener() {
            @Override
            void statusChanged(ProgressEvent event) {
                if(event instanceof FinishEvent && event.descriptor.name == 'Running build') {
                    scheduledExecutorService.schedule(cancelTask, 2000L, TimeUnit.MILLISECONDS)
                }
            }
        }
        runContinuousBuild(cancellationTokenSource, progressListener)
        def stdoutContent = stdout.toString()
        then:
        !stdoutContent.contains("ctrl+d to exit")
        stdoutContent.contains("Waiting for changes to input files of tasks...")
    }

}
