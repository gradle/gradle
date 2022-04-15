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
package org.gradle.internal.service.scopes;

import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.BuildScopeListenerRegistrationListener;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.plugins.DefaultPluginManager;
import org.gradle.api.internal.plugins.ImperativeOnlyPluginTarget;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.plugins.PluginTarget;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.options.OptionReader;
import org.gradle.api.services.internal.BuildServiceProvider;
import org.gradle.api.services.internal.BuildServiceProviderNagger;
import org.gradle.api.services.internal.BuildServiceRegistryInternal;
import org.gradle.api.services.internal.DefaultBuildServicesRegistry;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.cache.internal.DefaultFileContentCacheFactory;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.SplitFileContentCacheFactory;
import org.gradle.cache.scopes.BuildScopedCache;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.configuration.project.BuiltInCommand;
import org.gradle.execution.BuildConfigurationAction;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.execution.BuildOperationFiringBuildWorkerExecutor;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.execution.DefaultBuildConfigurationActionExecuter;
import org.gradle.execution.DefaultTasksBuildExecutionAction;
import org.gradle.execution.DryRunBuildExecutionAction;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.execution.SelectedTaskExecutionAction;
import org.gradle.execution.TaskNameResolvingBuildConfigurationAction;
import org.gradle.execution.TaskSelector;
import org.gradle.execution.commandline.CommandLineTaskConfigurer;
import org.gradle.execution.commandline.CommandLineTaskParser;
import org.gradle.execution.plan.ExecutionNodeAccessHierarchies;
import org.gradle.execution.plan.LocalTaskNodeExecutor;
import org.gradle.execution.plan.NodeExecutor;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.execution.plan.WorkNodeExecutor;
import org.gradle.execution.taskgraph.DefaultTaskExecutionGraph;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.execution.taskgraph.TaskListenerInternal;
import org.gradle.initialization.DefaultTaskExecutionPreparer;
import org.gradle.initialization.TaskExecutionPreparer;
import org.gradle.internal.Factory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.cleanup.DefaultBuildOutputCleanupRegistry;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.BuildOutputCleanupRegistry;
import org.gradle.internal.execution.TaskExecutionTracker;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resources.SharedResourceLeaseRegistry;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.vfs.FileSystemAccess;

import java.util.LinkedList;
import java.util.List;

/**
 * Contains the services for a given {@link GradleInternal} instance.
 */
@SuppressWarnings("deprecation")
public class GradleScopeServices extends DefaultServiceRegistry {

    private final CompositeStoppable registries = new CompositeStoppable();

    public GradleScopeServices(final ServiceRegistry parent) {
        super(parent);
        register(registration -> {
            for (PluginServiceRegistry pluginServiceRegistry : parent.getAll(PluginServiceRegistry.class)) {
                pluginServiceRegistry.registerGradleServices(registration);
            }
        });
    }

    OptionReader createOptionReader() {
        return new OptionReader();
    }

    CommandLineTaskParser createCommandLineTaskParser(OptionReader optionReader, TaskSelector taskSelector) {
        return new CommandLineTaskParser(new CommandLineTaskConfigurer(optionReader), taskSelector);
    }

    BuildWorkExecutor createBuildExecuter(StyledTextOutputFactory textOutputFactory, BuildOperationExecutor buildOperationExecutor) {
        return new BuildOperationFiringBuildWorkerExecutor(
            new DryRunBuildExecutionAction(textOutputFactory,
                new SelectedTaskExecutionAction()),
            buildOperationExecutor);
    }

    BuildConfigurationActionExecuter createBuildConfigurationActionExecuter(CommandLineTaskParser commandLineTaskParser, ProjectConfigurer projectConfigurer, List<BuiltInCommand> builtInCommands) {
        List<BuildConfigurationAction> taskSelectionActions = new LinkedList<>();
        taskSelectionActions.add(new DefaultTasksBuildExecutionAction(projectConfigurer, builtInCommands));
        taskSelectionActions.add(new TaskNameResolvingBuildConfigurationAction(commandLineTaskParser));
        return new DefaultBuildConfigurationActionExecuter(taskSelectionActions);
    }

    TaskExecutionPreparer createTaskExecutionPreparer(BuildConfigurationActionExecuter buildConfigurationActionExecuter, BuildOperationExecutor buildOperationExecutor, BuildModelParameters buildModelParameters) {
        return new DefaultTaskExecutionPreparer(buildConfigurationActionExecuter, buildOperationExecutor, buildModelParameters);
    }

    ProjectFinder createProjectFinder(final GradleInternal gradle) {
        return new DefaultProjectFinder(gradle::getRootProject);
    }

    LocalTaskNodeExecutor createLocalTaskNodeExecutor(ExecutionNodeAccessHierarchies executionNodeAccessHierarchies) {
        return new LocalTaskNodeExecutor(
            executionNodeAccessHierarchies.getOutputHierarchy()
        );
    }

    WorkNodeExecutor createWorkNodeExecutor() {
        return new WorkNodeExecutor();
    }

    ListenerBroadcast<org.gradle.api.execution.TaskExecutionListener> createTaskExecutionListenerBroadcast(ListenerManager listenerManager) {
        return listenerManager.createAnonymousBroadcaster(org.gradle.api.execution.TaskExecutionListener.class);
    }

    org.gradle.api.execution.TaskExecutionListener createTaskExecutionListener(ListenerBroadcast<org.gradle.api.execution.TaskExecutionListener> broadcast) {
        return broadcast.getSource();
    }

