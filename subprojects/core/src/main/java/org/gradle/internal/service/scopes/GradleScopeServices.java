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

import org.gradle.api.Action;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DefaultPluginManager;
import org.gradle.api.internal.plugins.ImperativeOnlyPluginTarget;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.plugins.PluginTarget;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.options.OptionReader;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.DefaultFileContentCacheFactory;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.SplitFileContentCacheFactory;
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.composite.internal.IncludedBuildTaskGraph;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.execution.BuildConfigurationAction;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.execution.DefaultBuildConfigurationActionExecuter;
import org.gradle.execution.DefaultBuildWorkExecutor;
import org.gradle.execution.DefaultTasksBuildExecutionAction;
import org.gradle.execution.DryRunBuildExecutionAction;
import org.gradle.execution.ExcludedTaskFilteringBuildConfigurationAction;
import org.gradle.execution.IncludedBuildLifecycleBuildWorkExecutor;
import org.gradle.execution.BuildOperationFiringBuildWorkerExecutor;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.execution.SelectedTaskExecutionAction;
import org.gradle.execution.TaskNameResolvingBuildConfigurationAction;
import org.gradle.execution.TaskSelector;
import org.gradle.execution.commandline.CommandLineTaskConfigurer;
import org.gradle.execution.commandline.CommandLineTaskParser;
import org.gradle.execution.plan.DependencyResolver;
import org.gradle.execution.plan.LocalTaskNodeExecutor;
import org.gradle.execution.plan.NodeExecutor;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.execution.plan.TaskDependencyResolver;
import org.gradle.execution.plan.TaskNodeDependencyResolver;
import org.gradle.execution.plan.TaskNodeFactory;
import org.gradle.execution.plan.WorkNodeDependencyResolver;
import org.gradle.execution.plan.WorkNodeExecutor;
import org.gradle.execution.taskgraph.DefaultTaskExecutionGraph;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.initialization.DefaultTaskExecutionPreparer;
import org.gradle.initialization.BuildOperatingFiringTaskExecutionPreparer;
import org.gradle.initialization.TaskExecutionPreparer;
import org.gradle.internal.Factory;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.internal.cleanup.DefaultBuildOutputCleanupRegistry;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.scan.BuildScanServices;
import org.gradle.internal.scan.config.BuildScanPluginApplied;
import org.gradle.internal.scan.scopeids.BuildScanScopeIds;
import org.gradle.internal.scan.scopeids.DefaultBuildScanScopeIds;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.scopeids.id.UserScopeId;
import org.gradle.internal.scopeids.id.WorkspaceScopeId;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.WellKnownFileLocations;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Arrays.asList;

/**
 * Contains the services for a given {@link GradleInternal} instance.
 */
public class GradleScopeServices extends DefaultServiceRegistry {

    private final CompositeStoppable registries = new CompositeStoppable();

    public GradleScopeServices(final ServiceRegistry parent, final GradleInternal gradle) {
        super(parent);
        add(GradleInternal.class, gradle);
        register(new Action<ServiceRegistration>() {
            @Override
            public void execute(ServiceRegistration registration) {
                for (PluginServiceRegistry pluginServiceRegistry : parent.getAll(PluginServiceRegistry.class)) {
                    pluginServiceRegistry.registerGradleServices(registration);
                }
            }
        });

        if (gradle.getParent() == null) {
            addProvider(new BuildScanServices());
        } else {
            // Task execution services at all levels needs this
            addProvider(new Object() {
                BuildScanPluginApplied createBuildScanPluginApplied() {
                    return gradle.getRoot().getServices().get(BuildScanPluginApplied.class);
                }
            });
        }
    }

    TaskSelector createTaskSelector(GradleInternal gradle, ProjectConfigurer projectConfigurer) {
        return new TaskSelector(gradle, projectConfigurer);
    }

    OptionReader createOptionReader() {
        return new OptionReader();
    }

    CommandLineTaskParser createCommandLineTaskParser(OptionReader optionReader, TaskSelector taskSelector) {
        return new CommandLineTaskParser(new CommandLineTaskConfigurer(optionReader), taskSelector);
    }

    BuildWorkExecutor createBuildExecuter(StyledTextOutputFactory textOutputFactory, IncludedBuildControllers includedBuildControllers, BuildOperationExecutor buildOperationExecutor) {
        return new BuildOperationFiringBuildWorkerExecutor(
            new IncludedBuildLifecycleBuildWorkExecutor(
                new DefaultBuildWorkExecutor(
                    asList(new DryRunBuildExecutionAction(textOutputFactory),
                        new SelectedTaskExecutionAction())),
                includedBuildControllers),
            buildOperationExecutor);
    }

    BuildConfigurationActionExecuter createBuildConfigurationActionExecuter(CommandLineTaskParser commandLineTaskParser, TaskSelector taskSelector, ProjectConfigurer projectConfigurer, ProjectStateRegistry projectStateRegistry) {
        List<BuildConfigurationAction> taskSelectionActions = new LinkedList<BuildConfigurationAction>();
        taskSelectionActions.add(new DefaultTasksBuildExecutionAction(projectConfigurer));
        taskSelectionActions.add(new TaskNameResolvingBuildConfigurationAction(commandLineTaskParser));
        return new DefaultBuildConfigurationActionExecuter(Arrays.asList(new ExcludedTaskFilteringBuildConfigurationAction(taskSelector)), taskSelectionActions, projectStateRegistry);
    }

