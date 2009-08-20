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
import org.gradle.api.UnknownTaskException;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.TaskExecuter;
import org.gradle.initialization.*;
import org.gradle.util.HelperUtil;
import static org.gradle.util.WrapUtil.*;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
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
public class GradleTest {
    private BuildLoader buildLoaderMock;
    private SettingsHandler settingsHandlerMock;
    private IGradlePropertiesLoader gradlePropertiesLoaderMock;
    private BuildConfigurer buildConfigurerMock;
    private DefaultProject expectedRootProject;
    private DefaultProject expectedCurrentProject;
    private SettingsInternal settingsMock;
    private List<String> expectedTaskNames;
    private List<Iterable<Task>> expectedTasks;
    private StartParameter expectedStartParams;
    private BuildInternal buildMock;
    private BuildListener buildBroadcaster;

    private Map testGradleProperties = new HashMap();

    private Gradle gradle;

    private TaskExecuter taskExecuterMock;

    private ProjectDescriptor expectedRootProjectDescriptor;
    
    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        HelperUtil.deleteTestDir();
        settingsHandlerMock = context.mock(SettingsHandler.class);
        gradlePropertiesLoaderMock = context.mock(IGradlePropertiesLoader.class);
        settingsMock = context.mock(SettingsInternal.class);
        taskExecuterMock = context.mock(TaskExecuter.class);
        buildLoaderMock = context.mock(BuildLoader.class);
        buildConfigurerMock = context.mock(BuildConfigurer.class);
        buildMock = context.mock(BuildInternal.class);
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
        expectedStartParams.setGradleUserHomeDir(new File(HelperUtil.TMP_DIR_FOR_TEST, "gradleUserHomeDir"));

        gradle = new Gradle(buildMock, settingsHandlerMock, gradlePropertiesLoaderMock, buildLoaderMock, buildConfigurerMock);
        
