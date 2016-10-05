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
import org.gradle.BuildResult
import org.gradle.StartParameter
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.ExceptionAnalyser
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.changedetection.state.TaskHistoryStore
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.configuration.BuildConfigurer
import org.gradle.execution.BuildConfigurationActionExecuter
import org.gradle.execution.BuildExecuter
import org.gradle.execution.TaskGraphExecuter
import org.gradle.internal.Factory
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.progress.BuildOperationDetails
import org.gradle.internal.progress.BuildOperationExecutor
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.BuildScopeServices
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import spock.lang.Specification

public class DefaultGradleLauncherSpec extends Specification {
    def initScriptHandlerMock = Mock(InitScriptHandler.class);
    def settingsLoaderMock = Mock(SettingsLoader.class);
    def taskExecuterMock = Mock(TaskGraphExecuter.class);
    def buildConfigurerMock = Mock(BuildConfigurer.class);
    def buildBroadcaster = Mock(BuildListener.class);
    def buildExecuter = Mock(BuildExecuter.class);
    def buildConfigurationActionExecuter = Mock(BuildConfigurationActionExecuter.class);
    def buildScopeServices = Mock(ServiceRegistry)
    def taskArtifactStateCacheAccess = Mock(TaskHistoryStore)

    private ProjectInternal expectedRootProject;
    private ProjectInternal expectedCurrentProject;
    private StartParameter expectedStartParams;
    private SettingsInternal settingsMock = Mock(SettingsInternal.class);
    private GradleInternal gradleMock = Mock(GradleInternal.class);

    private ProjectDescriptor expectedRootProjectDescriptor;

    private ClassLoaderScope baseClassLoaderScope = Mock(ClassLoaderScope.class);
    private ExceptionAnalyser exceptionAnalyserMock = Mock(ExceptionAnalyser);
    private LoggingManagerInternal loggingManagerMock = Mock(LoggingManagerInternal.class);
    private ModelConfigurationListener modelListenerMock = Mock(ModelConfigurationListener.class);
    private BuildCompletionListener buildCompletionListener = Mock(BuildCompletionListener.class);
    private BuildOperationExecutor buildOperationExecutor = new TestBuildOperationExecutor();
    private BuildScopeServices buildServices = Mock(BuildScopeServices.class);
    private Stoppable otherService = Mock(Stoppable)
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    final RuntimeException failure = new RuntimeException("main");
    final RuntimeException transformedException = new RuntimeException("transformed");

    def setup() {
        boolean expectedSearchUpwards = false;

        File expectedRootDir = tmpDir.file("rootDir");
        File expectedCurrentDir = new File(expectedRootDir, "currentDir");

        expectedRootProjectDescriptor = new DefaultProjectDescriptor(null, "someName", new File("somedir"), new DefaultProjectDescriptorRegistry(),
            TestFiles.resolver(expectedRootDir));
        expectedRootProject = TestUtil.createRootProject(expectedRootDir);
        expectedCurrentProject = TestUtil.createRootProject(expectedCurrentDir);

        expectedStartParams = new StartParameter();
        expectedStartParams.setCurrentDir(expectedCurrentDir);
        expectedStartParams.setSearchUpwards(expectedSearchUpwards);
        expectedStartParams.setGradleUserHomeDir(tmpDir.createDir("gradleUserHome"));

        _ * exceptionAnalyserMock.transform(failure) >> transformedException

        _ * settingsMock.getRootProject() >> expectedRootProjectDescriptor
        _ * settingsMock.getDefaultProject() >> expectedRootProjectDescriptor
        _ * settingsMock.getIncludedBuilds() >> []
        _ * settingsMock.getRootClassLoaderScope() >> baseClassLoaderScope
        0 * settingsMock._

        _ * gradleMock.getRootProject() >> expectedRootProject
        _ * gradleMock.getDefaultProject() >> expectedCurrentProject
        _ * gradleMock.getTaskGraph() >> taskExecuterMock
        _ * gradleMock.getStartParameter() >> expectedStartParams
        _ * gradleMock.getServices() >> buildScopeServices
        0 * gradleMock._

        buildScopeServices.get(TaskHistoryStore) >> taskArtifactStateCacheAccess
    }

    DefaultGradleLauncher launcher() {
        return new DefaultGradleLauncher(gradleMock, initScriptHandlerMock, settingsLoaderMock,
            buildConfigurerMock, exceptionAnalyserMock, loggingManagerMock, buildBroadcaster,
            modelListenerMock, buildCompletionListener, buildOperationExecutor, buildConfigurationActionExecuter, buildExecuter,
            buildServices, [otherService]);
    }

    public void testRun() {
        when:
        expectLoggingStarted();
        expectInitScriptsExecuted();
        expectSettingsBuilt();
        expectDagBuilt();
        expectTasksRun();
        expectBuildListenerCallbacks();

        DefaultGradleLauncher gradleLauncher = launcher();
        BuildResult buildResult = gradleLauncher.run();

        then:
        buildResult.getGradle() is gradleMock
        buildResult.failure == null
        1 * taskArtifactStateCacheAccess.flush()
    }

