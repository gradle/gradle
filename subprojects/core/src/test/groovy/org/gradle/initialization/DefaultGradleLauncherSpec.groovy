/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.initialization

import org.gradle.BuildListener
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.configuration.ProjectsPreparer
import org.gradle.execution.BuildWorkExecutor
import org.gradle.execution.MultipleBuildFailures
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.initialization.exception.ExceptionAnalyser
import org.gradle.initialization.internal.InternalBuildFinishedListener
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.scopes.BuildScopeServices
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

import java.util.function.Consumer

import static org.gradle.util.Path.path

class DefaultGradleLauncherSpec extends Specification {
    def settingsPreparerMock = Mock(SettingsPreparer)
    def taskExecutionPreparerMock = Mock(TaskExecutionPreparer)
    def taskGraphMock = Mock(TaskExecutionGraphInternal)
    def buildConfigurerMock = Mock(ProjectsPreparer)
    def buildBroadcaster = Mock(BuildListener)
    def buildExecuter = Mock(BuildWorkExecutor)

    def settingsMock = Mock(SettingsInternal.class)
    def gradleMock = Mock(GradleInternal.class)

    def exceptionAnalyserMock = Mock(ExceptionAnalyser)
    def buildCompletionListener = Mock(BuildCompletionListener.class)
    def buildFinishedListener = Mock(InternalBuildFinishedListener.class)
    def buildServices = Mock(BuildScopeServices.class)
    def otherService = Mock(Stoppable)
    def configurationCache = Mock(ConfigurationCache)
    def consumer = Mock(Consumer)
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def failure = new RuntimeException("main")
    def transformedException = new RuntimeException("transformed")

    def setup() {
        _ * exceptionAnalyserMock.transform(failure) >> transformedException

        _ * gradleMock.taskGraph >> taskGraphMock
        _ * gradleMock.settings >> settingsMock
        _ * gradleMock.buildListenerBroadcaster >> buildBroadcaster
    }

    DefaultGradleLauncher launcher() {
        return new DefaultGradleLauncher(gradleMock, buildConfigurerMock, exceptionAnalyserMock, buildBroadcaster,
            buildCompletionListener, buildFinishedListener, buildExecuter, buildServices, [otherService],
            settingsPreparerMock, taskExecutionPreparerMock, configurationCache, Mock(BuildOptionBuildOperationProgressEventsEmitter))
    }

    void testCanFinishBuildWhenNothingHasBeenDone() {
        def gradleLauncher = launcher()

        when:
        gradleLauncher.finishBuild(null, consumer)

        then:
        0 * buildBroadcaster._
        0 * consumer._
    }

    void testScheduleAndRunRequestedTasks() {
        expect:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRun()
        expectBuildFinished()

        def gradleLauncher = launcher()
        gradleLauncher.scheduleRequestedTasks()
        gradleLauncher.executeTasks()
        gradleLauncher.finishBuild(null, consumer)
    }

    void testScheduleAndRunAsNestedBuild() {
        expect:
        isNestedBuild()

        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRun()
        expectBuildFinished()

        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.scheduleRequestedTasks()
        gradleLauncher.executeTasks()
        gradleLauncher.finishBuild(null, consumer)
    }

    void testGetLoadedSettings() {
        when:
        isRootBuild()
        expectSettingsBuilt()

        DefaultGradleLauncher gradleLauncher = launcher()
        def result = gradleLauncher.getLoadedSettings()

        then:
        result == settingsMock

        expect:
        expectBuildFinished("Configure")
        gradleLauncher.finishBuild(null, consumer)
    }

    void testGetConfiguredBuild() {
        when:
        isRootBuild()
        expectSettingsBuilt()

        and:
        1 * buildConfigurerMock.prepareProjects(gradleMock)

        DefaultGradleLauncher gradleLauncher = launcher()
        def result = gradleLauncher.getConfiguredBuild()

        then:
        result == gradleMock

        expect:
        expectBuildFinished("Configure")
        gradleLauncher.finishBuild(null, consumer)
    }