    IncludedBuildControllers createIncludedBuildControllers(GradleInternal gradle, IncludedBuildControllers sharedControllers) {
        if (gradle.getParent() == null) {
            return sharedControllers;
        } else {
            return IncludedBuildControllers.EMPTY;
        }
    }

    TaskExecutionPreparer createTaskExecutionPreparer(BuildConfigurationActionExecuter buildConfigurationActionExecuter, IncludedBuildControllers includedBuildControllers, BuildOperationExecutor buildOperationExecutor) {
        return new BuildOperatingFiringTaskExecutionPreparer(
            new DefaultTaskExecutionPreparer(buildConfigurationActionExecuter, includedBuildControllers, buildOperationExecutor),
            buildOperationExecutor);
    }

    ProjectFinder createProjectFinder(final BuildStateRegistry buildStateRegistry, final GradleInternal gradle) {
        return new DefaultProjectFinder(buildStateRegistry, new Supplier<ProjectInternal>() {
            @Override
            public ProjectInternal get() {
                return gradle.getRootProject();
            }
        });
    }

    TaskNodeFactory createTaskNodeFactory(GradleInternal gradle, IncludedBuildTaskGraph includedBuildTaskGraph) {
        return new TaskNodeFactory(gradle, includedBuildTaskGraph);
    }

    TaskNodeDependencyResolver createTaskNodeResolver(TaskNodeFactory taskNodeFactory) {
        return new TaskNodeDependencyResolver(taskNodeFactory);
    }

    WorkNodeDependencyResolver createWorkNodeResolver() {
        return new WorkNodeDependencyResolver();
    }

    TaskDependencyResolver createTaskDependencyResolver(List<DependencyResolver> dependencyResolvers) {
        return new TaskDependencyResolver(dependencyResolvers);
    }

    LocalTaskNodeExecutor createLocalTaskNodeExecutor() {
        return new LocalTaskNodeExecutor();
    }

    WorkNodeExecutor createWorkNodeExecutor() {
        return new WorkNodeExecutor();
    }

    ListenerBroadcast<TaskExecutionListener> createTaskExecutionListenerBroadcast(ListenerManager listenerManager) {
        return listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class);
    }

    TaskExecutionListener createTaskExecutionListener(ListenerBroadcast<TaskExecutionListener> broadcast) {
        return broadcast.getSource();
    }

    ListenerBroadcast<TaskExecutionGraphListener> createTaskExecutionGraphListenerBroadcast(ListenerManager listenerManager) {
        return listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class);
    }

    TaskExecutionGraphInternal createTaskExecutionGraph(
        PlanExecutor planExecutor,
        List<NodeExecutor> nodeExecutors,
        BuildOperationExecutor buildOperationExecutor,
        ListenerBuildOperationDecorator listenerBuildOperationDecorator,
        WorkerLeaseService workerLeaseService,
        ResourceLockCoordinationService coordinationService,
        GradleInternal gradleInternal,
        TaskNodeFactory taskNodeFactory,
        TaskDependencyResolver dependencyResolver,
        ListenerBroadcast<TaskExecutionListener> taskListeners,
        ListenerBroadcast<TaskExecutionGraphListener> graphListeners
    ) {
        return new DefaultTaskExecutionGraph(planExecutor, nodeExecutors, buildOperationExecutor, listenerBuildOperationDecorator, workerLeaseService, coordinationService, gradleInternal, taskNodeFactory, dependencyResolver, graphListeners, taskListeners);
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
        PluginTarget target = new ImperativeOnlyPluginTarget<GradleInternal>(gradleInternal);
        return instantiator.newInstance(DefaultPluginManager.class, pluginRegistry, instantiatorFactory.inject(this), target, buildOperationExecutor, userCodeApplicationContext, decorator, domainObjectCollectionFactory);
    }

    FileContentCacheFactory createFileContentCacheFactory(FileContentCacheFactory globalCacheFactory, ListenerManager listenerManager, FileSystemSnapshotter fileSystemSnapshotter, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, Gradle gradle, WellKnownFileLocations wellKnownFileLocations) {
        DefaultFileContentCacheFactory localCacheFactory = new DefaultFileContentCacheFactory(listenerManager, fileSystemSnapshotter, cacheRepository, inMemoryCacheDecoratorFactory, gradle);
        return new SplitFileContentCacheFactory(globalCacheFactory, localCacheFactory, wellKnownFileLocations);
    }

    protected BuildOutputCleanupRegistry createBuildOutputCleanupRegistry(FileResolver fileResolver) {
        return new DefaultBuildOutputCleanupRegistry(fileResolver);
    }

    protected ConfigurationTargetIdentifier createConfigurationTargetIdentifier(GradleInternal gradle) {
        return ConfigurationTargetIdentifier.of(gradle);
    }

    // Note: This would be better housed in a scope that encapsulated the tree of Gradle objects.
    // as we don't have this right now we simulate it by reaching up the tree.
    protected BuildInvocationScopeId createBuildScopeId(GradleInternal gradle) {
        if (gradle.getParent() == null) {
            return new BuildInvocationScopeId(UniqueId.generate());
        } else {
            return gradle.getRoot().getServices().get(BuildInvocationScopeId.class);
        }
    }

    protected BuildScanScopeIds createBuildScanScopeIds(BuildInvocationScopeId buildInvocationScopeId, WorkspaceScopeId workspaceScopeId, UserScopeId userScopeId) {
        return new DefaultBuildScanScopeIds(buildInvocationScopeId, workspaceScopeId, userScopeId);
    }

    @Override
    public void close() {
        registries.stop();
        super.close();
    }

}
