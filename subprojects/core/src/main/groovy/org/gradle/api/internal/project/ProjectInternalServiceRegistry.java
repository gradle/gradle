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
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultModule;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.SharedConventionRepositoryHandlerFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.file.*;
import org.gradle.api.internal.initialization.DefaultScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.internal.plugins.DefaultProjectsPluginContainer;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.ant.AntLoggingAdapter;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.tasks.DefaultTaskContainerFactory;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.logging.LoggingManagerInternal;

import java.io.File;

/**
 * Contains the services for a given project.
 */
public class ProjectInternalServiceRegistry extends DefaultServiceRegistry implements ServiceRegistryFactory {
    private final ProjectInternal project;

    public ProjectInternalServiceRegistry(ServiceRegistry parent, final ProjectInternal project) {
        super(parent);
        this.project = project;
    }

    protected PluginRegistry createPluginRegistry(PluginRegistry parentRegistry) {
        return parentRegistry.createChild(get(ScriptClassLoaderProvider.class).getClassLoader());
    }

    protected FileResolver createFileResolver() {
        return new BaseDirConverter(project.getProjectDir());
    }

    protected LoggingManagerInternal createLoggingManager() {
        return getFactory(LoggingManagerInternal.class).create();
    }

    protected FileOperations createFileOperations() {
        return new DefaultFileOperations(get(FileResolver.class), project.getTasks(), get(TemporaryFileProvider.class));
    }

    protected TemporaryFileProvider createTemporaryFileProvider() {
        return new DefaultTemporaryFileProvider(new FileSource() {
            public File get() {
                return new File(project.getBuildDir(), "tmp");
            }
        });
    }

    protected Factory<AntBuilder> createAntBuilderFactory() {
        return new DefaultAntBuilderFactory(new AntLoggingAdapter(), project);
    }

    protected PluginContainer createPluginContainer() {
        return new DefaultProjectsPluginContainer(get(PluginRegistry.class), project);
    }

    protected Factory<TaskContainerInternal> createTaskContainerInternal() {
        return new DefaultTaskContainerFactory(get(ClassGenerator.class), get(ITaskFactory.class), project);
    }

    protected Convention createConvention() {
        return new DefaultConvention();
    }

    protected Factory<RepositoryHandler> createRepositoryHandlerFactory(Factory<RepositoryHandler> factory) {
        return new SharedConventionRepositoryHandlerFactory(factory, get(Convention.class));
    }

    protected RepositoryHandler createRepositoryHandler() {
        return getFactory(RepositoryHandler.class).create();
    }
    
    protected ConfigurationContainer createConfigurationContainer() {
        return get(ConfigurationContainerFactory.class).createConfigurationContainer(project.getRepositories(),
                get(DependencyMetaDataProvider.class), project);
    }

    protected ArtifactHandler createArtifactHandler() {
        return new DefaultArtifactHandler(get(ConfigurationContainer.class), get(PublishArtifactFactory.class));
    }

    protected ProjectFinder createProjectFinder() {
        return new ProjectFinder() {
            public Project getProject(String path) {
                return project.project(path);
            }
        };
    }

    protected DependencyHandler createDependencyHandler() {
        return new DefaultDependencyHandler(get(ConfigurationContainer.class), get(DependencyFactory.class),
                get(ProjectFinder.class));
    }

    protected ScriptHandlerInternal createScriptHandler() {
        DefaultScriptHandlerFactory factory = new DefaultScriptHandlerFactory(
                getFactory(RepositoryHandler.class),
                get(ConfigurationContainerFactory.class),
                get(DependencyMetaDataProvider.class),
                get(DependencyFactory.class));
        ClassLoader parentClassLoader;
        if (project.getParent() != null) {
            parentClassLoader = project.getParent().getBuildscript().getClassLoader();
        } else {
            parentClassLoader = project.getGradle().getScriptClassLoader();
        }
        return factory.create(project.getBuildScriptSource(), parentClassLoader, project);
    }

    protected DependencyMetaDataProvider createDependencyMetaDataProvider() {
        return new DependencyMetaDataProvider() {
            public InternalRepository getInternalRepository() {
                return get(InternalRepository.class);
            }

            public File getGradleUserHomeDir() {
                return project.getGradle().getGradleUserHomeDir();
            }

            public Module getModule() {
                return new DefaultModule(project.getGroup().toString(), project.getName(), project.getVersion().toString(), project.getStatus().toString());
            }
        };
    }

    public ServiceRegistryFactory createFor(Object domainObject) {
        if (domainObject instanceof TaskInternal) {
            return new TaskInternalServiceRegistry(this, project, (TaskInternal)domainObject);
        }
        throw new UnsupportedOperationException();
    }

}
