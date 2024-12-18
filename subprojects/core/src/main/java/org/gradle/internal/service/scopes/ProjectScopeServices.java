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

import org.gradle.api.AntBuilder;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.ExternalProcessStartedListener;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.collections.DefaultDomainObjectCollectionFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.component.DefaultSoftwareComponentContainer;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.ManagedFactories;
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.initialization.ActionBasedModelDefaultsHandler;
import org.gradle.api.internal.initialization.BuildLogicBuilder;
import org.gradle.api.internal.initialization.DefaultScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.DefaultPluginManager;
import org.gradle.api.internal.plugins.ImperativeOnlyPluginTarget;
import org.gradle.api.internal.plugins.PluginInstantiator;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.plugins.PluginTarget;
import org.gradle.api.internal.plugins.PluginTargetType;
import org.gradle.api.internal.plugins.RuleBasedPluginTarget;
import org.gradle.api.internal.project.CrossProjectConfigurator;
import org.gradle.api.internal.project.CrossProjectModelAccess;
import org.gradle.api.internal.project.DefaultAntBuilderFactory;
import org.gradle.api.internal.project.DeferredProjectConfiguration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.project.ant.DefaultAntLoggingAdapterFactory;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.taskfactory.TaskIdentityFactory;
import org.gradle.api.internal.project.taskfactory.TaskInstantiator;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.resources.ApiTextResourceAdapter;
import org.gradle.api.internal.resources.DefaultResourceHandler;
import org.gradle.api.internal.tasks.DefaultTaskContainerFactory;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyUsageTracker;
import org.gradle.api.internal.tasks.TaskStatistics;
import org.gradle.api.internal.tasks.properties.TaskScheme;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.configuration.ConfigurationTargetIdentifiers;
import org.gradle.configuration.project.DefaultProjectConfigurationActionContainer;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.internal.Factory;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.state.DefaultManagedFactoryRegistry;
import org.gradle.internal.state.ManagedFactoryRegistry;
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
import org.gradle.plugin.software.internal.ModelDefaultsHandler;
import org.gradle.plugin.software.internal.PluginScheme;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
import org.gradle.process.internal.ExecFactory;
import org.gradle.tooling.provider.model.internal.DefaultToolingModelBuilderRegistry;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Contains the services for a given project.
 */
public class ProjectScopeServices implements ServiceRegistrationProvider {

    public static CloseableServiceRegistry create(
        ServiceRegistry parent,
        ProjectInternal project,
        Factory<LoggingManagerInternal> loggingManagerInternalFactory
    ) {
        return ServiceRegistryBuilder.builder()
            .scope(Scope.Project.class)
            .displayName("project services")
            .parent(parent)
            .provider(new ProjectScopeServices(project, loggingManagerInternalFactory))
            .provider(new WorkerSharedProjectScopeServices(project.getProjectDir()))
            .build();
    }

    private final ProjectInternal project;
    private final Factory<LoggingManagerInternal> loggingManagerInternalFactory;

    public ProjectScopeServices(final ProjectInternal project, Factory<LoggingManagerInternal> loggingManagerInternalFactory) {
        this.project = project;
        this.loggingManagerInternalFactory = loggingManagerInternalFactory;
    }

    @Provides
    protected void configure(
        ServiceRegistration registration,
        List<GradleModuleServices> gradleModuleServiceProviders,
        DependencyManagementServices dependencyManagementServices
    ) {
        registration.add(ProjectInternal.class, project);
        dependencyManagementServices.addDslServices(registration, project);
        for (GradleModuleServices services : gradleModuleServiceProviders) {
            services.registerProjectServices(registration);
        }
    }

    @Provides
    protected PluginRegistry createPluginRegistry(PluginRegistry rootRegistry) {
        PluginRegistry parentRegistry;
        ProjectState parent = project.getOwner().getBuildParent();
        if (parent == null) {
            parentRegistry = rootRegistry.createChild(project.getBaseClassLoaderScope());
        } else {
            parentRegistry = parent.getMutableModel().getServices().get(PluginRegistry.class);
        }
        return parentRegistry.createChild(project.getClassLoaderScope());
    }

    @Provides
    protected DeferredProjectConfiguration createDeferredProjectConfiguration() {
        return new DeferredProjectConfiguration(project);
    }

    @Provides
    protected LoggingManagerInternal createLoggingManager() {
        return loggingManagerInternalFactory.create();
    }

    @Provides
    protected ProjectConfigurationActionContainer createProjectConfigurationActionContainer() {
        return new DefaultProjectConfigurationActionContainer();
    }

