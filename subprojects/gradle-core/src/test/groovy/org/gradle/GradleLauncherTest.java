/*
 * Copyright 2002-2007 the original author or authors.
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

package org.gradle;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.TaskExecuter;
import org.gradle.execution.TaskSelectionException;
import org.gradle.initialization.*;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.util.HelperUtil;
import org.gradle.util.TemporaryFolder;
import static org.gradle.util.WrapUtil.*;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class GradleLauncherTest {
    private BuildLoader buildLoaderMock;
    private InitScriptHandler initscriptHandlerMock;
    private SettingsHandler settingsHandlerMock;
    private IGradlePropertiesLoader gradlePropertiesLoaderMock;
    private BuildConfigurer buildConfigurerMock;
    private DefaultProject expectedRootProject;
    private DefaultProject expectedCurrentProject;
    private SettingsInternal settingsMock;
    private List<String> expectedTaskNames;
    private List<Iterable<Task>> expectedTasks;
    private StartParameter expectedStartParams;
    private GradleInternal gradleMock;
    private BuildListener buildBroadcaster;

    private Map testGradleProperties = new HashMap();

    private GradleLauncher gradleLauncher;

    private TaskExecuter taskExecuterMock;

    private ProjectDescriptor expectedRootProjectDescriptor;
    
    private JUnit4Mockery context = new JUnit4Mockery();

    private LoggingConfigurer loggingConfigurerMock = context.mock(LoggingConfigurer.class);
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        initscriptHandlerMock = context.mock(InitScriptHandler.class);
        settingsHandlerMock = context.mock(SettingsHandler.class);
        gradlePropertiesLoaderMock = context.mock(IGradlePropertiesLoader.class);
        settingsMock = context.mock(SettingsInternal.class);
        taskExecuterMock = context.mock(TaskExecuter.class);
        buildLoaderMock = context.mock(BuildLoader.class);
        buildConfigurerMock = context.mock(BuildConfigurer.class);
        gradleMock = context.mock(GradleInternal.class);
        buildBroadcaster = context.mock(BuildListener.class);
        testGradleProperties = toMap("prop1", "value1");
        boolean expectedSearchUpwards = false;

        File expectedRootDir = new File("rootDir");
        File expectedCurrentDir = new File(expectedRootDir, "currentDir");

        expectedRootProjectDescriptor = new DefaultProjectDescriptor(null, "someName", new File("somedir"), new DefaultProjectDescriptorRegistry());
        expectedRootProject = HelperUtil.createRootProject(expectedRootDir);
        expectedCurrentProject = HelperUtil.createRootProject(expectedCurrentDir);

        expectTasks("a", "b");

        expectedStartParams = new StartParameter();
        expectedStartParams.setTaskNames(expectedTaskNames);
        expectedStartParams.setCurrentDir(expectedCurrentDir);
        expectedStartParams.setSearchUpwards(expectedSearchUpwards);
        expectedStartParams.setGradleUserHomeDir(tmpDir.dir("gradleUserHome"));

        gradleLauncher = new GradleLauncher(gradleMock, initscriptHandlerMock, settingsHandlerMock,
                gradlePropertiesLoaderMock, buildLoaderMock, buildConfigurerMock, loggingConfigurerMock, new DefaultListenerManager());
        
        context.checking(new Expectations() {
            {
                allowing(gradlePropertiesLoaderMock).getGradleProperties();
                will(returnValue(testGradleProperties));
                allowing(settingsMock).getRootProject();
                will(returnValue(expectedRootProjectDescriptor));
                allowing(gradleMock).getRootProject();
                will(returnValue(expectedRootProject));
                allowing(gradleMock).getDefaultProject();
                will(returnValue(expectedCurrentProject));
                allowing(gradleMock).getTaskGraph();
                will(returnValue(taskExecuterMock));
                allowing(gradleMock).getStartParameter();
                will(returnValue(expectedStartParams));
                allowing(gradleMock).getBuildListenerBroadcaster();
                will(returnValue(buildBroadcaster));
            }
        });
    }

    private void expectTasks(String... tasks) {
        expectedTaskNames = toList(tasks);
        expectedTasks = new ArrayList<Iterable<Task>>();
        for (String task : tasks) {
            expectedTasks.add(toSortedSet(expectedCurrentProject.createTask(task)));
        }
    }

    @Test
    public void testRun() {
        expectInitScripts();
        expectSettingsBuilt();
        expectDagBuilt();
        expectTasksRun();
        expectBuildListenerCallbacks();
        BuildResult buildResult = gradleLauncher.run();
        assertThat(buildResult.getGradle(), sameInstance((Object) gradleMock));
        assertThat(buildResult.getFailure(), nullValue());
    }

    @Test
    public void testDryRun() {
        expectInitScripts();
        expectSettingsBuilt();
        expectDagBuilt();
        expectTasksRun();
        expectBuildListenerCallbacks();
        context.checking(new Expectations() {{
            one(taskExecuterMock).getAllTasks();
            will(returnValue(toList()));
        }});
        expectedStartParams.setDryRun(true);
        BuildResult buildResult = gradleLauncher.run();
        assertThat(buildResult.getGradle(), sameInstance((Object) gradleMock));
        assertThat(buildResult.getFailure(), nullValue());
    }

    @Test
    public void testGetBuildAndRunAnalysis() {
        expectInitScripts();
        expectSettingsBuilt();
        expectDagBuilt();
        expectBuildListenerCallbacks();
        BuildResult buildResult = gradleLauncher.getBuildAndRunAnalysis();
        assertThat(buildResult.getGradle(), sameInstance((Object) gradleMock));
        assertThat(buildResult.getFailure(), nullValue());
    }

    @Test
    public void testGetBuildAnalysis() {
        expectInitScripts();
        expectSettingsBuilt();
        expectBuildListenerCallbacks();
        context.checking(new Expectations() {{
            one(buildLoaderMock).load(expectedRootProjectDescriptor, gradleMock, testGradleProperties);
            one(buildConfigurerMock).process(expectedRootProject);
        }});
        BuildResult buildResult = gradleLauncher.getBuildAnalysis();
        assertThat(buildResult.getFailure(), nullValue());
        assertThat(buildResult.getGradle(), sameInstance((Object) gradleMock));
    }

    @Test
    public void testGetBuildAnalysisWithFailure() {
        final RuntimeException exception = new RuntimeException();
        expectInitScripts();
        expectSettingsBuilt();
        context.checking(new Expectations() {{
            one(buildBroadcaster).buildStarted(gradleMock);
            one(buildLoaderMock).load(expectedRootProjectDescriptor, gradleMock, testGradleProperties);
            will(throwException(exception));
            one(buildBroadcaster).buildFinished(with(any(BuildResult.class)));
        }});
        BuildResult buildResult = gradleLauncher.getBuildAnalysis();
        assertThat(buildResult.getGradle(), sameInstance((Object) gradleMock));
        assertThat((RuntimeException) buildResult.getFailure(), sameInstance(exception));
    }

    @Test
    public void testNotifiesListenerOfBuildAnalysisStages() {
        expectInitScripts();
        expectSettingsBuilt();
        expectBuildListenerCallbacks();
        context.checking(new Expectations() {{
            one(buildLoaderMock).load(expectedRootProjectDescriptor, gradleMock, testGradleProperties);
            one(buildConfigurerMock).process(expectedRootProject);
        }});

        gradleLauncher.getBuildAnalysis();
    }

    @Test
    public void testNotifiesListenerOfBuildStages() {
        expectInitScripts();
        expectSettingsBuilt();
        expectDagBuilt();
        expectTasksRun();
        expectBuildListenerCallbacks();

        gradleLauncher.run();
    }

    @Test
    public void testNotifiesListenerOnSettingsInitWithFailure() {
        final RuntimeException failure = new RuntimeException();
        expectInitScripts();
        context.checking(new Expectations() {{
            one(buildBroadcaster).buildStarted(gradleMock);
            one(settingsHandlerMock).findAndLoadSettings(gradleMock, gradlePropertiesLoaderMock);
            will(throwException(failure));
            one(buildBroadcaster).buildFinished(with(result(sameInstance(failure))));
        }});

        BuildResult buildResult = gradleLauncher.run();
        assertThat(buildResult.getFailure(), sameInstance((Throwable) failure));
    }

    @Test
    public void testNotifiesListenerOnBuildCompleteWithFailure() {
        final RuntimeException failure = new RuntimeException();
        expectInitScripts();
        expectSettingsBuilt();
        expectDagBuilt();
        expectTasksRunWithFailure(failure);
        context.checking(new Expectations() {{
            one(buildBroadcaster).buildStarted(gradleMock);
            one(buildBroadcaster).projectsLoaded(gradleMock);
            one(buildBroadcaster).projectsEvaluated(gradleMock);
            one(buildBroadcaster).buildFinished(with(result(sameInstance(failure))));
        }});

        BuildResult buildResult = gradleLauncher.run();
        assertThat(buildResult.getFailure(), sameInstance((Throwable) failure));
    }

    private void expectInitScripts() {
        context.checking(new Expectations() {{
            one(initscriptHandlerMock).executeScripts(gradleMock);
        }});
    }
    private void expectSettingsBuilt() {
        context.checking(new Expectations() {
            {
                one(settingsHandlerMock).findAndLoadSettings(gradleMock, gradlePropertiesLoaderMock);
                will(returnValue(settingsMock));
                one(buildBroadcaster).settingsEvaluated(settingsMock);
            }
        });
    }

    private void expectBuildListenerCallbacks() {
        context.checking(new Expectations() {{
            one(buildBroadcaster).buildStarted(gradleMock);
            one(buildBroadcaster).projectsLoaded(gradleMock);
            one(buildBroadcaster).projectsEvaluated(gradleMock);
            one(buildBroadcaster).buildFinished(with(result(nullValue(Throwable.class))));
        }});
    }

    private void expectDagBuilt() {
        context.checking(new Expectations() {
            {
                one(buildLoaderMock).load(expectedRootProjectDescriptor, gradleMock, testGradleProperties);
                one(buildConfigurerMock).process(expectedRootProject);
                one(taskExecuterMock).addTaskExecutionGraphListener(with(notNullValue(TaskExecutionGraphListener.class)));
                one(taskExecuterMock).addTasks(expectedTasks.get(0));
                one(taskExecuterMock).addTasks(expectedTasks.get(1));
            }
        });
    }

    private void expectTasksRun() {
        context.checking(new Expectations() {
            {
                one(taskExecuterMock).execute();
            }
        });
    }

    private void expectTasksRunWithFailure(final Throwable failure) {
        context.checking(new Expectations() {
            {
                one(taskExecuterMock).execute();
                will(throwException(failure));
            }
        });
    }

    @Test
    public void testRunWithUnknownTask() {
        expectedStartParams.setTaskNames(toList("unknown"));
        expectSettingsBuilt();
        context.checking(new Expectations() {
            {
                one(initscriptHandlerMock).executeScripts(gradleMock);
                one(buildBroadcaster).buildStarted(gradleMock);
                one(buildBroadcaster).projectsLoaded(gradleMock);
                one(buildBroadcaster).projectsEvaluated(gradleMock);
                one(buildBroadcaster).buildFinished(with(any(BuildResult.class)));
                one(buildLoaderMock).load(expectedRootProjectDescriptor, gradleMock, testGradleProperties);
                one(taskExecuterMock).addTaskExecutionGraphListener(with(notNullValue(TaskExecutionGraphListener.class)));
                one(buildConfigurerMock).process(expectedRootProject);
            }
        });
        BuildResult buildResult = gradleLauncher.run();
        assertThat(buildResult.getFailure(), notNullValue());
        assertThat(buildResult.getFailure().getClass(), equalTo((Object) TaskSelectionException.class));
    }

    // todo: This test is rather weak. Make it stronger.
    @Test
    public void testNewInstanceFactory() {
        StartParameter startParameter = new StartParameter();
        startParameter.setGradleHomeDir(tmpDir.dir("gradleHome"));
        GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);
        assertThat(gradleLauncher, notNullValue());
    }

    private Matcher<BuildResult> result(final Matcher<? extends Throwable> exceptionMatcher) {
        return new BaseMatcher<BuildResult>() {
            public void describeTo(Description description) {
                description.appendText("matching build result");
            }

            public boolean matches(Object actual) {
                BuildResult result = (BuildResult) actual;
                return (result.getGradle() == gradleMock) && exceptionMatcher.matches(result.getFailure());
            }
        };
    }
}
