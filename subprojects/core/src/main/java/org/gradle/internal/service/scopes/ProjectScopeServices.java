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

package org.gradle.internal.service.scopes;

import org.gradle.api.Action;
import org.gradle.api.AntBuilder;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.MutationGuards;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.ProjectBackedModule;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.collections.DefaultDomainObjectCollectionFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.component.ComponentRegistry;
import org.gradle.api.internal.component.DefaultSoftwareComponentContainer;
import org.gradle.api.internal.file.BaseDirFileResolver;
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.internal.file.DefaultFileOperations;
import org.gradle.api.internal.file.DefaultProjectLayout;
import org.gradle.api.internal.file.DefaultSourceDirectorySetFactory;
import org.gradle.api.internal.file.DefaultTemporaryFileProvider;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.initialization.DefaultScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptClassPathResolver;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.model.DefaultObjectFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.plugins.DefaultPluginManager;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.plugins.PluginTarget;
import org.gradle.api.internal.plugins.RuleBasedPluginTarget;
import org.gradle.api.internal.project.CrossProjectConfigurator;
import org.gradle.api.internal.project.DefaultAntBuilderFactory;
import org.gradle.api.internal.project.DeferredProjectConfiguration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.project.ant.DefaultAntLoggingAdapterFactory;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.taskfactory.TaskInstantiator;
import org.gradle.api.internal.tasks.DefaultTaskContainerFactory;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.internal.tasks.TaskStatistics;
import org.gradle.api.internal.tasks.properties.TaskScheme;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.configuration.project.DefaultProjectConfigurationActionContainer;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Factory;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.TextResourceLoader;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.typeconversion.DefaultTypeConverter;
import org.gradle.internal.typeconversion.TypeConverter;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.model.internal.registry.DefaultModelRegistry;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.normalization.internal.DefaultInputNormalizationHandler;
import org.gradle.normalization.internal.DefaultRuntimeClasspathNormalization;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;
import org.gradle.normalization.internal.RuntimeClasspathNormalizationInternal;
import org.gradle.process.internal.ExecFactory;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.internal.DefaultToolingModelBuilderRegistry;
import org.gradle.util.Path;

import java.io.File;
import java.util.function.Supplier;

/**
 * Contains the services for a given project.
 */
public class ProjectScopeServices extends DefaultServiceRegistry {
    private final ProjectInternal project;
    private final Factory<LoggingManagerInternal> loggingManagerInternalFactory;

    public ProjectScopeServices(final ServiceRegistry parent, final ProjectInternal project, Factory<LoggingManagerInternal> loggingManagerInternalFactory) {
        super(parent);
        this.project = project;
        this.loggingManagerInternalFactory = loggingManagerInternalFactory;
        register(new Action<ServiceRegistration>() {
            @Override
            public void execute(ServiceRegistration registration) {
                registration.add(DomainObjectContext.class, project);
                parent.get(DependencyManagementServices.class).addDslServices(registration, project);
                for (PluginServiceRegistry pluginServiceRegistry : parent.getAll(PluginServiceRegistry.class)) {
                    pluginServiceRegistry.registerProjectServices(registration);
                }
            }
        });
    }

    protected PluginRegistry createPluginRegistry(PluginRegistry rootRegistry) {
        PluginRegistry parentRegistry;
        if (project.getParent() == null) {
            parentRegistry = rootRegistry.createChild(project.getBaseClassLoaderScope());
        } else {
            parentRegistry = project.getParent().getServices().get(PluginRegistry.class);
        }
        return parentRegistry.createChild(project.getClassLoaderScope());
    }

    protected DeferredProjectConfiguration createDeferredProjectConfiguration() {
        return new DeferredProjectConfiguration(project);
    }

    protected FileResolver createFileResolver(Factory<PatternSet> patternSetFactory) {
        return new BaseDirFileResolver(project.getProjectDir(), patternSetFactory);
    }

    protected SourceDirectorySetFactory createSourceDirectorySetFactory(ObjectFactory objectFactory) {
        return new DefaultSourceDirectorySetFactory(objectFactory);
    }

    protected LoggingManagerInternal createLoggingManager() {
        return loggingManagerInternalFactory.create();
    }