    void testNotifiesListenerOnConfigureBuildFailure() {
        def failure = new RuntimeException()

        when:
        isRootBuild()
        expectSettingsBuilt()

        and:
        1 * buildConfigurerMock.prepareProjects(gradleMock) >> { throw failure }
        1 * exceptionAnalyserMock.transform({ it == failure }) >> transformedException

        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.getConfiguredBuild()

        then:
        def t = thrown RuntimeException
        t == transformedException

        when:
        gradleLauncher.finishBuild(null, consumer)

        then:
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException })
        0 * consumer._
    }

    void testImplicitlySchedulesRequestedTasksIfNotAlready() {
        when:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRun()

        then:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.executeTasks()
    }

    void testNotifiesListenerOnSettingsInitWithFailure() {
        given:
        isRootBuild()

        and:
        1 * settingsPreparerMock.prepareSettings(gradleMock) >> { throw failure }

        when:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.scheduleRequestedTasks()

        then:
        def t = thrown RuntimeException
        t == transformedException

        when:
        gradleLauncher.finishBuild(null, consumer)

        then:
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException && it.action == "Build" })
        0 * consumer._
    }

    void testNotifiesListenerOnTaskExecutionFailure() {
        given:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRunWithFailure(failure)

        and:
        1 * exceptionAnalyserMock.transform({ it instanceof MultipleBuildFailures && it.cause == failure }) >> transformedException

        when:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.scheduleRequestedTasks()
        gradleLauncher.executeTasks()

        then:
        def t = thrown RuntimeException
        t == transformedException

        when:
        gradleLauncher.finishBuild(null, consumer)

        then:
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException })
        0 * consumer._
    }

    void testNotifiesListenerOnBuildCompleteWithMultipleFailures() {
        def failure2 = new RuntimeException()

        given:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRunWithFailure(failure, failure2)

        and:
        1 * exceptionAnalyserMock.transform({ it instanceof MultipleBuildFailures && it.causes == [failure, failure2] }) >> transformedException

        when:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.scheduleRequestedTasks()
        gradleLauncher.executeTasks()

        then:
        def t = thrown RuntimeException
        t == transformedException

        when:
        gradleLauncher.finishBuild(null, consumer)

        then:
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException })
        0 * consumer._
    }

    void testTransformsBuildFinishedListenerFailure() {
        def consumer = Mock(Consumer)

        given:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRun()

        and:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.scheduleRequestedTasks()
        gradleLauncher.executeTasks()

        when:
        gradleLauncher.finishBuild(null, consumer)

        then:
        1 * buildBroadcaster.buildFinished({ it.failure == null }) >> { throw failure }
        1 * consumer.accept(failure)
        0 * consumer._
    }

    void testNotifiesListenersOnMultipleBuildFailuresAndBuildListenerFailure() {
        def failure2 = new RuntimeException()
        def failure3 = new RuntimeException()

        given:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRunWithFailure(failure, failure2)

        and:
        1 * exceptionAnalyserMock.transform({ it instanceof MultipleBuildFailures && it.causes == [failure, failure2] }) >> transformedException

        and:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.scheduleRequestedTasks()

        when:
        gradleLauncher.executeTasks()

        then:
        def t = thrown RuntimeException
        t == transformedException

        when:
        gradleLauncher.finishBuild(null, consumer)

        then:
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException }) >> { throw failure3 }
        1 * consumer.accept(failure3)
        0 * consumer._
    }

    void testCleansUpOnStop() throws IOException {
        when:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.stop()

        then:
        1 * buildServices.close()
        1 * otherService.stop()
        1 * buildCompletionListener.completed()
    }

    private void isNestedBuild() {
        _ * gradleMock.parent >> Mock(GradleInternal)
        _ * gradleMock.getIdentityPath() >> path(":nested")
        _ * gradleMock.contextualize(_) >> { "${it[0]} (:nested)" }
    }

    private void isRootBuild() {
        _ * gradleMock.parent >> null
        _ * gradleMock.contextualize(_) >> { it[0] }
    }

    private void expectSettingsBuilt() {
        1 * settingsPreparerMock.prepareSettings(gradleMock)
    }

    private void expectTaskGraphBuilt() {
        1 * taskExecutionPreparerMock.prepareForTaskExecution(gradleMock)
    }

    private void expectTasksRun() {
        1 * buildExecuter.execute(gradleMock, _)
    }

    private void expectTasksRunWithFailure(Throwable failure, Throwable other = null) {
        1 * buildExecuter.execute(gradleMock, _) >> { GradleInternal g, List failures ->
            failures.add(failure)
            if (other != null) {
                failures.add(other)
            }
        }
    }

    private void expectBuildFinished(String action = "Build") {
        1 * buildBroadcaster.buildFinished({ it.failure == null && it.action == action })
        1 * buildFinishedListener.buildFinished(_, false)
        0 * consumer._
    }
}
