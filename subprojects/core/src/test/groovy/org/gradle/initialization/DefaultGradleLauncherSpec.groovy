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
import org.gradle.composite.internal.IncludedBuildControllers
import org.gradle.configuration.ProjectsPreparer
import org.gradle.execution.BuildWorkExecutor
import org.gradle.execution.MultipleBuildFailures
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.initialization.exception.ExceptionAnalyser
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.scopes.BuildScopeServices
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

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
    def buildServices = Mock(BuildScopeServices.class)
    def otherService = Mock(Stoppable)
    def includedBuildControllers = Mock(IncludedBuildControllers)
    def instantExecution = Mock(InstantExecution)
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

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
            buildCompletionListener, buildExecuter, buildServices, [otherService], includedBuildControllers,
            settingsPreparerMock, taskExecutionPreparerMock, instantExecution)
    }

    void testRunTasks() {
        when:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRun()
        expectBuildListenerCallbacks()
        DefaultGradleLauncher gradleLauncher = launcher()
        GradleInternal result = gradleLauncher.executeTasks()

        then:
        result == gradleMock
    }

    void testRunAsNestedBuild() {
        when:
        isNestedBuild()

        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRun()
        expectBuildListenerCallbacks()
        DefaultGradleLauncher gradleLauncher = launcher()
        GradleInternal result = gradleLauncher.executeTasks()

        then:
        result == gradleMock
    }

    void testGetBuildAnalysis() {
        when:
        isRootBuild()
        expectSettingsBuilt()
        expectBuildListenerCallbacks()

        1 * buildConfigurerMock.prepareProjects(gradleMock)

        DefaultGradleLauncher gradleLauncher = launcher()
        def result = gradleLauncher.getConfiguredBuild()

        then:
        result == gradleMock
    }

    void testNotifiesListenerOfBuildAnalysisStages() {
        when:
        isRootBuild()
        expectSettingsBuilt()
        expectBuildListenerCallbacks()
        1 * buildConfigurerMock.prepareProjects(gradleMock)

        then:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.getConfiguredBuild()
    }

    void testNotifiesListenerOfBuildStages() {
        when:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRun()
        expectBuildListenerCallbacks()

        then:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.executeTasks()
    }

    void testNotifiesListenerOnBuildListenerFailure() {
        given:
        isRootBuild()
        1 * buildBroadcaster.buildStarted(gradleMock) >> { throw failure }
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException })

        when:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.executeTasks()

        then:
        def t = thrown RuntimeException
        t == transformedException
    }

    void testNotifiesListenerOnSettingsInitWithFailure() {
        given:
        isRootBuild()

        and:
        1 * buildBroadcaster.buildStarted(gradleMock)
        1 * settingsPreparerMock.prepareSettings(gradleMock) >> { throw failure }
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException })

        when:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.executeTasks()

        then:
        def t = thrown RuntimeException
        t == transformedException
    }

    void testNotifiesListenerOnBuildCompleteWithFailure() {
        given:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRunWithFailure(failure)

        and:
        1 * buildBroadcaster.buildStarted(gradleMock)
        1 * exceptionAnalyserMock.transform({ it instanceof MultipleBuildFailures && it.cause == failure }) >> transformedException
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException })

        when:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.executeTasks()

        then:
        def t = thrown RuntimeException
        t == transformedException
    }

    void testNotifiesListenerOnBuildCompleteWithMultipleFailures() {
        def failure2 = new RuntimeException()

        given:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRunWithFailure(failure, failure2)

        and:
        1 * buildBroadcaster.buildStarted(gradleMock)
        1 * exceptionAnalyserMock.transform({ it instanceof MultipleBuildFailures && it.causes == [failure, failure2] }) >> transformedException
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException })

        when:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.executeTasks()

        then:
        def t = thrown RuntimeException
        t == transformedException
    }

    void testTransformsBuildFinishedListenerFailure() {
        given:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRun()

        and:
        1 * buildBroadcaster.buildStarted(gradleMock)
        1 * buildBroadcaster.buildFinished({ it.failure == null }) >> { throw failure }
        1 * exceptionAnalyserMock.transform({ it instanceof MultipleBuildFailures && it.cause == failure }) >> transformedException

        and:
        DefaultGradleLauncher gradleLauncher = launcher()
        gradleLauncher.executeTasks()

        when:
        gradleLauncher.finishBuild()

        then:
        def t = thrown RuntimeException
        t == transformedException
    }

    void testNotifiesListenersOnMultipleBuildFailuresAndBuildListenerFailure() {
        def failure2 = new RuntimeException()
        def failure3 = new RuntimeException()
        def finalException = new RuntimeException()

        given:
        isRootBuild()
        expectSettingsBuilt()
        expectTaskGraphBuilt()
        expectTasksRunWithFailure(failure, failure2)

        and:
        1 * buildBroadcaster.buildStarted(gradleMock)
        1 * exceptionAnalyserMock.transform({ it instanceof MultipleBuildFailures && it.causes == [failure, failure2] }) >> transformedException
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException }) >> { throw failure3 }
        1 * exceptionAnalyserMock.transform({ it instanceof MultipleBuildFailures && it.causes == [failure, failure2, failure3] }) >> finalException

        and:
        DefaultGradleLauncher gradleLauncher = launcher()

        when:
        gradleLauncher.executeTasks()

        then:
        def t = thrown RuntimeException
        t == finalException
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
        _ * gradleMock.findIdentityPath() >> path(":nested")
        _ * gradleMock.contextualize(_) >> { "${it[0]} (:nested)" }
    }

    private void isRootBuild() {
        _ * gradleMock.parent >> null
        _ * gradleMock.contextualize(_) >> { it[0] }
    }

    private void expectSettingsBuilt() {
        1 * settingsPreparerMock.prepareSettings(gradleMock)
    }

    private void expectBuildListenerCallbacks() {
        1 * buildBroadcaster.buildStarted(gradleMock)
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
        1 * includedBuildControllers.finishBuild(_)
    }
}
