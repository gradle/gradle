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
import org.gradle.StartParameter;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.api.invocation.Gradle;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.listener.ListenerManager;
import org.gradle.util.GradleVersion;
import org.gradle.util.HelperUtil;
import org.gradle.util.MultiParentClassLoader;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class DefaultGradleTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
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
        assertThat(gradle.getGradleVersion(), equalTo(new GradleVersion().getVersion()));
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
        final ProjectEvaluationListener listener = context.mock(ProjectEvaluationListener.class, "listener");
        final ProjectEvaluationListener broadcaster = context.mock(ProjectEvaluationListener.class, "broadcaster");
        context.checking(new Expectations() {{
            one(listenerManager).addListener(listener);
            one(listenerManager).getBroadcaster(ProjectEvaluationListener.class);
            will(returnValue(broadcaster));
        }});

        gradle.addListener(listener);

        assertThat(gradle.getProjectEvaluationBroadcaster(), sameInstance(broadcaster));
    }

    @Test
    public void broadcastsBeforeProjectEvaluateEventsToClosures() {
        final Closure closure = HelperUtil.TEST_CLOSURE;
        context.checking(new Expectations() {{
            one(listenerManager).addListener(ProjectEvaluationListener.class, "beforeEvaluate", closure);
        }});

        gradle.beforeProject(closure);
    }

    @Test
    public void broadcastsAfterProjectEvaluateEventsToClosures() {
        final Closure closure = HelperUtil.TEST_CLOSURE;
        context.checking(new Expectations() {{
            one(listenerManager).addListener(ProjectEvaluationListener.class, "afterEvaluate", closure);
        }});

        gradle.afterProject(closure);
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
}