    @Provides
    protected DefaultResourceHandler.Factory createResourceHandlerFactory(FileResolver fileResolver, TaskDependencyFactory taskDependencyFactory, FileSystem fileSystem, TemporaryFileProvider temporaryFileProvider, ApiTextResourceAdapter.Factory textResourceAdapterFactory) {
        return DefaultResourceHandler.Factory.from(
            fileResolver,
            taskDependencyFactory,
            fileSystem,
            temporaryFileProvider,
            textResourceAdapterFactory
        );
    }

    @Provides
    protected ExecFactory decorateExecFactory(ExecFactory execFactory, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, InstantiatorFactory instantiatorFactory, ObjectFactory objectFactory, JavaModuleDetector javaModuleDetector, ListenerManager listenerManager) {
        return execFactory.forContext()
            .withFileResolver(fileResolver)
            .withFileCollectionFactory(fileCollectionFactory)
            .withInstantiator(instantiatorFactory.decorateLenient())
            .withObjectFactory(objectFactory)
            .withJavaModuleDetector(javaModuleDetector)
            .withExternalProcessStartedListener(listenerManager.getBroadcaster(ExternalProcessStartedListener.class))
            .build();
    }

    @Provides
    protected TemporaryFileProvider createTemporaryFileProvider() {
        return new DefaultTemporaryFileProvider(() -> project.getLayout().getBuildDirectory().dir("tmp").get().getAsFile());
    }

    @Provides
    protected Factory<AntBuilder> createAntBuilderFactory() {
        return new DefaultAntBuilderFactory(project, new DefaultAntLoggingAdapterFactory());
    }

    @Provides
    protected DefaultToolingModelBuilderRegistry decorateToolingModelRegistry(DefaultToolingModelBuilderRegistry buildScopedToolingModelBuilders, BuildOperationRunner buildOperationRunner, ProjectStateRegistry projectStateRegistry) {
        return buildScopedToolingModelBuilders.createChild();
    }

    @Provides
    protected PluginManagerInternal createPluginManager(
        Instantiator instantiator,
        InstantiatorFactory instantiatorFactory,
        ServiceRegistry projectScopeServiceRegistry,
        ModelRuleExtractor modelRuleExtractor,
        ModelRuleSourceDetector modelRuleSourceDetector,
        PluginRegistry pluginRegistry,
        BuildOperationRunner buildOperationRunner,
        UserCodeApplicationContext userCodeApplicationContext,
        CollectionCallbackActionDecorator decorator,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        PluginScheme pluginScheme,
        InternalProblems problems
    ) {

        PluginTarget ruleBasedTarget = new RuleBasedPluginTarget(
            project,
            new ImperativeOnlyPluginTarget<>(PluginTargetType.PROJECT, project, problems),
            modelRuleExtractor,
            modelRuleSourceDetector
        );
        return instantiator.newInstance(
            DefaultPluginManager.class,
            pluginRegistry,
            new PluginInstantiator(
                instantiatorFactory.injectScheme().withServices(projectScopeServiceRegistry).instantiator(),
                pluginScheme.getInstantiationScheme().withServices(projectScopeServiceRegistry).instantiator()
            ),
            ruleBasedTarget,
            buildOperationRunner,
            userCodeApplicationContext,
            decorator,
            domainObjectCollectionFactory
        );
    }

    @Provides
    protected ITaskFactory createTaskFactory(ServiceRegistry projectScopeServiceRegistry, ITaskFactory parentFactory, TaskScheme taskScheme) {
        return parentFactory.createChild(project, taskScheme.getInstantiationScheme().withServices(projectScopeServiceRegistry));
    }

    @Provides
    protected TaskInstantiator createTaskInstantiator(TaskIdentityFactory taskIdentityFactory, ITaskFactory taskFactory) {
        return new TaskInstantiator(taskIdentityFactory, taskFactory, project);
    }

    @Provides
    protected TaskContainerInternal createTaskContainerInternal(
        Instantiator instantiator,
        TaskIdentityFactory taskIdentityFactory,
        ITaskFactory taskFactory,
        TaskStatistics taskStatistics,
        BuildOperationRunner buildOperationRunner,
        CrossProjectConfigurator crossProjectConfigurator,
        CollectionCallbackActionDecorator decorator,
        ProjectRegistry<ProjectInternal> projectRegistry
    ) {
        return new DefaultTaskContainerFactory(
            instantiator,
            taskIdentityFactory,
            taskFactory,
            project,
            taskStatistics,
            buildOperationRunner,
            crossProjectConfigurator,
            decorator,
            projectRegistry
        ).create();
    }

