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

import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultModule;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.PublishModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.repositories.DefaultInternalRepository;
import org.gradle.api.internal.initialization.DefaultScriptHandler;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.execution.DefaultTaskGraphExecuter;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.listener.ListenerManager;

import java.io.File;

/**
 * Contains the services for a given build.
 */
public class GradleInternalServiceRegistry extends AbstractServiceRegistry implements ServiceRegistryFactory {
    private final GradleInternal gradle;

    public GradleInternalServiceRegistry(ServiceRegistry parent, final GradleInternal gradle) {
        super(parent);
        this.gradle = gradle;
    }

    protected ProjectFinder createProjectFinder() {
        return new ProjectFinder() {
            public Project getProject(String path) {
                return gradle.getRootProject().project(path);
            }
        };
    }

    protected IProjectRegistry createIProjectRegistry() {
        return new DefaultProjectRegistry<ProjectInternal>();
    }

    protected TaskGraphExecuter createTaskGraphExecuter() {
        return new DefaultTaskGraphExecuter(get(ListenerManager.class));
    }

    protected PluginRegistry createPluginRegistry() {
        return new DefaultPluginRegistry(gradle.getStartParameter().getPluginPropertiesFile());
    }

    protected DefaultScriptHandler createDefaultScriptHandler() {
        RepositoryHandler repositoryHandler = get(RepositoryHandlerFactory.class).createRepositoryHandler(
                new DefaultConvention());
        ConfigurationContainer configurationContainer = get(ConfigurationContainerFactory.class)
                .createConfigurationContainer(repositoryHandler, new DependencyMetaDataProviderImpl());
        DependencyHandler dependencyHandler = new DefaultDependencyHandler(configurationContainer, get(
                DependencyFactory.class), get(ProjectFinder.class));
        return new DefaultScriptHandler(repositoryHandler, dependencyHandler, configurationContainer,
                Thread.currentThread().getContextClassLoader());
    }

    protected InternalRepository createInternalRepository() {
        return new DefaultInternalRepository(gradle, get(PublishModuleDescriptorConverter.class));
    }

    public ServiceRegistryFactory createFor(Object domainObject) {
        if (domainObject instanceof ProjectInternal) {
            return new ProjectInternalServiceRegistry(this, (ProjectInternal) domainObject);
        }
        throw new UnsupportedOperationException();
    }

    private class DependencyMetaDataProviderImpl implements DependencyMetaDataProvider {
        public InternalRepository getInternalRepository() {
            return get(InternalRepository.class);
        }

        public File getGradleUserHomeDir() {
            return gradle.getGradleUserHomeDir();
        }

        public Module getModuleForPublicDescriptor() {
            return new DefaultModule(Project.DEFAULT_GROUP, "unspecified", Project.DEFAULT_VERSION, Project.DEFAULT_STATUS);
        }

        public Module getModuleForResolve() {
            return new DefaultModule(Project.DEFAULT_GROUP, "unspecified", Project.DEFAULT_VERSION, Project.DEFAULT_STATUS);
        }
    }
}
