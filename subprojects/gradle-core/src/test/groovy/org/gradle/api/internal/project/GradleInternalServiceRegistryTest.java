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
package org.gradle.api.internal.project;

import org.gradle.StartParameter;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.PublishModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.repositories.DefaultInternalRepository;
import org.gradle.api.internal.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.execution.DefaultTaskGraphExecuter;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.MultiParentClassLoader;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class GradleInternalServiceRegistryTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final ServiceRegistry parent = context.mock(ServiceRegistry.class);
    private final GradleInternalServiceRegistry registry = new GradleInternalServiceRegistry(parent, gradle);
    private final StartParameter startParameter = new StartParameter();
    private final PublishModuleDescriptorConverter publishModuleDescriptorConverter =
            context.mock(PublishModuleDescriptorConverter.class);
    private final ListenerManager listenerManager = context.mock(ListenerManager.class);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(parent).get(PublishModuleDescriptorConverter.class);
            will(returnValue(publishModuleDescriptorConverter));
            allowing(parent).get(ListenerManager.class);
            will(returnValue(listenerManager));
            allowing(gradle).getStartParameter();
            will(returnValue(startParameter));
            allowing(gradle).getScriptClassLoader();
            will(returnValue(new MultiParentClassLoader()));
        }});
    }

    @Test
    public void canCreateServicesForAProjectInstance() {
        ProjectInternal project = context.mock(ProjectInternal.class);
        ServiceRegistryFactory serviceRegistry = registry.createFor(project);
        assertThat(serviceRegistry, instanceOf(ProjectInternalServiceRegistry.class));
    }

    @Test
    public void providesAProjectRegistry() {
        assertThat(registry.get(IProjectRegistry.class), instanceOf(DefaultProjectRegistry.class));
        assertThat(registry.get(IProjectRegistry.class), sameInstance(registry.get(IProjectRegistry.class)));
    }

    @Test
    public void providesAPluginRegistry() {
        assertThat(registry.get(PluginRegistry.class), instanceOf(DefaultPluginRegistry.class));
        assertThat(registry.get(PluginRegistry.class), sameInstance(registry.get(PluginRegistry.class)));
    }

    @Test
    public void providesATaskGraphExecuter() {
        context.checking(new Expectations() {{
            one(listenerManager).createAnonymousBroadcaster(TaskExecutionGraphListener.class);
            will(returnValue(new ListenerBroadcast<TaskExecutionGraphListener>(TaskExecutionGraphListener.class)));
            one(listenerManager).createAnonymousBroadcaster(TaskExecutionListener.class);
            will(returnValue(new ListenerBroadcast<TaskExecutionListener>(TaskExecutionListener.class)));
        }});
        assertThat(registry.get(TaskGraphExecuter.class), instanceOf(DefaultTaskGraphExecuter.class));
        assertThat(registry.get(TaskGraphExecuter.class), sameInstance(registry.get(TaskGraphExecuter.class)));
    }

    @Test
    public void providesAnInternalRepository() {
        assertThat(registry.get(InternalRepository.class), instanceOf(DefaultInternalRepository.class));
        assertThat(registry.get(InternalRepository.class), sameInstance(registry.get(InternalRepository.class)));
    }
}