    @Provides
    protected SoftwareComponentContainer createSoftwareComponentContainer(Instantiator instantiator, InstantiatorFactory instantiatorFactory, ServiceRegistry services, CollectionCallbackActionDecorator decorator) {
        Instantiator elementInstantiator = instantiatorFactory.decorate(services);
        return elementInstantiator.newInstance(DefaultSoftwareComponentContainer.class, instantiator, elementInstantiator, decorator);
    }

    @Provides
    protected ProjectFinder createProjectFinder() {
        return new DefaultProjectFinder(() -> project);
    }

    @Provides
    protected ModelRegistry createModelRegistry(ModelRuleExtractor ruleExtractor) {
        return new DefaultModelRegistry(ruleExtractor, project.getPath(), run -> project.getOwner().applyToMutableState(p -> run.run()));
    }

    @Provides
    protected ScriptHandlerInternal createScriptHandler(
        DependencyManagementServices dependencyManagementServices,
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        BuildLogicBuilder buildLogicBuilder
    ) {
        ScriptHandlerFactory factory = new DefaultScriptHandlerFactory(
            dependencyManagementServices,
            buildLogicBuilder
        );

        return factory.createProjectScriptHandler(
            project.getBuildScriptSource(),
            project.getClassLoaderScope(),
            fileResolver,
            fileCollectionFactory,
            project
        );
    }

    @Provides
    protected PropertyHost createPropertyHost() {
        return new ProjectBackedPropertyHost(project);
    }

    @Provides
    protected TypeConverter createTypeConverter(PathToFileResolver fileResolver) {
        return new DefaultTypeConverter(fileResolver);
    }

    @Provides
    protected RuntimeClasspathNormalizationInternal createRuntimeClasspathNormalizationStrategy(Instantiator instantiator) {
        return instantiator.newInstance(DefaultRuntimeClasspathNormalization.class);
    }

    @Provides
    protected InputNormalizationHandlerInternal createInputNormalizationHandler(Instantiator instantiator, RuntimeClasspathNormalizationInternal runtimeClasspathNormalizationStrategy) {
        return instantiator.newInstance(DefaultInputNormalizationHandler.class, runtimeClasspathNormalizationStrategy);
    }

    @Provides
    protected TaskDependencyFactory createTaskDependencyFactory() {
        @Nullable TaskDependencyUsageTracker tracker = project.getServices().get(CrossProjectModelAccess.class).taskDependencyUsageTracker(project);
        return DefaultTaskDependencyFactory.forProject(project.getTasks(), tracker);
    }

    @Provides
    protected ConfigurationTargetIdentifier createConfigurationTargetIdentifier() {
        return ConfigurationTargetIdentifiers.of(project);
    }

    @Provides
    protected DomainObjectCollectionFactory createDomainObjectCollectionFactory(
        InstantiatorFactory instantiatorFactory,
        ServiceRegistry projectScopeServiceRegistry,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator,
        CrossProjectConfigurator projectConfigurator
    ) {
        return new DefaultDomainObjectCollectionFactory(instantiatorFactory, projectScopeServiceRegistry, collectionCallbackActionDecorator, projectConfigurator.getLazyBehaviorGuard());
    }

    @Provides
    protected ManagedFactoryRegistry createManagedFactoryRegistry(ManagedFactoryRegistry parent, FileCollectionFactory fileCollectionFactory, FileFactory fileFactory, FilePropertyFactory filePropertyFactory) {
        return new DefaultManagedFactoryRegistry(parent).withFactories(
            new ManagedFactories.ConfigurableFileCollectionManagedFactory(fileCollectionFactory),
            new org.gradle.api.internal.file.ManagedFactories.RegularFilePropertyManagedFactory(filePropertyFactory),
            new org.gradle.api.internal.file.ManagedFactories.DirectoryManagedFactory(fileFactory),
            new org.gradle.api.internal.file.ManagedFactories.DirectoryPropertyManagedFactory(filePropertyFactory)
        );
    }

    @Provides
    protected ModelDefaultsHandler createActionBasedModelDefaultsHandler(SoftwareTypeRegistry softwareTypeRegistry, PluginScheme pluginScheme, InternalProblems problems) {
        return new ActionBasedModelDefaultsHandler(softwareTypeRegistry, pluginScheme.getInspectionScheme(), problems);
    }
}
