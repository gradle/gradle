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
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.changes.DefaultTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.changes.ShortCircuitTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.state.CacheBackedFileSnapshotRepository;
import org.gradle.api.internal.changedetection.state.CacheBackedTaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.ClasspathSnapshotter;
import org.gradle.api.internal.changedetection.state.DefaultClasspathSnapshotter;
import org.gradle.api.internal.changedetection.state.DefaultFileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.DefaultGenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.DefaultTaskHistoryStore;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache;
import org.gradle.api.internal.changedetection.state.OutputFilesSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.TaskHistoryStore;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.DefaultFileHasher;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter;
import org.gradle.api.internal.tasks.execution.ResolveTaskArtifactStateTaskExecuter;
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
import org.gradle.caching.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.internal.tasks.GZipTaskOutputPacker;
import org.gradle.caching.internal.tasks.OutputPreparingTaskOutputPacker;
import org.gradle.caching.internal.tasks.TarTaskOutputPacker;
import org.gradle.caching.internal.tasks.TaskOutputPacker;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory;
import org.gradle.execution.taskgraph.TaskPlanExecutor;
import org.gradle.execution.taskgraph.TaskPlanExecutorFactory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.environment.GradleBuildEnvironment;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.TimeProvider;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.List;

public class TaskExecutionServices {

