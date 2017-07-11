/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.StartParameter;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.changes.DefaultTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.changes.ShortCircuitTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.state.CacheBackedFileSnapshotRepository;
import org.gradle.api.internal.changedetection.state.CacheBackedTaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.DefaultFileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.DefaultTaskHistoryStore;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.TaskHistoryStore;
import org.gradle.api.internal.changedetection.state.ValueSnapshotter;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.taskfactory.FileSnapshottingPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter;
import org.gradle.api.internal.tasks.execution.ResolveBuildCacheKeyExecuter;
import org.gradle.api.internal.tasks.execution.ResolveTaskArtifactStateTaskExecuter;
import org.gradle.api.internal.tasks.execution.ResolveTaskOutputCachingStateExecuter;
import org.gradle.api.internal.tasks.execution.SkipCachedTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipEmptySourceFilesTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter;
import org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter;
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener;
import org.gradle.api.internal.tasks.execution.ValidatingTaskExecuter;
import org.gradle.api.internal.tasks.execution.VerifyNoInputChangesTaskExecuter;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.tasks.BuildCacheTaskServices;
import org.gradle.caching.internal.tasks.TaskCacheKeyCalculator;
import org.gradle.caching.internal.tasks.TaskOutputCacheCommandFactory;
import org.gradle.execution.taskgraph.TaskPlanExecutor;
import org.gradle.execution.taskgraph.TaskPlanExecutorFactory;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.environment.GradleBuildEnvironment;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.List;

public class TaskExecutionServices {

    void configure(ServiceRegistration registration) {
        registration.addProvider(new BuildCacheTaskServices());
    }

    TaskExecuter createTaskExecuter(TaskArtifactStateRepository repository,
                                    TaskOutputCacheCommandFactory taskOutputCacheCommandFactory,
                                    BuildCacheController buildCacheController,
                                    StartParameter startParameter,
                                    ListenerManager listenerManager,
                                    TaskInputsListener inputsListener,
                                    BuildOperationExecutor buildOperationExecutor,
                                    AsyncWorkTracker asyncWorkTracker) {

        boolean taskOutputCacheEnabled = startParameter.isBuildCacheEnabled();
        TaskOutputsGenerationListener taskOutputsGenerationListener = listenerManager.getBroadcaster(TaskOutputsGenerationListener.class);

        TaskExecuter executer = new ExecuteActionsTaskExecuter(
            taskOutputsGenerationListener,
            listenerManager.getBroadcaster(TaskActionListener.class),
            buildOperationExecutor,
            asyncWorkTracker
        );
        boolean verifyInputsEnabled = Boolean.getBoolean("org.gradle.tasks.verifyinputs");
        if (verifyInputsEnabled) {
            executer = new VerifyNoInputChangesTaskExecuter(repository, executer);
        }
        if (taskOutputCacheEnabled) {
            executer = new SkipCachedTaskExecuter(
                buildCacheController,
                taskOutputsGenerationListener,
                taskOutputCacheCommandFactory,
                executer
            );
        }
        executer = new SkipUpToDateTaskExecuter(executer);
        executer = new ResolveTaskOutputCachingStateExecuter(taskOutputCacheEnabled, executer);
        if (verifyInputsEnabled || taskOutputCacheEnabled) {
            executer = new ResolveBuildCacheKeyExecuter(executer, buildOperationExecutor);
        }
        executer = new ValidatingTaskExecuter(executer);
        executer = new SkipEmptySourceFilesTaskExecuter(inputsListener, executer);
        executer = new ResolveTaskArtifactStateTaskExecuter(repository, executer);
        executer = new SkipTaskWithNoActionsExecuter(executer);
        executer = new SkipOnlyIfTaskExecuter(executer);
        executer = new ExecuteAtMostOnceTaskExecuter(executer);
        executer = new CatchExceptionTaskExecuter(executer);
        return executer;
    }

    TaskHistoryStore createCacheAccess(Gradle gradle, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, GradleBuildEnvironment environment) {
        return new DefaultTaskHistoryStore(gradle, cacheRepository, inMemoryCacheDecoratorFactory);
    }

    FileCollectionSnapshotterRegistry createFileCollectionSnapshotterRegistry(ServiceRegistry serviceRegistry) {
        List<FileSnapshottingPropertyAnnotationHandler> handlers = serviceRegistry.getAll(FileSnapshottingPropertyAnnotationHandler.class);
        ImmutableList.Builder<FileCollectionSnapshotter> snapshotters = ImmutableList.builder();
        snapshotters.add(serviceRegistry.get(GenericFileCollectionSnapshotter.class));
        for (FileSnapshottingPropertyAnnotationHandler handler : handlers) {
            snapshotters.add(serviceRegistry.get(handler.getSnapshotterType()));
        }
        return new DefaultFileCollectionSnapshotterRegistry(snapshotters.build());
    }

    TaskArtifactStateRepository createTaskArtifactStateRepository(Instantiator instantiator, TaskHistoryStore cacheAccess, StartParameter startParameter, StringInterner stringInterner, FileCollectionFactory fileCollectionFactory, ClassLoaderHierarchyHasher classLoaderHierarchyHasher, FileCollectionSnapshotterRegistry fileCollectionSnapshotterRegistry, TaskCacheKeyCalculator cacheKeyCalculator, ValueSnapshotter valueSnapshotter, BuildInvocationScopeId buildInvocationScopeId) {
        SerializerRegistry serializerRegistry = new DefaultSerializerRegistry();
        for (FileCollectionSnapshotter snapshotter : fileCollectionSnapshotterRegistry.getAllSnapshotters()) {
            snapshotter.registerSerializers(serializerRegistry);
        }

        TaskHistoryRepository taskHistoryRepository = new CacheBackedTaskHistoryRepository(
            cacheAccess,
            new CacheBackedFileSnapshotRepository(cacheAccess,
                serializerRegistry.build(FileCollectionSnapshot.class),
                new RandomLongIdGenerator()
            ),
            stringInterner,
            buildInvocationScopeId
        );

        return new ShortCircuitTaskArtifactStateRepository(
            startParameter,
            instantiator,
            new DefaultTaskArtifactStateRepository(
                taskHistoryRepository,
                instantiator,
                fileCollectionSnapshotterRegistry,
                fileCollectionFactory,
                classLoaderHierarchyHasher,
                cacheKeyCalculator,
                valueSnapshotter
            )
        );
    }


    TaskPlanExecutor createTaskExecutorFactory(ParallelismConfigurationManager parallelismConfigurationManager, ExecutorFactory executorFactory, WorkerLeaseService workerLeaseService) {
        return new TaskPlanExecutorFactory(parallelismConfigurationManager, executorFactory, workerLeaseService).create();
    }

}
