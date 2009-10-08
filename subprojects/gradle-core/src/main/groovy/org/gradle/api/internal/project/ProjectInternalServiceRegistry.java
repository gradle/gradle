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
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.initialization.dsl.ScriptHandler;
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
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.ant.AntLoggingAdapter;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ProjectPluginsContainer;

import java.io.File;

/**
 * Contains the services for a given project.
 */
public class ProjectInternalServiceRegistry extends AbstractServiceRegistry implements ServiceRegistryFactory {
    private final ProjectInternal project;

    public ProjectInternalServiceRegistry(ServiceRegistry parent, final ProjectInternal project) {
        super(parent);
        this.project = project;

        add(new Service(AntBuilderFactory.class) {
            @Override
            protected Object create() {
                return new DefaultAntBuilderFactory(new AntLoggingAdapter(), project);
            }
        });

        add(new Service(ProjectPluginsContainer.class) {
            @Override
            protected Object create() {
                return new DefaultProjectsPluginContainer(get(PluginRegistry.class));
            }
        });

        add(new Service(TaskContainerInternal.class) {
            @Override
            protected Object create() {
                return new DefaultTaskContainer(project, get(ITaskFactory.class));
            }
        });

        add(new Service(Convention.class) {
            @Override
            protected Object create() {
                return new DefaultConvention();
            }
        });

        add(new Service(RepositoryHandler.class) {
            @Override
            protected Object create() {
                return get(RepositoryHandlerFactory.class).createRepositoryHandler(get(Convention.class));
            }
        });

        add(new Service(ConfigurationContainer.class) {
            @Override
            protected Object create() {
                return get(ConfigurationContainerFactory.class).createConfigurationContainer(get(ResolverProvider.class),
                        new DependencyMetaDataProviderImpl());
            }
        });

        add(new Service(ArtifactHandler.class) {
            @Override
            protected Object create() {
                return new DefaultArtifactHandler(get(ConfigurationContainer.class), get(PublishArtifactFactory.class));
            }
        });

        add(new Service(ProjectFinder.class) {
            @Override
            protected Object create() {
                return new ProjectFinder() {
                    public Project getProject(String path) {
                        return project.project(path);
                    }
                };
            }
        });

        add(new Service(DependencyHandler.class) {
            @Override
            protected Object create() {
                return new DefaultDependencyHandler(get(ConfigurationContainer.class), get(DependencyFactory.class),
                        get(ProjectFinder.class));
            }
        });

        add(new Service(ScriptHandler.class) {
            @Override
            protected Object create() {
                RepositoryHandler repositoryHandler = get(RepositoryHandlerFactory.class).createRepositoryHandler(
                        new DefaultConvention());
                ConfigurationContainer configurationContainer = get(ConfigurationContainerFactory.class)
                        .createConfigurationContainer(repositoryHandler, new DependencyMetaDataProviderImpl());
                DependencyHandler dependencyHandler = new DefaultDependencyHandler(configurationContainer,
                        get(DependencyFactory.class), get(ProjectFinder.class));
                ClassLoader parentClassLoader;
                if (project.getParent() != null) {
                    parentClassLoader = project.getParent().getClassLoaderProvider().getClassLoader();
                } else {
                    parentClassLoader = project.getGradle().getBuildScriptClassLoader();
                }
                return new DefaultScriptHandler(repositoryHandler, dependencyHandler, configurationContainer,
                        parentClassLoader);
            }
        });

        add(new Service(ScriptClassLoaderProvider.class) {
            @Override
            protected Object create() {
                return get(ScriptHandler.class);
            }
        });
    }

    public ServiceRegistryFactory createFor(Object domainObject) {
        if (domainObject instanceof TaskInternal) {
            return new TaskInternalServiceRegistry(this, project);
        }
        throw new UnsupportedOperationException();
    }

    private class DependencyMetaDataProviderImpl implements DependencyMetaDataProvider {
        public InternalRepository getInternalRepository() {
            return get(InternalRepository.class);
        }

        public File getGradleUserHomeDir() {
            return project.getGradle().getGradleUserHomeDir();
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