    TaskListenerInternal createTaskListenerInternal(ListenerManager listenerManager) {
        return listenerManager.getBroadcaster(TaskListenerInternal.class);
    }

    ListenerBroadcast<TaskExecutionGraphListener> createTaskExecutionGraphListenerBroadcast(ListenerManager listenerManager) {
        return listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class);
    }

    TaskExecutionGraphInternal createTaskExecutionGraph(
        PlanExecutor planExecutor,
        List<NodeExecutor> nodeExecutors,
        BuildOperationExecutor buildOperationExecutor,
        ListenerBuildOperationDecorator listenerBuildOperationDecorator,
        GradleInternal gradleInternal,
        ListenerBroadcast<org.gradle.api.execution.TaskExecutionListener> taskListeners,
        ListenerBroadcast<TaskExecutionGraphListener> graphListeners,
        ListenerManager listenerManager,
        ServiceRegistry gradleScopedServices
    ) {
        return new DefaultTaskExecutionGraph(
            planExecutor,
            nodeExecutors,
            buildOperationExecutor,
            listenerBuildOperationDecorator,
            gradleInternal,
            graphListeners,
            taskListeners,
            listenerManager.getBroadcaster(BuildScopeListenerRegistrationListener.class),
            gradleScopedServices
        );
    }

    ServiceRegistryFactory createServiceRegistryFactory(final ServiceRegistry services) {
        final Factory<LoggingManagerInternal> loggingManagerInternalFactory = getFactory(LoggingManagerInternal.class);
        return new ServiceRegistryFactory() {
            @Override
            public ServiceRegistry createFor(Object domainObject) {
                if (domainObject instanceof ProjectInternal) {
                    ProjectScopeServices projectScopeServices = new ProjectScopeServices(services, (ProjectInternal) domainObject, loggingManagerInternalFactory);
                    registries.add(projectScopeServices);
                    return projectScopeServices;
                }
                throw new UnsupportedOperationException();
            }
        };
    }

    PluginRegistry createPluginRegistry(PluginRegistry parentRegistry) {
        return parentRegistry.createChild(get(GradleInternal.class).getClassLoaderScope());
    }

    PluginManagerInternal createPluginManager(Instantiator instantiator, GradleInternal gradleInternal, PluginRegistry pluginRegistry, InstantiatorFactory instantiatorFactory, BuildOperationExecutor buildOperationExecutor, UserCodeApplicationContext userCodeApplicationContext, CollectionCallbackActionDecorator decorator, DomainObjectCollectionFactory domainObjectCollectionFactory) {
        PluginTarget target = new ImperativeOnlyPluginTarget<>(gradleInternal);
        return instantiator.newInstance(DefaultPluginManager.class, pluginRegistry, instantiatorFactory.inject(this), target, buildOperationExecutor, userCodeApplicationContext, decorator, domainObjectCollectionFactory);
    }

    FileContentCacheFactory createFileContentCacheFactory(
        GlobalCacheLocations globalCacheLocations,
        BuildScopedCache cacheRepository,
        FileContentCacheFactory globalCacheFactory,
        InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
        ListenerManager listenerManager,
        FileSystemAccess fileSystemAccess
    ) {
        DefaultFileContentCacheFactory localCacheFactory = new DefaultFileContentCacheFactory(
            listenerManager,
            fileSystemAccess,
            cacheRepository,
            inMemoryCacheDecoratorFactory
        );
        return new SplitFileContentCacheFactory(
            globalCacheFactory,
            localCacheFactory,
            globalCacheLocations
        );
    }

    BuildServiceRegistryInternal createSharedServiceRegistry(
        BuildState buildState,
        Instantiator instantiator,
        DomainObjectCollectionFactory factory,
        InstantiatorFactory instantiatorFactory,
        ServiceRegistry services,
        ListenerManager listenerManager,
        IsolatableFactory isolatableFactory,
        SharedResourceLeaseRegistry sharedResourceLeaseRegistry,
        FeaturePreviews featurePreviews
    ) {
        // Instantiate via `instantiator` for the DSL decorations to the `BuildServiceRegistry` API
        return instantiator.newInstance(
            DefaultBuildServicesRegistry.class,
            buildState.getBuildIdentifier(),
            factory,
            instantiatorFactory,
            services,
            listenerManager,
            isolatableFactory,
            sharedResourceLeaseRegistry,
            featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE)
                ? new BuildServiceProviderNagger(services.get(TaskExecutionTracker.class))
                : BuildServiceProvider.Listener.EMPTY
        );
    }

    protected BuildOutputCleanupRegistry createBuildOutputCleanupRegistry(FileCollectionFactory fileCollectionFactory) {
        return new DefaultBuildOutputCleanupRegistry(fileCollectionFactory);
    }

    protected ConfigurationTargetIdentifier createConfigurationTargetIdentifier(GradleInternal gradle) {
        return ConfigurationTargetIdentifier.of(gradle);
    }

    // This needs to go here instead of being “build tree” scoped due to the GradleBuild task.
    // Builds launched by that task are part of the same build tree, but should have their own invocation ID.
    // Such builds also have their own root Gradle object.
    protected BuildInvocationScopeId createBuildInvocationScopeId(GradleInternal gradle) {
        GradleInternal rootGradle = gradle.getRoot();
        if (gradle == rootGradle) {
            return new BuildInvocationScopeId(UniqueId.generate());
        } else {
            return rootGradle.getServices().get(BuildInvocationScopeId.class);
        }
    }

    @Override
    public void close() {
        registries.stop();
        super.close();
    }

}