    TaskExecuter createTaskExecuter(TaskArtifactStateRepository repository, TaskOutputPacker packer, StartParameter startParameter, ListenerManager listenerManager, GradleInternal gradle, TaskOutputOriginFactory taskOutputOriginFactory) {
        // TODO - need a more comprehensible way to only collect inputs for the outer build
        //      - we are trying to ignore buildSrc here, but also avoid weirdness with use of GradleBuild tasks
        boolean isOuterBuild = gradle.getParent() == null;
        TaskInputsListener taskInputsListener = isOuterBuild
            ? listenerManager.getBroadcaster(TaskInputsListener.class)
            : TaskInputsListener.NOOP;

        TaskOutputsGenerationListener taskOutputsGenerationListener = listenerManager.getBroadcaster(TaskOutputsGenerationListener.class);
        return new CatchExceptionTaskExecuter(
            new ExecuteAtMostOnceTaskExecuter(
                new SkipOnlyIfTaskExecuter(
                    new SkipTaskWithNoActionsExecuter(
                        new ResolveTaskArtifactStateTaskExecuter(
                            repository,
                            new SkipEmptySourceFilesTaskExecuter(
                                taskInputsListener,
                                new ValidatingTaskExecuter(
                                    new SkipUpToDateTaskExecuter(
                                        createSkipCachedExecuterIfNecessary(
                                            startParameter,
                                            gradle.getBuildCache(),
                                            packer,
                                            taskOutputsGenerationListener,
                                            taskOutputOriginFactory,
                                            createVerifyNoInputChangesExecuterIfNecessary(
                                                startParameter,
                                                repository,
                                                new ExecuteActionsTaskExecuter(
                                                    taskOutputsGenerationListener,
                                                    listenerManager.getBroadcaster(TaskActionListener.class)
                                                )
                                            )
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

    private static TaskExecuter createSkipCachedExecuterIfNecessary(StartParameter startParameter, BuildCacheConfigurationInternal buildCacheConfiguration, TaskOutputPacker packer, TaskOutputsGenerationListener taskOutputsGenerationListener, TaskOutputOriginFactory taskOutputOriginFactory, TaskExecuter delegate) {
        if (startParameter.isTaskOutputCacheEnabled()) {
            return new SkipCachedTaskExecuter(taskOutputOriginFactory, buildCacheConfiguration, packer, taskOutputsGenerationListener, delegate);
        } else {
            return delegate;
        }
    }

    private static TaskExecuter createVerifyNoInputChangesExecuterIfNecessary(StartParameter startParameter, TaskArtifactStateRepository repository, TaskExecuter delegate) {
        if (Boolean.getBoolean("org.gradle.tasks.verifyinputs")) {
            return new VerifyNoInputChangesTaskExecuter(repository, delegate);
        } else {
            return delegate;
        }
    }

    TaskHistoryStore createCacheAccess(Gradle gradle, CacheRepository cacheRepository, InMemoryTaskArtifactCache inMemoryTaskArtifactCache, GradleBuildEnvironment environment) {
        return new DefaultTaskHistoryStore(gradle, cacheRepository, inMemoryTaskArtifactCache);
    }

    CachingFileHasher createFileSnapshotter(TaskHistoryStore cacheAccess, StringInterner stringInterner) {
        return new CachingFileHasher(new DefaultFileHasher(), cacheAccess, stringInterner);
    }

    GenericFileCollectionSnapshotter createGenericFileCollectionSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, ListenerManager listenerManager) {
        DefaultGenericFileCollectionSnapshotter snapshotter = new DefaultGenericFileCollectionSnapshotter(hasher, stringInterner, fileSystem, directoryFileTreeFactory);
        listenerManager.addListener(snapshotter);
        return snapshotter;
    }

    ClasspathSnapshotter createClasspathSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, ListenerManager listenerManager) {
        DefaultClasspathSnapshotter snapshotter = new DefaultClasspathSnapshotter(hasher, stringInterner, fileSystem, directoryFileTreeFactory);
        listenerManager.addListener(snapshotter);
        return snapshotter;
    }

    FileCollectionSnapshotterRegistry createFileCollectionSnapshotterRegistry(ServiceRegistry serviceRegistry) {
        List<FileCollectionSnapshotter> snapshotters = serviceRegistry.getAll(FileCollectionSnapshotter.class);
        return new DefaultFileCollectionSnapshotterRegistry(snapshotters);
    }

    TaskArtifactStateRepository createTaskArtifactStateRepository(Instantiator instantiator, TaskHistoryStore cacheAccess, StartParameter startParameter, StringInterner stringInterner, FileCollectionFactory fileCollectionFactory, ClassLoaderHierarchyHasher classLoaderHierarchyHasher, FileCollectionSnapshotterRegistry fileCollectionSnapshotterRegistry) {
        OutputFilesSnapshotter outputFilesSnapshotter = new OutputFilesSnapshotter();

        SerializerRegistry serializerRegistry = new DefaultSerializerRegistry();
        for (FileCollectionSnapshotter snapshotter : fileCollectionSnapshotterRegistry.getAllSnapshotters()) {
            snapshotter.registerSerializers(serializerRegistry);
        }

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
                fileCollectionSnapshotterRegistry,
                fileCollectionFactory,
                classLoaderHierarchyHasher
            )
        );
    }

    TaskPlanExecutor createTaskExecutorFactory(StartParameter startParameter, ExecutorFactory executorFactory, BuildOperationWorkerRegistry buildOperationWorkerRegistry) {
        int parallelThreads = startParameter.isParallelProjectExecutionEnabled() ? startParameter.getMaxWorkerCount() : 1;
        return new TaskPlanExecutorFactory(parallelThreads, executorFactory, buildOperationWorkerRegistry).create();
    }

    TaskOutputPacker createTaskResultPacker(FileSystem fileSystem) {
        return new OutputPreparingTaskOutputPacker(
            new GZipTaskOutputPacker(
                new TarTaskOutputPacker(fileSystem)
            )
        );
    }

    TaskOutputOriginFactory createTaskOutputOriginFactory(TimeProvider timeProvider, InetAddressFactory inetAddressFactory, GradleInternal gradleInternal) {
        File rootDir = gradleInternal.getRootProject().getRootDir();
        return new TaskOutputOriginFactory(timeProvider, inetAddressFactory, rootDir, SystemProperties.getInstance().getUserName(), OperatingSystem.current().getName(), GradleVersion.current());
    }
}
