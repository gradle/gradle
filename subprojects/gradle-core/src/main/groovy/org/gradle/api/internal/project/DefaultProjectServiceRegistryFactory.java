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
import org.gradle.api.artifacts.dsl.*;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.initialization.DefaultScriptHandler;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.internal.plugins.DefaultProjectsPluginContainer;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ProjectPluginsContainer;
import org.gradle.configuration.ProjectEvaluator;
import org.gradle.logging.AntLoggingAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// todo - compose this
public class DefaultProjectServiceRegistryFactory implements ProjectServiceRegistryFactory {
    private final ITaskFactory taskFactory;
    private final RepositoryHandlerFactory repositoryHandlerFactory;
    private final ConfigurationContainerFactory configurationContainerFactory;
    private final PublishArtifactFactory publishArtifactFactory;
    private final DependencyFactory dependencyFactory;
    private final ProjectEvaluator projectEvaluator;

    public DefaultProjectServiceRegistryFactory(RepositoryHandlerFactory repositoryHandlerFactory,
                                                ConfigurationContainerFactory configurationContainerFactory,
                                                PublishArtifactFactory publishArtifactFactory,
                                                DependencyFactory dependencyFactory,
                                                ProjectEvaluator projectEvaluator,
                                                ClassGenerator classGenerator) {
        this.repositoryHandlerFactory = repositoryHandlerFactory;
        this.configurationContainerFactory = configurationContainerFactory;
        this.publishArtifactFactory = publishArtifactFactory;
        this.dependencyFactory = dependencyFactory;
        this.projectEvaluator = projectEvaluator;
        taskFactory = new AnnotationProcessingTaskFactory(new TaskFactory(classGenerator));
    }

    public ProjectServiceRegistry create(ProjectInternal project) {
        return new ProjectServiceRegistryImpl(project);
    }

    private class ProjectServiceRegistryImpl implements ProjectServiceRegistry {
        private final ProjectInternal project;
        private final List<Service> services = new ArrayList<Service>();

        public ProjectServiceRegistryImpl(final ProjectInternal project) {
            this.project = project;

            services.add(new Service(ProjectEvaluator.class) {
                @Override
                protected Object create() {
                    return projectEvaluator;
                }
            });

            services.add(new Service(RepositoryHandlerFactory.class) {
                @Override
                protected Object create() {
                    return repositoryHandlerFactory;
                }
            });

            services.add(new Service(AntBuilderFactory.class) {
                @Override
                protected Object create() {
                    return new DefaultAntBuilderFactory(new AntLoggingAdapter(), project);
                }
            });

            services.add(new Service(ProjectPluginsContainer.class) {
                @Override
                protected Object create() {
                    return new DefaultProjectsPluginContainer(project.getGradle().getPluginRegistry());
                }
            });

            services.add(new Service(TaskContainerInternal.class) {
                @Override
                protected Object create() {
                    return new DefaultTaskContainer(project, taskFactory);
                }
            });

            services.add(new Service(Convention.class) {
                @Override
                protected Object create() {
                    return new DefaultConvention();
                }
            });

            services.add(new Service(RepositoryHandler.class) {
                @Override
                protected Object create() {
                    return repositoryHandlerFactory.createRepositoryHandler(get(Convention.class));
                }
            });

            services.add(new Service(ConfigurationHandler.class) {
                @Override
                protected Object create() {
                    return configurationContainerFactory.createConfigurationContainer(get(ResolverProvider.class),
                            new DependencyMetaDataProviderImpl());
                }
            });

            services.add(new Service(ArtifactHandler.class) {
                @Override
                protected Object create() {
                    return new DefaultArtifactHandler(get(ConfigurationContainer.class), publishArtifactFactory);
                }
            });

            services.add(new Service(ProjectFinder.class) {
                @Override
                protected Object create() {
                    return new ProjectFinder() {
                        public Project getProject(String path) {
                            return project.project(path);
                        }
                    };
                }
            });

            services.add(new Service(DependencyHandler.class) {
                @Override
                protected Object create() {
                    return new DefaultDependencyHandler(get(ConfigurationContainer.class), dependencyFactory, get(
                            ProjectFinder.class));
                }
            });

            services.add(new Service(ScriptHandler.class) {
                @Override
                protected Object create() {
                    RepositoryHandler repositoryHandler = repositoryHandlerFactory.createRepositoryHandler(
                            new DefaultConvention());
                    ConfigurationHandler configurationContainer = configurationContainerFactory
                            .createConfigurationContainer(repositoryHandler, new DependencyMetaDataProviderImpl());
                    DependencyHandler dependencyHandler = new DefaultDependencyHandler(configurationContainer,
                            dependencyFactory, get(ProjectFinder.class));
                    ClassLoader parentClassLoader;
                    if (project.getParent() != null) {
                        parentClassLoader = project.getParent().getClassLoaderProvider().getClassLoader();
                    } else {
                        parentClassLoader = project.getGradle().getBuildScriptClassLoader();
                    }
                    return new DefaultScriptHandler(repositoryHandler, dependencyHandler,
                            configurationContainer, parentClassLoader);
                }
            });

            services.add(new Service(ScriptClassLoaderProvider.class) {
                @Override
                protected Object create() {
                    return get(ScriptHandler.class);
                }
            });
        }

        public <T> T get(Class<T> serviceType) throws IllegalArgumentException {
            for (Service service : services) {
                T t = service.getService(serviceType);
                if (t != null) {
                    return t;
                }
            }

            throw new IllegalArgumentException(String.format("No project service of type %s available.",
                    serviceType.getSimpleName()));
        }

        private abstract class Service {
            final Class<?> serviceType;
            Object service;

            Service(Class<?> serviceType) {
                this.serviceType = serviceType;
            }

            <T> T getService(Class<T> serviceType) {
                if (!serviceType.isAssignableFrom(this.serviceType)) {
                    return null;
                }
                if (service == null) {
                    service = create();
                    assert service != null;
                }
                return serviceType.cast(service);
            }

            protected abstract Object create();
        }

        private class DependencyMetaDataProviderImpl implements DependencyMetaDataProvider {
            public InternalRepository getInternalRepository() {
                return project.getGradle().getInternalRepository();
            }

            public File getGradleUserHomeDir() {
                return project.getGradle().getGradleUserHomeDir();
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