    protected ProjectConfigurationActionContainer createProjectConfigurationActionContainer() {
        return new DefaultProjectConfigurationActionContainer();
    }

    protected DefaultFileOperations createFileOperations(FileResolver fileResolver, TemporaryFileProvider temporaryFileProvider, Instantiator instantiator, FileLookup fileLookup, DirectoryFileTreeFactory directoryFileTreeFactory, StreamHasher streamHasher, FileHasher fileHasher, TextResourceLoader textResourceLoader, FileCollectionFactory fileCollectionFactory, FileSystem fileSystem, Clock clock) {
        return new DefaultFileOperations(fileResolver, project.getTasks(), temporaryFileProvider, instantiator, fileLookup, directoryFileTreeFactory, streamHasher, fileHasher, textResourceLoader, fileCollectionFactory, fileSystem, clock);
    }

    protected ExecFactory decorateExecFactory(ExecFactory execFactory, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, InstantiatorFactory instantiatorFactory) {
        return execFactory.forContext(fileResolver, fileCollectionFactory, instantiatorFactory.decorateLenient());
    }

    protected TemporaryFileProvider createTemporaryFileProvider() {
        return new DefaultTemporaryFileProvider(new Factory<File>() {
            @Override
            public File create() {
                return new File(project.getBuildDir(), "tmp");
            }
        });
    }

    protected Factory<AntBuilder> createAntBuilderFactory() {
        return new DefaultAntBuilderFactory(project, new DefaultAntLoggingAdapterFactory());
    }

    protected ToolingModelBuilderRegistry decorateToolingModelRegistry(ToolingModelBuilderRegistry buildScopedToolingModelBuilders, BuildOperationExecutor buildOperationExecutor, ProjectStateRegistry projectStateRegistry) {
        return new DefaultToolingModelBuilderRegistry(buildOperationExecutor, projectStateRegistry, buildScopedToolingModelBuilders);
    }

    protected PluginManagerInternal createPluginManager(Instantiator instantiator, InstantiatorFactory instantiatorFactory, BuildOperationExecutor buildOperationExecutor, UserCodeApplicationContext userCodeApplicationContext, CollectionCallbackActionDecorator decorator, DomainObjectCollectionFactory domainObjectCollectionFactory) {
        PluginTarget target = new RuleBasedPluginTarget(
            project,
            get(ModelRuleExtractor.class),
            get(ModelRuleSourceDetector.class)
        );
        return instantiator.newInstance(DefaultPluginManager.class, get(PluginRegistry.class), instantiatorFactory.inject(this), target, buildOperationExecutor, userCodeApplicationContext, decorator, domainObjectCollectionFactory);
    }

    protected ITaskFactory createTaskFactory(ITaskFactory parentFactory, TaskScheme taskScheme) {
        return parentFactory.createChild(project, taskScheme.getInstantiationScheme().withServices(this));
    }

    protected TaskInstantiator createTaskInstantiator(ITaskFactory taskFactory) {
        return new TaskInstantiator(taskFactory, project);
    }

    protected TaskContainerInternal createTaskContainerInternal(TaskStatistics taskStatistics, BuildOperationExecutor buildOperationExecutor, CrossProjectConfigurator crossProjectConfigurator, CollectionCallbackActionDecorator decorator) {
        return new DefaultTaskContainerFactory(get(ModelRegistry.class), get(Instantiator.class), get(ITaskFactory.class), project, get(ProjectAccessListener.class), taskStatistics, buildOperationExecutor, crossProjectConfigurator, decorator).create();
    }

    protected SoftwareComponentContainer createSoftwareComponentContainer(CollectionCallbackActionDecorator decorator) {
        Instantiator instantiator = get(Instantiator.class);
        return instantiator.newInstance(DefaultSoftwareComponentContainer.class, instantiator, decorator);
    }

    protected ProjectFinder createProjectFinder(final BuildStateRegistry buildStateRegistry) {
        return new DefaultProjectFinder(buildStateRegistry, new Supplier<ProjectInternal>() {
            @Override
            public ProjectInternal get() {
                return project;
            }
        });
    }

    protected ModelRegistry createModelRegistry(ModelRuleExtractor ruleExtractor) {
        return new DefaultModelRegistry(ruleExtractor, project.getPath());
    }

