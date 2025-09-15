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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.plugins.DefaultPluginManager;
import org.gradle.api.internal.plugins.ImperativeOnlyPluginTarget;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.plugins.PluginTarget;
import org.gradle.api.internal.plugins.PluginTargetType;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.cache.internal.DefaultFileContentCacheFactory;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.SplitFileContentCacheFactory;
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.execution.plan.DefaultNodeExecutor;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.execution.taskgraph.DefaultTaskExecutionGraph;
import org.gradle.execution.taskgraph.TaskExecutionGraphExecutionListener;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.execution.taskgraph.TaskListenerInternal;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.vfs.FileSystemAccess;

/**
 * Contains the services for a given {@link GradleInternal} instance.
 */
@SuppressWarnings("deprecation")
public class GradleScopeServices implements ServiceRegistrationProvider {

    @Provides
    ListenerBroadcast<org.gradle.api.execution.TaskExecutionListener> createTaskExecutionListenerBroadcast(ListenerManager listenerManager) {
        return listenerManager.createAnonymousBroadcaster(org.gradle.api.execution.TaskExecutionListener.class);
    }

    @Provides
    org.gradle.api.execution.TaskExecutionListener createTaskExecutionListener(ListenerBroadcast<org.gradle.api.execution.TaskExecutionListener> broadcast) {
        return broadcast.getSource();
    }

    @Provides
    TaskListenerInternal createTaskListenerInternal(ListenerManager listenerManager) {
        return listenerManager.getBroadcaster(TaskListenerInternal.class);
    }

    @Provides
    ListenerBroadcast<TaskExecutionGraphListener> createTaskExecutionGraphListenerBroadcast(ListenerManager listenerManager) {
        return listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class);
    }

    @Provides
    ListenerBroadcast<TaskExecutionGraphExecutionListener> createTaskExecutionGraphListenerInternalBroadcast(ListenerManager listenerManager) {
        return listenerManager.createAnonymousBroadcaster(TaskExecutionGraphExecutionListener.class);
    }

    @Provides
    TaskExecutionGraphInternal createTaskExecutionGraph(
        PlanExecutor planExecutor,
        BuildOperationRunner buildOperationRunner,
        ListenerBuildOperationDecorator listenerBuildOperationDecorator,
        GradleInternal gradleInternal,
        ListenerBroadcast<org.gradle.api.execution.TaskExecutionListener> taskListeners,
        ListenerBroadcast<TaskExecutionGraphListener> graphListeners,
        ListenerBroadcast<TaskExecutionGraphExecutionListener> internalGraphListeners,
        ListenerManager listenerManager,
        ServiceRegistry gradleScopedServices
    ) {
        return new DefaultTaskExecutionGraph(
            planExecutor,
            new DefaultNodeExecutor(),
            buildOperationRunner,
            listenerBuildOperationDecorator,
            gradleInternal,
            graphListeners,
            internalGraphListeners,
            taskListeners,
            listenerManager.getBroadcaster(BuildScopeListenerRegistrationListener.class),
            gradleScopedServices
        );
    }

    @Provides
    PluginRegistry createPluginRegistry(PluginRegistry parentRegistry, GradleInternal gradleInternal) {
        return parentRegistry.createChild(gradleInternal.getClassLoaderScope());
    }

    @Provides
    PluginManagerInternal createPluginManager(
        ServiceRegistry gradleScopeServiceRegistry,
        Instantiator instantiator,
        GradleInternal gradleInternal,
        PluginRegistry pluginRegistry,
        InstantiatorFactory instantiatorFactory,
        BuildOperationRunner buildOperationRunner,
        UserCodeApplicationContext userCodeApplicationContext,
        CollectionCallbackActionDecorator decorator,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        InternalProblems problems
    ) {
        PluginTarget target = new ImperativeOnlyPluginTarget<>(PluginTargetType.GRADLE, gradleInternal, problems);
        return instantiator.newInstance(DefaultPluginManager.class, pluginRegistry, instantiatorFactory.inject(gradleScopeServiceRegistry), target, buildOperationRunner, userCodeApplicationContext, decorator, domainObjectCollectionFactory);
    }

    @Provides
    FileContentCacheFactory createFileContentCacheFactory(
        GlobalCacheLocations globalCacheLocations,
        BuildScopedCacheBuilderFactory cacheBuilderFactory,
        FileContentCacheFactory globalCacheFactory,
        InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
        ListenerManager listenerManager,
        FileSystemAccess fileSystemAccess
    ) {
        DefaultFileContentCacheFactory localCacheFactory = new DefaultFileContentCacheFactory(
            listenerManager,
            fileSystemAccess,
            cacheBuilderFactory,
            inMemoryCacheDecoratorFactory
        );
        return new SplitFileContentCacheFactory(
            globalCacheFactory,
            localCacheFactory,
            globalCacheLocations
        );
    }
}
