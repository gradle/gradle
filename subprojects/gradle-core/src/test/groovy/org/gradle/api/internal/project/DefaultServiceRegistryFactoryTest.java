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

import org.gradle.api.artifacts.dsl.*;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.initialization.DefaultScriptHandler;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.configuration.ProjectEvaluator;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultServiceRegistryFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ConfigurationContainerFactory configurationContainerFactory = context.mock(
            ConfigurationContainerFactory.class);
    private final RepositoryHandlerFactory repositoryHandlerFactory = context.mock(RepositoryHandlerFactory.class);
    private final DependencyFactory dependencyFactory = context.mock(DependencyFactory.class);
    private final PublishArtifactFactory publishArtifactFactory = context.mock(PublishArtifactFactory.class);
    private final ProjectEvaluator projectEvaluator = context.mock(ProjectEvaluator.class);

    private final DefaultServiceRegistryFactory factory = new DefaultServiceRegistryFactory(
            repositoryHandlerFactory, configurationContainerFactory, publishArtifactFactory, dependencyFactory,
            projectEvaluator, context.mock(ClassGenerator.class));
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final ConfigurationHandler configurationHandler = context.mock(ConfigurationHandler.class);

    @Test
    public void projectProvidesAConvention() {
        ServiceRegistry registry = factory.createForProject(project);
        assertThat(registry.get(Convention.class), instanceOf(DefaultConvention.class));
        assertThat(registry.get(Convention.class), sameInstance(registry.get(Convention.class)));
    }

    @Test
    public void projectProvidesATaskContainer() {
        ServiceRegistry registry = factory.createForProject(project);
        assertThat(registry.get(TaskContainer.class), instanceOf(DefaultTaskContainer.class));
        assertThat(registry.get(TaskContainer.class), sameInstance(registry.get(TaskContainer.class)));
    }

    @Test
    public void projectProvidesARepositoryHandler() {
        final RepositoryHandler repositoryHandler = context.mock(RepositoryHandler.class);

        context.checking(new Expectations() {{
            one(repositoryHandlerFactory).createRepositoryHandler(with(any(Convention.class)));
            will(returnValue(repositoryHandler));
        }});

        ServiceRegistry registry = factory.createForProject(project);
        assertThat(registry.get(RepositoryHandler.class), sameInstance(repositoryHandler));
        assertThat(registry.get(RepositoryHandler.class), sameInstance(registry.get(RepositoryHandler.class)));
    }

    @Test
    public void projectProvidesARepositoryHandlerFactory() {
        ServiceRegistry registry = factory.createForProject(project);
        assertThat(registry.get(RepositoryHandlerFactory.class), sameInstance(repositoryHandlerFactory));
        assertThat(registry.get(RepositoryHandlerFactory.class), sameInstance(registry.get(
                RepositoryHandlerFactory.class)));
    }

    @Test
    public void projectProvidesAConfigurationHandler() {
        ServiceRegistry registry = factory.createForProject(project);

        expectConfigurationHandlerCreated();

        assertThat(registry.get(ConfigurationHandler.class), sameInstance(configurationHandler));
        assertThat(registry.get(ConfigurationHandler.class), sameInstance(registry.get(ConfigurationHandler.class)));
    }

    @Test
    public void projectProvidesAnArtifactHandler() {
        ServiceRegistry registry = factory.createForProject(project);

        expectConfigurationHandlerCreated();

        assertThat(registry.get(ArtifactHandler.class), instanceOf(DefaultArtifactHandler.class));
        assertThat(registry.get(ArtifactHandler.class), sameInstance(registry.get(ArtifactHandler.class)));
    }

    @Test
    public void projectProvidesADependencyHandler() {
        ServiceRegistry registry = factory.createForProject(project);

        expectConfigurationHandlerCreated();

        assertThat(registry.get(DependencyHandler.class), instanceOf(DefaultDependencyHandler.class));
        assertThat(registry.get(DependencyHandler.class), sameInstance(registry.get(DependencyHandler.class)));
    }

    @Test
    public void projectProvidesAnAntBuilderFactory() {
        ServiceRegistry registry = factory.createForProject(project);

        assertThat(registry.get(AntBuilderFactory.class), instanceOf(DefaultAntBuilderFactory.class));
        assertThat(registry.get(AntBuilderFactory.class), sameInstance(registry.get(AntBuilderFactory.class)));
    }

    @Test
    public void projectProvidesAProjectEvaluator() {
        ServiceRegistry registry = factory.createForProject(project);

        assertThat(registry.get(ProjectEvaluator.class), sameInstance(projectEvaluator));
        assertThat(registry.get(ProjectEvaluator.class), sameInstance(registry.get(ProjectEvaluator.class)));
    }

    @Test
    public void projectProvidesAScriptHandlerAndScriptClassLoaderProvider() {
        expectConfigurationHandlerCreated();
        context.checking(new Expectations(){{
            allowing(project).getParent();
            will(returnValue(null));
            
            allowing(project).getGradle();
            will(returnValue(gradle));

            allowing(gradle).getBuildScriptClassLoader();
            will(returnValue(null));

            ignoring(configurationHandler);
        }});

        ServiceRegistry registry = factory.createForProject(project);

        assertThat(registry.get(ScriptHandler.class), instanceOf(DefaultScriptHandler.class));
        assertThat(registry.get(ScriptHandler.class), sameInstance(registry.get(
                ScriptHandler.class)));
        assertThat(registry.get(ScriptClassLoaderProvider.class), sameInstance((Object) registry.get(ScriptHandler.class)));
    }

    @Test
    public void buildProvidesAScriptHandlerAndScriptClassLoaderProvider() {
        expectConfigurationHandlerCreated();
        context.checking(new Expectations(){{
            allowing(project).getParent();
            will(returnValue(null));

            allowing(project).getGradle();
            will(returnValue(gradle));

            allowing(gradle).getBuildScriptClassLoader();
            will(returnValue(null));

            ignoring(configurationHandler);
        }});

        ServiceRegistry registry = factory.createForBuild(gradle);

        assertThat(registry.get(ScriptHandler.class), instanceOf(DefaultScriptHandler.class));
        assertThat(registry.get(ScriptHandler.class), sameInstance(registry.get(
                ScriptHandler.class)));
        assertThat(registry.get(ScriptClassLoaderProvider.class), sameInstance((Object) registry.get(ScriptHandler.class)));
    }

    @Test
    public void projectRegistryThrowsExceptionForUnknownService() {
        try {
            factory.createForProject(project).get(String.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No service of type String available."));
        }
    }

    @Test
    public void buildRegistryThrowsExceptionForUnknownService() {
        try {
            factory.createForBuild(gradle).get(String.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No service of type String available."));
        }
    }

    private void expectConfigurationHandlerCreated() {
        context.checking(new Expectations() {{
            RepositoryHandler repositoryHandler = context.mock(RepositoryHandler.class);

            one(repositoryHandlerFactory).createRepositoryHandler(with(notNullValue(Convention.class)));
            will(returnValue(repositoryHandler));

            one(configurationContainerFactory).createConfigurationContainer(with(sameInstance(repositoryHandler)), with(
                    notNullValue(DependencyMetaDataProvider.class)));
            will(returnValue(configurationHandler));
        }});
    }
}
