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

import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.initialization.DefaultScriptHandler;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.repositories.DefaultInternalRepository;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.plugins.Convention;
import org.gradle.execution.DefaultTaskExecuter;
import org.gradle.execution.TaskExecuter;
import org.gradle.StartParameter;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(JMock.class)
public class GradleInternalServiceRegistryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ConfigurationContainerFactory configurationContainerFactory = context.mock(
            ConfigurationContainerFactory.class);
    private final RepositoryHandlerFactory repositoryHandlerFactory = context.mock(RepositoryHandlerFactory.class);
    private final DependencyFactory dependencyFactory = context.mock(DependencyFactory.class);
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final ServiceRegistry parent = context.mock(ServiceRegistry.class);
    private final GradleInternalServiceRegistry registry = new GradleInternalServiceRegistry(parent, gradle);
    private final StartParameter startParameter = new StartParameter();
    private final ModuleDescriptorConverter moduleDescriptorConverter = context.mock(ModuleDescriptorConverter.class);

    @Before
    public void setUp() {
        startParameter.setPluginPropertiesFile(new File("plugin"));
        context.checking(new Expectations(){{
            allowing(parent).get(RepositoryHandlerFactory.class);
            will(returnValue(repositoryHandlerFactory));
            allowing(parent).get(ConfigurationContainerFactory.class);
            will(returnValue(configurationContainerFactory));
            allowing(parent).get(DependencyFactory.class);
            will(returnValue(dependencyFactory));
            allowing(parent).get(ModuleDescriptorConverter.class);
            will(returnValue(moduleDescriptorConverter));
            allowing(gradle).getBuildScriptClassLoader();
            will(returnValue(new ClassLoader() {
            }));
            allowing(gradle).getStartParameter();
            will(returnValue(startParameter));
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
    public void providesATaskExecuter() {
        assertThat(registry.get(TaskExecuter.class), instanceOf(DefaultTaskExecuter.class));
        assertThat(registry.get(TaskExecuter.class), sameInstance(registry.get(TaskExecuter.class)));
    }

    @Test
    public void providesAScriptHandlerAndScriptClassLoaderProvider() {
        expectConfigurationHandlerCreated();

        assertThat(registry.get(ScriptHandler.class), instanceOf(DefaultScriptHandler.class));
        assertThat(registry.get(ScriptHandler.class), sameInstance(registry.get(ScriptHandler.class)));
        assertThat(registry.get(ScriptClassLoaderProvider.class), sameInstance((Object) registry.get(ScriptHandler.class)));
    }

    @Test
    public void providesAnInternalRepository() {
        assertThat(registry.get(InternalRepository.class), instanceOf(DefaultInternalRepository.class));
        assertThat(registry.get(InternalRepository.class), sameInstance(registry.get(InternalRepository.class)));
    }

    private void expectConfigurationHandlerCreated() {
        context.checking(new Expectations() {{
            RepositoryHandler repositoryHandler = context.mock(RepositoryHandler.class);
            ConfigurationContainer configurationContainer = context.mock(ConfigurationContainer.class);

            one(repositoryHandlerFactory).createRepositoryHandler(with(notNullValue(Convention.class)));
            will(returnValue(repositoryHandler));

            one(configurationContainerFactory).createConfigurationContainer(with(sameInstance(repositoryHandler)), with(
                    notNullValue(DependencyMetaDataProvider.class)));
            will(returnValue(configurationContainer));

            one(configurationContainer).add("classpath");
        }});
    }
}
