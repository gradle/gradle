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

import org.gradle.StartParameter;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.execution.TaskOutputCacheListener;
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.changes.DefaultTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.changes.ShortCircuitTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.state.CacheBackedFileSnapshotRepository;
import org.gradle.api.internal.changedetection.state.CacheBackedTaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.CachingFileSnapshotter;
import org.gradle.api.internal.changedetection.state.DefaultFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.DefaultTaskArtifactStateCacheAccess;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.FileSnapshotter;
import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache;
import org.gradle.cache.internal.AsyncCacheAccessDecorator;
import org.gradle.api.internal.changedetection.state.OutputFilesCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskArtifactStateCacheAccess;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.cache.TaskOutputPacker;
import org.gradle.api.internal.tasks.cache.ZipTaskOutputPacker;
import org.gradle.api.internal.tasks.cache.config.TaskCachingInternal;
import org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter;
import org.gradle.api.internal.tasks.execution.PostExecutionAnalysisTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipCachedTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipEmptySourceFilesTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter;
import org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter;
import org.gradle.api.internal.tasks.execution.TaskActionExecutionListener;
import org.gradle.api.internal.tasks.execution.ValidatingTaskExecuter;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheDecorator;
import org.gradle.execution.taskgraph.TaskPlanExecutor;
import org.gradle.execution.taskgraph.TaskPlanExecutorFactory;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.environment.GradleBuildEnvironment;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;
import org.gradle.internal.operations.DefaultBuildOperationProcessor;
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory;
import org.gradle.internal.operations.DefaultBuildOperationWorkerRegistry;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;

public class TaskExecutionServices {

    TaskExecuter createTaskExecuter(TaskArtifactStateRepository repository, TaskOutputPacker packer, StartParameter startParameter, ListenerManager listenerManager, GradleInternal gradle) {
        // TODO - need a more comprehensible way to only collect inputs for the outer build
        //      - we are trying to ignore buildSrc here, but also avoid weirdness with use of GradleBuild tasks
        boolean isOuterBuild = gradle.getParent() == null;
        TaskInputsListener taskInputsListener = isOuterBuild
            ? listenerManager.getBroadcaster(TaskInputsListener.class)
            : TaskInputsListener.NOOP;

        return new ExecuteAtMostOnceTaskExecuter(
            new SkipOnlyIfTaskExecuter(
                new SkipTaskWithNoActionsExecuter(
                    new SkipEmptySourceFilesTaskExecuter(
                        taskInputsListener,
                        new ValidatingTaskExecuter(
                            new SkipUpToDateTaskExecuter(
                                repository,
                                createSkipCachedExecuterIfNecessary(
                                    startParameter,
                                    gradle.getTaskCaching(),
                                    packer,
                                    listenerManager,
                                    new PostExecutionAnalysisTaskExecuter(
                                        new ExecuteActionsTaskExecuter(
                                            listenerManager.getBroadcaster(TaskActionExecutionListener.class),
                                            listenerManager.getBroadcaster(TaskActionListener.class)
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        );
    }

    private static TaskExecuter createSkipCachedExecuterIfNecessary(StartParameter startParameter, TaskCachingInternal taskCaching, TaskOutputPacker packer, ListenerManager listenerManager, TaskExecuter delegate) {
        if (startParameter.isTaskOutputCacheEnabled()) {
            return new SkipCachedTaskExecuter(taskCaching, packer, startParameter, listenerManager.getBroadcaster(TaskOutputCacheListener.class), delegate);
        } else {
            return delegate;
        }
    }

    TaskArtifactStateCacheAccess createCacheAccess(Gradle gradle, CacheRepository cacheRepository, InMemoryTaskArtifactCache inMemoryTaskArtifactCache, GradleBuildEnvironment environment) {
        CacheDecorator decorator;
        if (environment.isLongLivingProcess()) {
            decorator = inMemoryTaskArtifactCache;
        } else {
            decorator = new AsyncCacheAccessDecorator();
        }
        return new DefaultTaskArtifactStateCacheAccess(gradle, cacheRepository, decorator);
    }

    CachingFileSnapshotter createFileSnapshotter(TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner) {
        return new CachingFileSnapshotter(new DefaultHasher(), cacheAccess, stringInterner);
    }

    FileCollectionSnapshotter createFileCollectionSnapshotter(FileSnapshotter fileSnapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, ListenerManager listenerManager) {
        DefaultFileCollectionSnapshotter snapshotter = new DefaultFileCollectionSnapshotter(fileSnapshotter, cacheAccess, stringInterner, fileSystem, directoryFileTreeFactory);
        listenerManager.addListener(snapshotter);
        return snapshotter;
    }

    TaskArtifactStateRepository createTaskArtifactStateRepository(Instantiator instantiator, TaskArtifactStateCacheAccess cacheAccess, StartParameter startParameter,  StringInterner stringInterner, FileCollectionFactory fileCollectionFactory, ClassLoaderHierarchyHasher classLoaderHierarchyHasher, FileCollectionSnapshotter fileCollectionSnapshotter) {
        OutputFilesCollectionSnapshotter outputFilesSnapshotter = new OutputFilesCollectionSnapshotter(fileCollectionSnapshotter);

        SerializerRegistry serializerRegistry = new DefaultSerializerRegistry();
        fileCollectionSnapshotter.registerSerializers(serializerRegistry);
        outputFilesSnapshotter.registerSerializers(serializerRegistry);

        TaskHistoryRepository taskHistoryRepository = new CacheBackedTaskHistoryRepository(cacheAccess,
            new CacheBackedFileSnapshotRepository(cacheAccess,
                serializerRegistry.build(FileCollectionSnapshot.class),
                new RandomLongIdGenerator()),
            stringInterner);

        return new ShortCircuitTaskArtifactStateRepository(
            startParameter,
            instantiator,
            new DefaultTaskArtifactStateRepository(
                taskHistoryRepository,
                instantiator,
                outputFilesSnapshotter,
                fileCollectionSnapshotter,
                fileCollectionFactory,
                classLoaderHierarchyHasher
            )
        );
    }

    TaskPlanExecutor createTaskExecutorFactory(StartParameter startParameter, ExecutorFactory executorFactory, BuildOperationWorkerRegistry buildOperationWorkerRegistry) {
        int parallelThreads = startParameter.isParallelProjectExecutionEnabled() ? startParameter.getMaxWorkerCount() : 1;
        return new TaskPlanExecutorFactory(parallelThreads, executorFactory, buildOperationWorkerRegistry).create();
    }

    BuildOperationProcessor createBuildOperationProcessor(StartParameter startParameter, ExecutorFactory executorFactory) {
        return new DefaultBuildOperationProcessor(new DefaultBuildOperationQueueFactory(), executorFactory, startParameter.getMaxWorkerCount());
    }

    BuildOperationWorkerRegistry createBuildOperationWorkerRegistry(StartParameter startParameter) {
        return new DefaultBuildOperationWorkerRegistry(startParameter.getMaxWorkerCount());
    }

    TaskOutputPacker createTaskResultPacker() {
        return new ZipTaskOutputPacker();
    }
}