    public void testGetBuildAnalysis() {
        when:
        expectLoggingStarted();
        expectInitScriptsExecuted();
        expectSettingsBuilt();
        expectBuildListenerCallbacks();

        1 * buildConfigurerMock.configure(gradleMock)

        DefaultGradleLauncher gradleLauncher = launcher();
        BuildResult buildResult = gradleLauncher.getBuildAnalysis();

        then:
        buildResult.getGradle() is gradleMock
        buildResult.failure == null
    }

    public void testNotifiesListenerOfBuildAnalysisStages() {
        when:
        expectLoggingStarted();
        expectInitScriptsExecuted();
        expectSettingsBuilt();
        expectBuildListenerCallbacks();
        1 * buildConfigurerMock.configure(gradleMock)

        then:
        DefaultGradleLauncher gradleLauncher = launcher();
        gradleLauncher.getBuildAnalysis();
    }

    public void testNotifiesListenerOfBuildStages() {
        when:
        expectLoggingStarted();
        expectInitScriptsExecuted();
        expectSettingsBuilt();
        expectDagBuilt();
        expectTasksRun();
        expectBuildListenerCallbacks();

        then:
        DefaultGradleLauncher gradleLauncher = launcher();
        gradleLauncher.run();
    }

    public void testNotifiesListenerOnBuildListenerFailure() {
        given:
        expectLoggingStarted();

        and:
        1 * buildBroadcaster.buildStarted(gradleMock) >> {throw failure}
        1 * buildBroadcaster.buildFinished({ it.failure == transformedException })

        when:
        DefaultGradleLauncher gradleLauncher = launcher();
        gradleLauncher.run();

        then:
        def t = thrown ReportedException
        t.cause == transformedException
    }

    public void testNotifiesListenerOnSettingsInitWithFailure() {
        given:
        expectLoggingStarted();
        expectInitScriptsExecuted();

        and:
        1 * buildBroadcaster.buildStarted(gradleMock)
        1 * settingsLoaderMock.findAndLoadSettings(gradleMock) >> {throw failure}
        1 * buildBroadcaster.buildFinished({it.failure == transformedException})

        when:
        DefaultGradleLauncher gradleLauncher = launcher();
        gradleLauncher.run();

        then:
        def t = thrown ReportedException
        t.cause == transformedException
    }

    public void testNotifiesListenerOnBuildCompleteWithFailure() {
        given:
        expectLoggingStarted();
        expectInitScriptsExecuted();
        expectSettingsBuilt();
        expectDagBuilt();
        expectTasksRunWithFailure(failure);

        and:
        1 * buildBroadcaster.buildStarted(gradleMock)
        1 * buildBroadcaster.projectsEvaluated(gradleMock)
        1 * modelListenerMock.onConfigure(gradleMock)
        1 * buildBroadcaster.buildFinished({it.failure == transformedException})

        when:
        DefaultGradleLauncher gradleLauncher = launcher();
        gradleLauncher.run();

        then:
        def t = thrown ReportedException
        t.cause == transformedException
    }

    public void testCleansUpOnStop() throws IOException {
        given:
        expectLoggingStarted();

        when:
        DefaultGradleLauncher gradleLauncher = launcher();
        gradleLauncher.stop();

        then:
        1 * loggingManagerMock.stop()
        1 * buildServices.close()
        1 * otherService.stop()
        1 * buildCompletionListener.completed()
    }

    private void expectLoggingStarted() {
        1 * loggingManagerMock.start()
    }

    private void expectInitScriptsExecuted() {
        1 * initScriptHandlerMock.executeScripts(gradleMock)
    }

    private void expectSettingsBuilt() {
        1 * settingsLoaderMock.findAndLoadSettings(gradleMock) >> settingsMock
    }

    private void expectBuildListenerCallbacks() {
        1 * buildBroadcaster.buildStarted(gradleMock)
        1 * buildBroadcaster.projectsEvaluated(gradleMock)
        1 * buildBroadcaster.buildFinished({BuildResult result -> result.failure == null})
        1 * modelListenerMock.onConfigure(gradleMock)
    }

    private void expectDagBuilt() {
        1 * buildConfigurerMock.configure(gradleMock)
        1 * buildConfigurationActionExecuter.select(gradleMock)
    }

    private void expectTasksRun() {
        1 * buildExecuter.execute(gradleMock)
    }

    private void expectTasksRunWithFailure(final Throwable failure) {
        1 * buildExecuter.execute(gradleMock) >> {throw failure}
    }

    private static class TestBuildOperationExecutor implements BuildOperationExecutor {
        @Override
        public Object getCurrentOperationId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T run(BuildOperationDetails operationDetails, Factory<T> factory) {
            return factory.create();
        }

        @Override
        public <T> T run(String displayName, Factory<T> factory) {
            return factory.create();
        }

        @Override
        public void run(BuildOperationDetails operationDetails, Runnable action) {
            action.run();
        }

        @Override
        public void run(String displayName, Runnable action) {
            action.run();
        }
    }
}
