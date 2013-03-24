/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.AntBuilder;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.*;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.file.*;
import org.gradle.api.internal.initialization.DefaultScriptHandler;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.plugins.DefaultProjectsPluginContainer;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.tasks.DefaultTaskContainerFactory;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.internal.DefaultToolingModelBuilderRegistry;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class ProjectInternalServiceRegistryTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final ConfigurationContainerInternal configurationContainer = context.mock(ConfigurationContainerInternal.class);
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final DependencyManagementServices dependencyManagementServices = context.mock(DependencyManagementServices.class);
    private final ITaskFactory taskFactory = context.mock(ITaskFactory.class);
    private final DependencyFactory dependencyFactory = context.mock(DependencyFactory.class);
    private final ServiceRegistry parent = context.mock(ServiceRegistry.class);
    private final ProjectInternalServiceRegistry registry = new ProjectInternalServiceRegistry(parent, project);
    private final PluginRegistry pluginRegistry = context.mock(PluginRegistry.class);
    private final DependencyResolutionServices dependencyResolutionServices = context.mock(DependencyResolutionServices.class);
    private final RepositoryHandler repositoryHandler = context.mock(RepositoryHandler.class);
    private final ArtifactPublicationServices publicationServices = context.mock(ArtifactPublicationServices.class);
    private final DependencyHandler dependencyHandler = context.mock(DependencyHandler.class);
    private final ArtifactHandler artifactHandler = context.mock(ArtifactHandler.class);
    private final DirectInstantiator instantiator = new DirectInstantiator();

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(project).getGradle();
            will(returnValue(gradle));
            allowing(project).getProjectDir();
            will(returnValue(new File("project-dir").getAbsoluteFile()));
            allowing(project).getBuildScriptSource();
            allowing(parent).get(ITaskFactory.class);
            will(returnValue(taskFactory));
            allowing(parent).get(DependencyFactory.class);
            will(returnValue(dependencyFactory));
            allowing(parent).get(PluginRegistry.class);
            will(returnValue(pluginRegistry));
            allowing(parent).get(DependencyManagementServices.class);
            will(returnValue(dependencyManagementServices));
            allowing(parent).get(org.gradle.internal.reflect.Instantiator.class);
            will(returnValue(instantiator));
            allowing(parent).get(FileSystem.class);
            will(returnValue(context.mock(FileSystem.class)));
            allowing(parent).get(ClassGenerator.class);
            will(returnValue(context.mock(ClassGenerator.class)));
            allowing(parent).get(ProjectAccessListener.class);
            will(returnValue(context.mock(ProjectAccessListener.class)));
        }});
    }

    @Test
    public void createsARegistryForATask() {
        ServiceRegistryFactory taskRegistry = registry.createFor(context.mock(TaskInternal.class));
        assertThat(taskRegistry, instanceOf(TaskInternalServiceRegistry.class));
    }

    @Test
    public void providesATaskContainerFactory() {
        final ITaskFactory childFactory = context.mock(ITaskFactory.class);

        context.checking(new Expectations() {{
            Matcher matcher = instanceOf(ClassGeneratorBackedInstantiator.class);
            one(taskFactory).createChild(with(sameInstance(project)), with((Matcher<Instantiator>)matcher));
            will(returnValue(childFactory));
        }});

        assertThat(registry.getFactory(TaskContainerInternal.class), instanceOf(DefaultTaskContainerFactory.class));
    }

    @Test
    public void providesAPluginContainer() {
        expectScriptClassLoaderProviderCreated();
        context.checking(new Expectations() {{
            Matcher matcher = Matchers.instanceOf(DependencyInjectingInstantiator.class);
            one(pluginRegistry).createChild(with(notNullValue(ClassLoader.class)), with((Matcher<Instantiator>)matcher));
        }});

        assertThat(registry.get(PluginContainer.class), instanceOf(DefaultProjectsPluginContainer.class));
        assertThat(registry.get(PluginContainer.class), sameInstance(registry.get(PluginContainer.class)));
    }

    @Test
    public void providesAToolingModelRegistry() {
        assertThat(registry.get(ToolingModelBuilderRegistry.class), instanceOf(DefaultToolingModelBuilderRegistry.class));
        assertThat(registry.get(ToolingModelBuilderRegistry.class), sameInstance(registry.get(ToolingModelBuilderRegistry.class)));
    }

    @Test
    public void providesAnArtifactPublicationServicesFactory() {
        expectDependencyResolutionServicesCreated();

        assertThat(registry.get(ArtifactPublicationServices.class), sameInstance(publicationServices));
    }

    @Test
    public void providesARepositoryHandler() {
        expectDependencyResolutionServicesCreated();

        assertThat(registry.get(RepositoryHandler.class), sameInstance(repositoryHandler));
        assertThat(registry.get(RepositoryHandler.class), sameInstance(registry.get(RepositoryHandler.class)));
    }

    @Test
    public void providesAConfigurationContainer() {
        expectDependencyResolutionServicesCreated();

        assertThat(registry.get(ConfigurationContainerInternal.class), sameInstance(configurationContainer));
        assertThat(registry.get(ConfigurationContainerInternal.class), sameInstance(registry.get(ConfigurationContainerInternal.class)));
    }

    @Test
    public void providesAnArtifactHandler() {
        expectDependencyResolutionServicesCreated();

        assertThat(registry.get(ArtifactHandler.class), sameInstance(artifactHandler));
        assertThat(registry.get(ArtifactHandler.class), sameInstance(registry.get(ArtifactHandler.class)));
    }

    @Test
    public void providesADependencyHandler() {
        expectDependencyResolutionServicesCreated();

        assertThat(registry.get(DependencyHandler.class), sameInstance(dependencyHandler));
        assertThat(registry.get(DependencyHandler.class), sameInstance(registry.get(DependencyHandler.class)));
    }

    @Test
    public void providesAnAntBuilderFactory() {
        assertThat(registry.getFactory(AntBuilder.class), instanceOf(DefaultAntBuilderFactory.class));
        assertThat(registry.getFactory(AntBuilder.class), sameInstance((Object) registry.getFactory(AntBuilder.class)));
    }

    @Test
    public void providesAScriptHandlerAndScriptClassLoaderProvider() {
        expectScriptClassLoaderProviderCreated();

        assertThat(registry.get(ScriptHandler.class), instanceOf(DefaultScriptHandler.class));
        assertThat(registry.get(ScriptHandler.class), sameInstance(registry.get(ScriptHandler.class)));
        assertThat(registry.get(ScriptClassLoaderProvider.class), sameInstance((Object) registry.get(
                ScriptHandler.class)));
    }

    @Test
    public void providesAFileResolver() {
        assertThat(registry.get(FileResolver.class), instanceOf(BaseDirFileResolver.class));
        assertThat(registry.get(FileResolver.class), sameInstance(registry.get(FileResolver.class)));
    }

    @Test
    public void providesAFileOperationsInstance() {
        context.checking(new Expectations(){{
            one(project).getTasks();
        }});

        assertThat(registry.get(FileOperations.class), instanceOf(DefaultFileOperations.class));
        assertThat(registry.get(FileOperations.class), sameInstance(registry.get(FileOperations.class)));
    }
    
    @Test
    public void providesATemporaryFileProvider() {
        assertThat(registry.get(TemporaryFileProvider.class), instanceOf(DefaultTemporaryFileProvider.class));
        assertThat(registry.get(TemporaryFileProvider.class), sameInstance(registry.get(TemporaryFileProvider.class)));
    }
    
    @Test
    public void providesALoggingManager() {
        final Factory<LoggingManagerInternal> loggingManagerFactory = context.mock(Factory.class);
        final LoggingManager loggingManager = context.mock(LoggingManagerInternal.class);

        context.checking(new Expectations(){{
            allowing(parent).getFactory(LoggingManagerInternal.class);
            will(returnValue(loggingManagerFactory));
            one(loggingManagerFactory).create();
            will(returnValue(loggingManager));
        }});

        assertThat(registry.get(LoggingManager.class), sameInstance(loggingManager));
        assertThat(registry.get(LoggingManager.class), sameInstance(registry.get(LoggingManager.class)));
    }

    private void expectScriptClassLoaderProviderCreated() {
        context.checking(new Expectations() {{
            one(dependencyManagementServices).create(with(notNullValue(FileResolver.class)),
                    with(notNullValue(DependencyMetaDataProvider.class)),
                    with(notNullValue(ProjectFinder.class)),
                    with(notNullValue(DomainObjectContext.class)));
            will(returnValue(dependencyResolutionServices));

            ignoring(dependencyResolutionServices);

            allowing(project).getParent();
            will(returnValue(null));

            allowing(gradle).getScriptClassLoader();
            will(returnValue(null));
        }});
    }

    private void expectDependencyResolutionServicesCreated() {
        context.checking(new Expectations(){{
            one(dependencyManagementServices).create(with(notNullValue(FileResolver.class)),
                    with(notNullValue(DependencyMetaDataProvider.class)),
                    with(notNullValue(ProjectFinder.class)),
                    with(notNullValue(DomainObjectContext.class)));
            will(returnValue(dependencyResolutionServices));

            allowing(dependencyResolutionServices).getResolveRepositoryHandler();
            will(returnValue(repositoryHandler));

            allowing(dependencyResolutionServices).createArtifactPublicationServices();
            will(returnValue(publicationServices));

            allowing(dependencyResolutionServices).getConfigurationContainer();
            will(returnValue(configurationContainer));

            allowing(dependencyResolutionServices).getDependencyHandler();
            will(returnValue(dependencyHandler));

            allowing(dependencyResolutionServices).getArtifactHandler();
            will(returnValue(artifactHandler));
        }});
    }
}