    protected ScriptHandlerInternal createScriptHandler() {
        ScriptHandlerFactory factory = new DefaultScriptHandlerFactory(
            get(DependencyManagementServices.class),
            get(FileResolver.class),
            get(DependencyMetaDataProvider.class),
            get(ScriptClassPathResolver.class));
        return factory.create(project.getBuildScriptSource(), project.getClassLoaderScope(), new ScriptScopedContext(project));
    }

    private class ScriptScopedContext implements DomainObjectContext {
        private final DomainObjectContext delegate;

        public ScriptScopedContext(DomainObjectContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public Path identityPath(String name) {
            return delegate.identityPath(name);
        }

        @Override
        public Path projectPath(String name) {
            return delegate.projectPath(name);
        }

        @Override
        public Path getProjectPath() {
            return delegate.getProjectPath();
        }

        @Override
        public Path getBuildPath() {
            return delegate.getBuildPath();
        }

        @Override
        public boolean isScript() {
            return true;
        }
    }

    protected DependencyMetaDataProvider createDependencyMetaDataProvider() {
        return new ProjectBackedModuleMetaDataProvider();
    }

    protected ServiceRegistryFactory createServiceRegistryFactory(final ServiceRegistry services) {
        return new ServiceRegistryFactory() {
            @Override
            public ServiceRegistry createFor(Object domainObject) {
                throw new UnsupportedOperationException();
            }
        };
    }

    protected ComponentRegistry createComponentRegistry() {
        return new ComponentRegistry();
    }

    protected TypeConverter createTypeConverter(PathToFileResolver fileResolver) {
        return new DefaultTypeConverter(fileResolver);
    }

    private class ProjectBackedModuleMetaDataProvider implements DependencyMetaDataProvider {
        @Override
        public Module getModule() {
            return new ProjectBackedModule(project);
        }
    }

    protected RuntimeClasspathNormalizationInternal createRuntimeClasspathNormalizationStrategy(Instantiator instantiator) {
        return instantiator.newInstance(DefaultRuntimeClasspathNormalization.class);
    }

    protected InputNormalizationHandlerInternal createInputNormalizationHandler(Instantiator instantiator, RuntimeClasspathNormalizationInternal runtimeClasspathNormalizationStrategy) {
        return instantiator.newInstance(DefaultInputNormalizationHandler.class, runtimeClasspathNormalizationStrategy);
    }

    protected DefaultProjectLayout createProjectLayout(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory) {
        return new DefaultProjectLayout(project.getProjectDir(), fileResolver, project.getTasks(), fileCollectionFactory);
    }

    protected ConfigurationTargetIdentifier createConfigurationTargetIdentifier() {
        return ConfigurationTargetIdentifier.of(project);
    }

    protected FileCollectionFactory createFileCollectionFactory(PathToFileResolver fileResolver, TaskResolver taskResolver) {
        return new DefaultFileCollectionFactory(fileResolver, taskResolver);
    }

    protected ObjectFactory createObjectFactory(InstantiatorFactory instantiatorFactory, FileResolver fileResolver, DirectoryFileTreeFactory directoryFileTreeFactory, FilePropertyFactory filePropertyFactory, FileCollectionFactory fileCollectionFactory, DomainObjectCollectionFactory domainObjectCollectionFactory) {
        ServiceRegistry services = ProjectScopeServices.this;
        Instantiator instantiator = instantiatorFactory.injectAndDecorate(services);
        return new DefaultObjectFactory(instantiator, NamedObjectInstantiator.INSTANCE, fileResolver, directoryFileTreeFactory, filePropertyFactory, fileCollectionFactory, domainObjectCollectionFactory);
    }

    protected DomainObjectCollectionFactory createDomainObjectCollectionFactory(InstantiatorFactory instantiatorFactory, CollectionCallbackActionDecorator collectionCallbackActionDecorator, CrossProjectConfigurator projectConfigurator) {
        ServiceRegistry services = ProjectScopeServices.this;
        return new DefaultDomainObjectCollectionFactory(instantiatorFactory, services, collectionCallbackActionDecorator, MutationGuards.of(projectConfigurator));
    }

}