        context.checking(new Expectations() {
            {
                allowing(gradlePropertiesLoaderMock).getGradleProperties();
                will(returnValue(testGradleProperties));
                allowing(settingsMock).getRootProject();
                will(returnValue(expectedRootProjectDescriptor));
                allowing(buildMock).getRootProject();
                will(returnValue(expectedRootProject));
                allowing(buildMock).getDefaultProject();
                will(returnValue(expectedCurrentProject));
                allowing(buildMock).getTaskGraph();
                will(returnValue(taskExecuterMock));
                allowing(buildMock).getStartParameter();
                will(returnValue(expectedStartParams));
                allowing(buildMock).getBuildListenerBroadcaster();
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

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test
    public void testRun() {
        expectSettingsBuilt();
        expectTasksRunWithDagRebuild();
        expectBuildListenerCallbacks();
        BuildResult buildResult = gradle.run();
        assertThat(buildResult.getSettings(), sameInstance((Settings) settingsMock));
        assertThat(buildResult.getFailure(), nullValue());
    }

    @Test
    public void testDryRun() {
        expectSettingsBuilt();
        expectTasksRunWithDagRebuild();
        expectBuildListenerCallbacks();
        context.checking(new Expectations() {{
            one(taskExecuterMock).getAllTasks();
            will(returnValue(toList()));
        }});
        expectedStartParams.setDryRun(true);
        BuildResult buildResult = gradle.run();
        assertThat(buildResult.getSettings(), sameInstance((Settings) settingsMock));
        assertThat(buildResult.getFailure(), nullValue());
    }

    @Test
    public void testGetBuildAndRunAnalysis() {
        expectSettingsBuilt();
        expectTasksRunWithDagRebuild();
        expectBuildListenerCallbacks();
        BuildResult buildResult = gradle.getBuildAndRunAnalysis();
        assertThat(buildResult.getSettings(), sameInstance((Settings) settingsMock));
        assertThat(buildResult.getFailure(), nullValue());
    }

    @Test
    public void testGetBuildAnalysis() {
        expectSettingsBuilt();
        expectBuildListenerCallbacks();
        context.checking(new Expectations() {{
            one(buildLoaderMock).load(expectedRootProjectDescriptor, buildMock, testGradleProperties);
            one(buildConfigurerMock).process(expectedRootProject);
        }});
        BuildResult buildResult = gradle.getBuildAnalysis();
        assertThat(buildResult.getSettings(), sameInstance((Settings) settingsMock));
        assertThat(buildResult.getFailure(), nullValue());
        assertThat((BuildInternal) buildResult.getBuild(), sameInstance(buildMock));
    }

    @Test
    public void testGetBuildAnalysisWithFailure() {
        final RuntimeException exception = new RuntimeException();
        expectSettingsBuilt();
        context.checking(new Expectations() {{
            one(buildBroadcaster).buildStarted(buildMock);
            one(buildLoaderMock).load(expectedRootProjectDescriptor, buildMock, testGradleProperties);
            will(throwException(exception));
            one(buildBroadcaster).buildFinished(with(any(BuildResult.class)));
        }});
        BuildResult buildResult = gradle.getBuildAnalysis();
        assertThat(buildResult.getSettings(), sameInstance((Settings) settingsMock));
        assertThat((RuntimeException) buildResult.getFailure(), sameInstance(exception));
        assertThat((BuildInternal) buildResult.getBuild(), sameInstance(buildMock));
    }

    @Test
    public void testNotifiesListenerOfBuildAnalysisStages() {
        expectSettingsBuilt();
        expectBuildListenerCallbacks();
        context.checking(new Expectations() {{
            one(buildLoaderMock).load(expectedRootProjectDescriptor, buildMock, testGradleProperties);
            one(buildConfigurerMock).process(expectedRootProject);
        }});

        gradle.getBuildAnalysis();
    }

    @Test
    public void testNotifiesListenerOfBuildStages() {
        expectSettingsBuilt();
        expectTasksRunWithDagRebuild();
        expectBuildListenerCallbacks();

        gradle.run();
    }

    @Test
    public void testNotifiesListenerOnSettingsInitWithFailure() {
        final RuntimeException failure = new RuntimeException();
        context.checking(new Expectations() {{
            one(buildBroadcaster).buildStarted(buildMock);
            one(settingsHandlerMock).findAndLoadSettings(buildMock, gradlePropertiesLoaderMock);
            will(throwException(failure));
            one(buildBroadcaster).buildFinished(with(result(null, sameInstance(failure))));
        }});

        BuildResult buildResult = gradle.run();
        assertThat(buildResult.getFailure(), sameInstance((Throwable) failure));
    }

    @Test
    public void testNotifiesListenerOnBuildCompleteWithFailure() {
        final RuntimeException failure = new RuntimeException();
        expectSettingsBuilt();
        expectTasksRunWithFailure(failure);
        context.checking(new Expectations() {{
            one(buildBroadcaster).buildStarted(buildMock);
            one(buildBroadcaster).projectsLoaded(buildMock);
            one(buildBroadcaster).projectsEvaluated(buildMock);
            one(buildBroadcaster).buildFinished(with(result(settingsMock, sameInstance(failure))));
        }});

        BuildResult buildResult = gradle.run();
        assertThat(buildResult.getFailure(), sameInstance((Throwable) failure));
    }

    private void expectSettingsBuilt() {
        context.checking(new Expectations() {
            {
                one(settingsHandlerMock).findAndLoadSettings(buildMock, gradlePropertiesLoaderMock);
                will(returnValue(settingsMock));
                one(buildBroadcaster).settingsEvaluated(settingsMock);
            }
        });
    }

    private void expectBuildListenerCallbacks() {
        context.checking(new Expectations() {{
            one(buildBroadcaster).buildStarted(buildMock);
            one(buildBroadcaster).projectsLoaded(buildMock);
            one(buildBroadcaster).projectsEvaluated(buildMock);
            one(buildBroadcaster).buildFinished(with(result(settingsMock, nullValue(Throwable.class))));
        }});
    }

    private void expectTasksRunWithDagRebuild() {
        context.checking(new Expectations() {
            {
                one(buildLoaderMock).load(expectedRootProjectDescriptor, buildMock, testGradleProperties);
                one(buildConfigurerMock).process(expectedRootProject);
                one(taskExecuterMock).addTaskExecutionGraphListener(with(notNullValue(TaskExecutionGraphListener.class)));
                one(taskExecuterMock).addTasks(expectedTasks.get(0));
                one(taskExecuterMock).addTasks(expectedTasks.get(1));
                one(taskExecuterMock).execute();
            }
        });
    }

    private void expectTasksRunWithFailure(final Throwable failure) {
        context.checking(new Expectations() {
            {
                one(buildLoaderMock).load(expectedRootProjectDescriptor, buildMock, testGradleProperties);
                one(taskExecuterMock).addTaskExecutionGraphListener(with(notNullValue(TaskExecutionGraphListener.class)));
                one(buildConfigurerMock).process(expectedRootProject);
                one(taskExecuterMock).addTasks(expectedTasks.get(0));
                one(taskExecuterMock).addTasks(expectedTasks.get(1));
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
                one(buildBroadcaster).buildStarted(buildMock);
                one(buildBroadcaster).projectsLoaded(buildMock);
                one(buildBroadcaster).projectsEvaluated(buildMock);
                one(buildBroadcaster).buildFinished(with(any(BuildResult.class)));
                one(buildLoaderMock).load(expectedRootProjectDescriptor, buildMock, testGradleProperties);
                one(taskExecuterMock).addTaskExecutionGraphListener(with(notNullValue(TaskExecutionGraphListener.class)));
                one(buildConfigurerMock).process(expectedRootProject);
            }
        });
        BuildResult buildResult = gradle.run();
        assertThat(buildResult.getFailure(), notNullValue());
        assertThat(buildResult.getFailure().getClass(), equalTo((Object) UnknownTaskException.class));
    }

    // todo: This test is rather weak. Make it stronger.
    @Test
    public void testNewInstanceFactory() {
        StartParameter startParameter = new StartParameter();
        startParameter.setGradleHomeDir(new File(HelperUtil.TMP_DIR_FOR_TEST, "gradleHomeDir"));
        Gradle gradle = Gradle.newInstance(startParameter);
        assertThat(gradle, notNullValue());
    }

    private Matcher<BuildResult> result(final Settings expectedSettings, final Matcher<? extends Throwable> exceptionMatcher) {
        return new BaseMatcher<BuildResult>() {
            public void describeTo(Description description) {
                description.appendText("matching build result");
            }

            public boolean matches(Object actual) {
                BuildResult result = (BuildResult) actual;
                return (result.getSettings() == expectedSettings) && exceptionMatcher.matches(result.getFailure());
            }
        };
    }
}
