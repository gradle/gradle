/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.invocation;

import groovy.lang.Closure;
import org.gradle.BuildListener;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
import org.gradle.util.GradleVersion;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.MultiParentClassLoader;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JMock.class)
public class DefaultGradleTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final StartParameter parameter = new StartParameter();
    private final ScriptHandler scriptHandlerMock = context.mock(ScriptHandler.class);
    private final ServiceRegistryFactory serviceRegistryFactoryMock = context.mock(ServiceRegistryFactory.class, "parent");
    private final ServiceRegistryFactory gradleServiceRegistryMock = context.mock(ServiceRegistryFactory.class, "gradle");
    private final IProjectRegistry projectRegistry = context.mock(IProjectRegistry.class);
    private final PluginRegistry pluginRegistry = context.mock(PluginRegistry.class);
    private final TaskGraphExecuter taskExecuter = context.mock(TaskGraphExecuter.class);
    private final ListenerManager listenerManager = context.mock(ListenerManager.class);
    private final Gradle parent = context.mock(Gradle.class, "parentBuild");
    private final MultiParentClassLoader scriptClassLoaderMock = context.mock(MultiParentClassLoader.class);
    private final GradleDistributionLocator gradleDistributionLocatorMock = context.mock(GradleDistributionLocator.class);
    private final ListenerBroadcast<BuildListener> buildListenerBroadcast = new ListenerBroadcast<BuildListener>(BuildListener.class);
    private final ListenerBroadcast<ProjectEvaluationListener> projectEvaluationListenerBroadcast = context.mock(ListenerBroadcast.class);
    private final FileResolver fileResolverMock = context.mock(FileResolver.class);
    private final PluginContainer pluginContainer = context.mock(PluginContainer.class);
    private final ScriptPluginFactory scriptPluginFactory= context.mock(ScriptPluginFactory.class);

    private DefaultGradle gradle;

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            one(serviceRegistryFactoryMock).createFor(with(any(DefaultGradle.class)));
            will(returnValue(gradleServiceRegistryMock));
            allowing(gradleServiceRegistryMock).get(ScriptHandler.class);
            will(returnValue(scriptHandlerMock));
            allowing(gradleServiceRegistryMock).get(ScriptClassLoaderProvider.class);
            will(returnValue(context.mock(ScriptClassLoaderProvider.class)));
            allowing(gradleServiceRegistryMock).get(IProjectRegistry.class);
            will(returnValue(projectRegistry));
            allowing(gradleServiceRegistryMock).get(PluginRegistry.class);
            will(returnValue(pluginRegistry));
            allowing(gradleServiceRegistryMock).get(TaskGraphExecuter.class);
            will(returnValue(taskExecuter));
            allowing(gradleServiceRegistryMock).get(ListenerManager.class);
            will(returnValue(listenerManager));
            allowing(gradleServiceRegistryMock).get(MultiParentClassLoader.class);
            will(returnValue(scriptClassLoaderMock));
            allowing(gradleServiceRegistryMock).get(GradleDistributionLocator.class);
            will(returnValue(gradleDistributionLocatorMock));
            allowing(gradleServiceRegistryMock).get(PluginContainer.class);
            will(returnValue(pluginContainer));
            allowing(gradleServiceRegistryMock).get(FileResolver.class);
            will(returnValue(fileResolverMock));
            allowing(gradleServiceRegistryMock).get(ScriptPluginFactory.class);
            will(returnValue(scriptPluginFactory));
            allowing(listenerManager).createAnonymousBroadcaster(BuildListener.class);
            will(returnValue(buildListenerBroadcast));
            allowing(listenerManager).createAnonymousBroadcaster(ProjectEvaluationListener.class);
            will(returnValue(projectEvaluationListenerBroadcast));
        }});
        gradle = new DefaultGradle(parent, parameter, serviceRegistryFactoryMock);
    }

    @Test
    public void defaultValues() {
        assertThat(gradle.getParent(), sameInstance(parent));
        assertThat(gradle.getServices(), sameInstance(gradleServiceRegistryMock));
        assertThat(gradle.getProjectRegistry(), sameInstance(projectRegistry));
        assertThat(gradle.getTaskGraph(), sameInstance(taskExecuter));
    }

    @Test
    public void usesGradleVersion() {
        assertThat(gradle.getGradleVersion(), equalTo(GradleVersion.current().getVersion()));
    }

    @Test
    public void usesDistributionLocatorForGradleHomeDir() throws IOException {
        final File gradleHome = new File("home");

        context.checking(new Expectations() {{
            one(gradleDistributionLocatorMock).getGradleHome();
            will(returnValue(gradleHome));
        }});

        assertThat(gradle.getGradleHomeDir(), equalTo(gradleHome));
    }

    @Test
    public void usesStartParameterForUserDir() throws IOException {
        parameter.setGradleUserHomeDir(new File("user"));

        assertThat(gradle.getGradleUserHomeDir(), equalTo(new File("user").getCanonicalFile()));
    }

    @Test
    public void broadcastsProjectEventsToListeners() {
        final ProjectEvaluationListener broadcaster = context.mock(ProjectEvaluationListener.class, "broadcaster");
        context.checking(new Expectations() {{
            one(projectEvaluationListenerBroadcast).getSource();
            will(returnValue(broadcaster));
        }});

        assertThat(gradle.getProjectEvaluationBroadcaster(), sameInstance(broadcaster));
    }

    @Test
    public void broadcastsBeforeProjectEvaluateEventsToClosures() {
        final Closure closure = HelperUtil.TEST_CLOSURE;
        context.checking(new Expectations() {{
            one(projectEvaluationListenerBroadcast).add(new ClosureBackedMethodInvocationDispatch("beforeEvaluate", closure));
        }});

        gradle.beforeProject(closure);
    }

    @Test
    public void broadcastsAfterProjectEvaluateEventsToClosures() {
        final Closure closure = HelperUtil.TEST_CLOSURE;
        context.checking(new Expectations() {{
            one(projectEvaluationListenerBroadcast).add(new ClosureBackedMethodInvocationDispatch("afterEvaluate", closure));
        }});

        gradle.afterProject(closure);
    }

    @Test
    public void broadcastsBuildStartedEventsToClosures() {
        final Closure closure = closure();
        gradle.buildStarted(closure);

        context.checking(new Expectations() {{
            one(closure).call(new Object[0]);
        }});
        gradle.getBuildListenerBroadcaster().buildStarted(gradle);
    }

    @Test
    public void broadcastsSettingsEvaluatedEventsToClosures() {
        final Closure closure = closure();
        gradle.settingsEvaluated(closure);

        context.checking(new Expectations() {{
            one(closure).call(new Object[0]);
        }});

        gradle.getBuildListenerBroadcaster().settingsEvaluated(null);
    }

    @Test
    public void broadcastsProjectsLoadedEventsToClosures() {
        final Closure closure = closure();
        gradle.projectsLoaded(closure);

        context.checking(new Expectations() {{
            one(closure).call(new Object[0]);
        }});

        gradle.getBuildListenerBroadcaster().projectsLoaded(gradle);
    }

    @Test
    public void broadcastsProjectsEvaluatedEventsToClosures() {
        final Closure closure = closure();
        gradle.projectsEvaluated(closure);

        context.checking(new Expectations() {{
            one(closure).call(new Object[0]);
        }});
        gradle.getBuildListenerBroadcaster().projectsEvaluated(gradle);
    }

    @Test
    public void broadcastsBuildFinishedEventsToClosures() {
        final Closure closure = closure();
        gradle.buildFinished(closure);

        context.checking(new Expectations() {{
            one(closure).call(new Object[0]);
        }});
        gradle.getBuildListenerBroadcaster().buildFinished(null);
    }

    @Test
    public void usesSpecifiedLogger() {
        final Object logger = new Object();
        context.checking(new Expectations() {{
            one(listenerManager).useLogger(logger);
        }});
        gradle.useLogger(logger);
    }

    @Test
    public void getRootProjectThrowsExceptionWhenRootProjectIsNotAvailable() {
        try {
            gradle.getRootProject();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("The root project is not yet available for " + gradle + "."));
        }

        ProjectInternal rootProject = context.mock(ProjectInternal.class);
        gradle.setRootProject(rootProject);
        
        assertThat(gradle.getRootProject(), sameInstance(rootProject));
    }
    
    @Test
    public void rootProjectActionIsExecutedWhenProjectsAreLoaded() {
        final Action<Project> action = context.mock(Action.class);
        final ProjectInternal rootProject = context.mock(ProjectInternal.class);

        gradle.rootProject(action);

        context.checking(new Expectations() {{
            one(action).execute(rootProject);
        }});
        
        gradle.setRootProject(rootProject);
        gradle.getBuildListenerBroadcaster().projectsLoaded(gradle);
    }

    @Test
    public void allprojectsActionIsExecutedWhenProjectAreLoaded() {
        final Action<Project> action = context.mock(Action.class);
        final ProjectInternal rootProject = context.mock(ProjectInternal.class);

        gradle.allprojects(action);

        context.checking(new Expectations() {{
            one(rootProject).allprojects(action);
        }});

        gradle.setRootProject(rootProject);
        gradle.getBuildListenerBroadcaster().projectsLoaded(gradle);
    }

    @Test
    public void hasToString() {
        assertThat(gradle.toString(), equalTo("build"));

        final ProjectInternal project = context.mock(ProjectInternal.class);
        context.checking(new Expectations(){{
            allowing(project).getName();
            will(returnValue("rootProject"));
        }});
        gradle.setRootProject(project);
        assertThat(gradle.toString(), equalTo("build 'rootProject'"));
    }

    private Closure closure() {
        final Closure mock = context.mock(Closure.class);
        context.checking(new Expectations(){{
            allowing(mock).getMaximumNumberOfParameters();
            will(returnValue(0));
        }});
        return mock;
    }
}
