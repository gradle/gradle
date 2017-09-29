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
import org.gradle.api.internal.changedetection.state.CacheBackedTaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.DefaultFileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.DefaultTaskHistoryStore;
import org.gradle.api.internal.changedetection.state.DefaultTaskOutputFilesRepository;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.FileSystemMirror;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.TaskHistoryStore;
import org.gradle.api.internal.changedetection.state.TaskOutputFilesRepository;
import org.gradle.api.internal.changedetection.state.ValueSnapshotter;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.taskfactory.FileSnapshottingPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter;
import org.gradle.api.internal.tasks.execution.CleanupStaleOutputsExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter;
import org.gradle.api.internal.tasks.execution.OutputDirectoryCreatingTaskExecuter;
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
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.tasks.BuildCacheTaskServices;
import org.gradle.caching.internal.tasks.TaskOutputCacheCommandFactory;
import org.gradle.execution.taskgraph.TaskPlanExecutor;
import org.gradle.execution.taskgraph.TaskPlanExecutorFactory;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.scan.config.BuildScanPluginApplied;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.GradleVersion;

import java.util.Collections;
import java.util.List;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

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
                                    AsyncWorkTracker asyncWorkTracker,
                                    BuildOutputCleanupRegistry cleanupRegistry,
                                    TaskOutputFilesRepository taskOutputFilesRepository,
                                    BuildScanPluginApplied buildScanPlugin) {

        boolean taskOutputCacheEnabled = startParameter.isBuildCacheEnabled();
        boolean scanPluginApplied = buildScanPlugin.isBuildScanPluginApplied();
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
        executer = new OutputDirectoryCreatingTaskExecuter(executer);
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
        if (verifyInputsEnabled || taskOutputCacheEnabled || scanPluginApplied) {
            executer = new ResolveBuildCacheKeyExecuter(executer, buildOperationExecutor);
        }
        executer = new ValidatingTaskExecuter(executer);
        executer = new SkipEmptySourceFilesTaskExecuter(inputsListener, cleanupRegistry, taskOutputsGenerationListener, executer);
        executer = new CleanupStaleOutputsExecuter(cleanupRegistry, taskOutputFilesRepository, buildOperationExecutor, executer);
        executer = new ResolveTaskArtifactStateTaskExecuter(repository, executer);
        executer = new SkipTaskWithNoActionsExecuter(executer);
        executer = new SkipOnlyIfTaskExecuter(executer);
        executer = new ExecuteAtMostOnceTaskExecuter(executer);
        executer = new CatchExceptionTaskExecuter(executer);
        return executer;
    }

    TaskHistoryStore createCacheAccess(Gradle gradle, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new DefaultTaskHistoryStore(gradle, cacheRepository, inMemoryCacheDecoratorFactory);
    }

    FileCollectionSnapshotterRegistry createFileCollectionSnapshotterRegistry(ServiceRegistry serviceRegistry) {
        List<FileSnapshottingPropertyAnnotationHandler> handlers = serviceRegistry.getAll(FileSnapshottingPropertyAnnotationHandler.class);
        ImmutableList.Builder<FileCollectionSnapshotter> snapshotterImplementations = ImmutableList.builder();
        snapshotterImplementations.add(serviceRegistry.get(GenericFileCollectionSnapshotter.class));
        for (FileSnapshottingPropertyAnnotationHandler handler : handlers) {
            snapshotterImplementations.add(serviceRegistry.get(handler.getSnapshotterImplementationType()));
        }
        return new DefaultFileCollectionSnapshotterRegistry(snapshotterImplementations.build());
    }

    TaskHistoryRepository createTaskHistoryRepository(
        TaskHistoryStore cacheAccess,
        FileCollectionSnapshotterRegistry fileCollectionSnapshotterRegistry,
        StringInterner stringInterner,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        FileCollectionSnapshotterRegistry snapshotterRegistry,
        FileCollectionFactory fileCollectionFactory,
        BuildInvocationScopeId buildInvocationScopeId) {
        SerializerRegistry serializerRegistry = new DefaultSerializerRegistry();
        for (FileCollectionSnapshotter snapshotter : fileCollectionSnapshotterRegistry.getAllSnapshotters()) {
            snapshotter.registerSerializers(serializerRegistry);
        }

        return new CacheBackedTaskHistoryRepository(
            cacheAccess,
            serializerRegistry.build(FileCollectionSnapshot.class),
            stringInterner,
            classLoaderHierarchyHasher,
            valueSnapshotter,
            snapshotterRegistry,
            fileCollectionFactory,
            buildInvocationScopeId
        );
    }

    TaskOutputFilesRepository createTaskOutputFilesRepository(CacheRepository cacheRepository, Gradle gradle, FileSystemMirror fileSystemMirror, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        PersistentCache cacheAccess = cacheRepository
            .cache(gradle, "buildOutputCleanup")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withDisplayName("Build Output Cleanup Cache")
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .withProperties(Collections.singletonMap("gradle.version", GradleVersion.current().getVersion()))
            .open();
        return new DefaultTaskOutputFilesRepository(cacheAccess, fileSystemMirror, inMemoryCacheDecoratorFactory);
    }

    TaskArtifactStateRepository createTaskArtifactStateRepository(Instantiator instantiator, StartParameter startParameter, TaskHistoryRepository taskHistoryRepository, TaskOutputFilesRepository taskOutputsRepository) {

        return new ShortCircuitTaskArtifactStateRepository(
            startParameter,
            instantiator,
            new DefaultTaskArtifactStateRepository(
                taskHistoryRepository,
                instantiator,
                taskOutputsRepository
            )
        );
    }

    TaskPlanExecutor createTaskExecutorFactory(ParallelismConfigurationManager parallelismConfigurationManager, ExecutorFactory executorFactory, WorkerLeaseService workerLeaseService) {
        return new TaskPlanExecutorFactory(parallelismConfigurationManager, executorFactory, workerLeaseService).create();
    }

}
