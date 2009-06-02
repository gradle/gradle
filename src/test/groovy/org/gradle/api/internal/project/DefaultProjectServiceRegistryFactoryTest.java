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

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ProjectDependenciesBuildInstruction;
import org.gradle.api.artifacts.dsl.*;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.invocation.Build;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultProjectServiceRegistryFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ConfigurationContainerFactory configurationContainerFactory = context.mock(ConfigurationContainerFactory.class);
    private final RepositoryHandlerFactory repositoryHandlerFactory = context.mock(RepositoryHandlerFactory.class);
    private final DependencyFactory dependencyFactory = context.mock(DependencyFactory.class);
    private final PublishArtifactFactory publishArtifactFactory = context.mock(PublishArtifactFactory.class);
    private final ProjectDependenciesBuildInstruction instruction = new ProjectDependenciesBuildInstruction(WrapUtil.toList("task"));

    private final DefaultProjectServiceRegistryFactory factory = new DefaultProjectServiceRegistryFactory(
            repositoryHandlerFactory, configurationContainerFactory, publishArtifactFactory, dependencyFactory);
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final ConfigurationHandler configurationHandler = context.mock(ConfigurationHandler.class);

    @Test
    public void providesAConvention() {
        ProjectServiceRegistry registry = factory.create(project);
        assertThat(registry.get(Convention.class), instanceOf(DefaultConvention.class));
        assertThat(registry.get(Convention.class), sameInstance(registry.get(Convention.class)));
    }

    @Test
    public void providesATaskContainer() {
        ProjectServiceRegistry registry = factory.create(project);
        assertThat(registry.get(TaskContainer.class), instanceOf(DefaultTaskContainer.class));
        assertThat(registry.get(TaskContainer.class), sameInstance(registry.get(TaskContainer.class)));
    }

    @Test
    public void providesARepositoryHandler() {
        final RepositoryHandler repositoryHandler = context.mock(RepositoryHandler.class);

        context.checking(new Expectations() {{
            one(repositoryHandlerFactory).createRepositoryHandler(with(any(Convention.class)));
            will(returnValue(repositoryHandler));
        }});

        ProjectServiceRegistry registry = factory.create(project);
        assertThat(registry.get(RepositoryHandler.class), sameInstance(repositoryHandler));
        assertThat(registry.get(RepositoryHandler.class), sameInstance(registry.get(RepositoryHandler.class)));
    }

    @Test
    public void providesAConfigurationHandler() {
        ProjectServiceRegistry registry = factory.create(project);

        expectConfigurationHandlerCreated();

        assertThat(registry.get(ConfigurationHandler.class), sameInstance(configurationHandler));
        assertThat(registry.get(ConfigurationHandler.class), sameInstance(registry.get(ConfigurationHandler.class)));
    }

    @Test
    public void providesAnArtifactHandler() {
        ProjectServiceRegistry registry = factory.create(project);

        expectConfigurationHandlerCreated();

        assertThat(registry.get(ArtifactHandler.class), instanceOf(DefaultArtifactHandler.class));
        assertThat(registry.get(ArtifactHandler.class), sameInstance(registry.get(ArtifactHandler.class)));
    }

    @Test
    public void providesADependencyHandler() {
        ProjectServiceRegistry registry = factory.create(project);

        expectConfigurationHandlerCreated();
        
        assertThat(registry.get(DependencyHandler.class), instanceOf(DefaultDependencyHandler.class));
        assertThat(registry.get(DependencyHandler.class), sameInstance(registry.get(DependencyHandler.class)));
    }

    @Test
    public void registryThrowsExceptionForUnknownService() {
        try {
            factory.create(project).get(String.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No project service of type String available."));
        }
    }

    private void expectConfigurationHandlerCreated() {
        context.checking(new Expectations(){{
            one(configurationContainerFactory).createConfigurationContainer(with(any(ResolverProvider.class)), with(any(
                    DependencyMetaDataProvider.class)));
            will(returnValue(configurationHandler));
        }});
    }
}
