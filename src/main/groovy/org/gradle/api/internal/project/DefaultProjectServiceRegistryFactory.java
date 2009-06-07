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

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.dsl.*;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.plugins.Convention;
import org.gradle.configuration.ProjectEvaluator;
import org.gradle.logging.AntLoggingAdapter;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// todo - compose this
public class DefaultProjectServiceRegistryFactory implements ProjectServiceRegistryFactory {
    private final ITaskFactory taskFactory = new TaskFactory();
    private final RepositoryHandlerFactory repositoryHandlerFactory;
    private final ConfigurationContainerFactory configurationContainerFactory;
    private final PublishArtifactFactory publishArtifactFactory;
    private final DependencyFactory dependencyFactory;
    private final ProjectEvaluator projectEvaluator;

    public DefaultProjectServiceRegistryFactory(RepositoryHandlerFactory repositoryHandlerFactory,
                                                ConfigurationContainerFactory configurationContainerFactory,
                                                PublishArtifactFactory publishArtifactFactory,
                                                DependencyFactory dependencyFactory,
                                                ProjectEvaluator projectEvaluator) {
        this.repositoryHandlerFactory = repositoryHandlerFactory;
        this.configurationContainerFactory = configurationContainerFactory;
        this.publishArtifactFactory = publishArtifactFactory;
        this.dependencyFactory = dependencyFactory;
        this.projectEvaluator = projectEvaluator;
    }

    public ProjectServiceRegistry create(ProjectInternal project) {
        return new ProjectServiceRegistryImpl(project);
    }

    private class ProjectServiceRegistryImpl implements ProjectServiceRegistry {
        private final ProjectInternal project;
        private DefaultConvention convention;
        private DefaultTaskContainer taskContainer;
        private RepositoryHandler repositoryHandler;
        private ConfigurationContainer configurationContainer;
        private ArtifactHandler artifactHandler;
        private DependencyHandler dependencyHandler;
        private final DefaultAntBuilderFactory antBuilderFactory;

        public ProjectServiceRegistryImpl(ProjectInternal project) {
            this.project = project;
            antBuilderFactory = new DefaultAntBuilderFactory(new AntLoggingAdapter(), project);
        }

        public <T> T get(Class<T> serviceType) throws IllegalArgumentException {
            if (serviceType.isAssignableFrom(Convention.class)) {
                if (convention == null) {
                    convention = new DefaultConvention();
                }
                return serviceType.cast(convention);
            }
            if (serviceType.isAssignableFrom(TaskContainerInternal.class)) {
                if (taskContainer == null) {
                    taskContainer = new DefaultTaskContainer(this.project, taskFactory);
                }
                return serviceType.cast(taskContainer);
            }
            if (serviceType.isAssignableFrom(RepositoryHandler.class)) {
                if (repositoryHandler == null) {
                    repositoryHandler = repositoryHandlerFactory.createRepositoryHandler(get(Convention.class));
                }
                return serviceType.cast(repositoryHandler);
            }
            if (serviceType.isAssignableFrom(ConfigurationHandler.class)) {
                if (configurationContainer == null) {
                    configurationContainer = configurationContainerFactory.createConfigurationContainer(new ResolverProviderImpl(),
                            new DependencyMetaDataProviderImpl());
                }
                return serviceType.cast(configurationContainer);
            }
            if (serviceType.isAssignableFrom(ArtifactHandler.class)) {
                if (artifactHandler == null) {
                    artifactHandler = new DefaultArtifactHandler(get(ConfigurationContainer.class),
                            publishArtifactFactory);
                }
                return serviceType.cast(artifactHandler);
            }
            if (serviceType.isAssignableFrom(DependencyHandler.class)) {
                if (dependencyHandler == null) {
                    ProjectFinder projectFinder = new ProjectFinder() {
                        public Project getProject(String path) {
                            return project.project(path);
                        }
                    };
                    dependencyHandler = new DefaultDependencyHandler(get(ConfigurationContainer.class), dependencyFactory, projectFinder);
                }
                return serviceType.cast(dependencyHandler);
            }
            if (serviceType.isAssignableFrom(AntBuilderFactory.class)) {
                return serviceType.cast(antBuilderFactory);
            }
            if (serviceType.isAssignableFrom(ProjectEvaluator.class)) {
                return serviceType.cast(projectEvaluator);
            }
            if (serviceType.isAssignableFrom(RepositoryHandlerFactory.class)) {
                return serviceType.cast(repositoryHandlerFactory);
            }
            throw new IllegalArgumentException(String.format("No project service of type %s available.",
                    serviceType.getSimpleName()));
        }

        private class ResolverProviderImpl implements ResolverProvider {
            public List<DependencyResolver> getResolvers() {
                return get(RepositoryHandler.class).getResolvers();
            }
        }

        private class DependencyMetaDataProviderImpl implements DependencyMetaDataProvider {
            public InternalRepository getInternalRepository() {
                return project.getBuild().getInternalRepository();
            }

            public File getGradleUserHomeDir() {
                return project.getBuild().getGradleUserHomeDir();
            }

            public Map getClientModuleRegistry() {
                return new HashMap();
            }

            public Module getModule() {
                return new Module() {
                    public String getGroup() {
                        return project.getGroup().toString();
                    }

                    public String getName() {
                        return project.getName();
                    }

                    public String getVersion() {
                        return project.getVersion().toString();
                    }

                    public String getStatus() {
                        return project.getStatus().toString();
                    }
                };
            }
        }
    }
}
