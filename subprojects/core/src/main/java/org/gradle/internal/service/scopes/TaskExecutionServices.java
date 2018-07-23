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
import org.gradle.api.internal.changedetection.state.DefaultFileCollectionFingerprinterRegistry;
import org.gradle.api.internal.changedetection.state.DefaultTaskHistoryStore;
import org.gradle.api.internal.changedetection.state.DefaultTaskOutputFilesRepository;
import org.gradle.api.internal.changedetection.state.FileCollectionFingerprinter;
import org.gradle.api.internal.changedetection.state.FileCollectionFingerprinterRegistry;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.TaskHistoryStore;
import org.gradle.api.internal.changedetection.state.TaskOutputFilesRepository;
import org.gradle.api.internal.changedetection.state.ValueSnapshotter;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter;
import org.gradle.api.internal.tasks.execution.CleanupStaleOutputsExecuter;
import org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter;
import org.gradle.api.internal.tasks.execution.FinalizeInputFilePropertiesTaskExecuter;
import org.gradle.api.internal.tasks.execution.OutputDirectoryCreatingTaskExecuter;
import org.gradle.api.internal.tasks.execution.ResolveBuildCacheKeyExecuter;
import org.gradle.api.internal.tasks.execution.ResolveTaskArtifactStateTaskExecuter;
import org.gradle.api.internal.tasks.execution.ResolveTaskOutputCachingStateExecuter;
import org.gradle.api.internal.tasks.execution.SkipCachedTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipEmptySourceFilesTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter;
import org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter;
import org.gradle.api.internal.tasks.execution.TaskOutputChangesListener;
import org.gradle.api.internal.tasks.execution.ValidatingTaskExecuter;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.internal.tasks.properties.annotations.FileSnapshottingPropertyAnnotationHandler;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.tasks.TaskCacheKeyCalculator;
import org.gradle.caching.internal.tasks.TaskOutputCacheCommandFactory;
import org.gradle.execution.TaskExecutionGraphInternal;
import org.gradle.execution.taskgraph.DefaultTaskPlanExecutor;
import org.gradle.execution.taskgraph.TaskPlanExecutor;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.fingerprint.HistoricalFileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.IgnoredPathFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.NameOnlyFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.RelativePathFileCollectionFingerprinter;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.scan.config.BuildScanPluginApplied;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.GradleVersion;

import java.util.Collections;
import java.util.List;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class TaskExecutionServices {

    private static final ImmutableList<? extends Class<? extends FileCollectionFingerprinter>> BUILT_IN_FINGERPRINTER_TYPES = ImmutableList.of(
        AbsolutePathFileCollectionFingerprinter.class, RelativePathFileCollectionFingerprinter.class, NameOnlyFileCollectionFingerprinter.class, IgnoredPathFileCollectionFingerprinter.class, OutputFileCollectionFingerprinter.class);

    TaskExecuter createTaskExecuter(TaskArtifactStateRepository repository,
                                    TaskOutputCacheCommandFactory taskOutputCacheCommandFactory,
                                    BuildCacheController buildCacheController,
                                    ListenerManager listenerManager,
                                    TaskInputsListener inputsListener,
                                    BuildOperationExecutor buildOperationExecutor,
                                    AsyncWorkTracker asyncWorkTracker,
                                    BuildOutputCleanupRegistry cleanupRegistry,
                                    TaskOutputFilesRepository taskOutputFilesRepository,
                                    BuildScanPluginApplied buildScanPlugin,
                                    PathToFileResolver resolver,
                                    PropertyWalker propertyWalker,
                                    TaskExecutionGraphInternal taskExecutionGraph,
                                    BuildInvocationScopeId buildInvocationScopeId,
                                    BuildCancellationToken buildCancellationToken
    ) {

        boolean buildCacheEnabled = buildCacheController.isEnabled();
        boolean scanPluginApplied = buildScanPlugin.isBuildScanPluginApplied();
        TaskOutputChangesListener taskOutputChangesListener = listenerManager.getBroadcaster(TaskOutputChangesListener.class);

        TaskExecuter executer = new ExecuteActionsTaskExecuter(
            taskOutputChangesListener,
            listenerManager.getBroadcaster(TaskActionListener.class),
            buildOperationExecutor,
            asyncWorkTracker,
            buildInvocationScopeId,
            buildCancellationToken
        );
        executer = new OutputDirectoryCreatingTaskExecuter(executer);
        if (buildCacheEnabled) {
            executer = new SkipCachedTaskExecuter(
                buildCacheController,
                taskOutputChangesListener,
                taskOutputCacheCommandFactory,
                executer
            );
        }
        executer = new SkipUpToDateTaskExecuter(executer);
        executer = new ResolveTaskOutputCachingStateExecuter(buildCacheEnabled, executer);
        if (buildCacheEnabled || scanPluginApplied) {
            executer = new ResolveBuildCacheKeyExecuter(executer, buildOperationExecutor, buildCacheController.isEmitDebugLogging());
        }
        executer = new ValidatingTaskExecuter(executer);
        executer = new SkipEmptySourceFilesTaskExecuter(inputsListener, cleanupRegistry, taskOutputChangesListener, executer, buildInvocationScopeId);
        executer = new FinalizeInputFilePropertiesTaskExecuter(executer);
        executer = new CleanupStaleOutputsExecuter(cleanupRegistry, taskOutputFilesRepository, buildOperationExecutor, taskOutputChangesListener, executer);
        executer = new ResolveTaskArtifactStateTaskExecuter(repository, resolver, propertyWalker, executer);
        executer = new SkipTaskWithNoActionsExecuter(taskExecutionGraph, executer);
        executer = new SkipOnlyIfTaskExecuter(executer);
        executer = new ExecuteAtMostOnceTaskExecuter(executer);
        executer = new CatchExceptionTaskExecuter(executer);
        executer = new EventFiringTaskExecuter(buildOperationExecutor, taskExecutionGraph, executer);
        return executer;
    }

    TaskHistoryStore createCacheAccess(Gradle gradle, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new DefaultTaskHistoryStore(gradle, cacheRepository, inMemoryCacheDecoratorFactory);
    }

    FileCollectionFingerprinterRegistry createFileCollectionFingerprinterRegistry(ServiceRegistry serviceRegistry) {
        List<FileSnapshottingPropertyAnnotationHandler> handlers = serviceRegistry.getAll(FileSnapshottingPropertyAnnotationHandler.class);
        ImmutableList.Builder<FileCollectionFingerprinter> fingerprinterImplementations = ImmutableList.builder();
        for (Class<? extends FileCollectionFingerprinter> builtInFingerprinterType : BUILT_IN_FINGERPRINTER_TYPES) {
            fingerprinterImplementations.add(serviceRegistry.get(builtInFingerprinterType));
        }
        for (FileSnapshottingPropertyAnnotationHandler handler : handlers) {
            fingerprinterImplementations.add(serviceRegistry.get(handler.getFingerprinterImplementationType()));
        }
        return new DefaultFileCollectionFingerprinterRegistry(fingerprinterImplementations.build());
    }

    TaskHistoryRepository createTaskHistoryRepository(
        TaskHistoryStore cacheAccess,
        FileCollectionFingerprinterRegistry fileCollectionFingerprinterRegistry,
        StringInterner stringInterner,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        FileCollectionFingerprinterRegistry fingerprinterRegistry) {
        SerializerRegistry serializerRegistry = new DefaultSerializerRegistry();
        for (FileCollectionFingerprinter fingerprinter : fileCollectionFingerprinterRegistry.getAllFingerprinters()) {
            fingerprinter.registerSerializers(serializerRegistry);
        }

        return new CacheBackedTaskHistoryRepository(
            cacheAccess,
            serializerRegistry.build(HistoricalFileCollectionFingerprint.class),
            stringInterner,
            classLoaderHierarchyHasher,
            valueSnapshotter,
            fingerprinterRegistry
        );
    }

    TaskOutputFilesRepository createTaskOutputFilesRepository(CacheRepository cacheRepository, Gradle gradle, FileSystemSnapshotter fileSystemSnapshotter, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        PersistentCache cacheAccess = cacheRepository
            .cache(gradle, "buildOutputCleanup")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withDisplayName("Build Output Cleanup Cache")
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .withProperties(Collections.singletonMap("gradle.version", GradleVersion.current().getVersion()))
            .open();
        return new DefaultTaskOutputFilesRepository(cacheAccess, fileSystemSnapshotter, inMemoryCacheDecoratorFactory);
    }

    TaskArtifactStateRepository createTaskArtifactStateRepository(Instantiator instantiator, StartParameter startParameter, TaskHistoryRepository taskHistoryRepository, TaskOutputFilesRepository taskOutputsRepository) {
        TaskCacheKeyCalculator taskCacheKeyCalculator = new TaskCacheKeyCalculator(startParameter.isBuildCacheDebugLogging());

        return new ShortCircuitTaskArtifactStateRepository(
            startParameter,
            instantiator,
            new DefaultTaskArtifactStateRepository(
                taskHistoryRepository,
                instantiator,
                taskOutputsRepository,
                taskCacheKeyCalculator
            )
        );
    }

    TaskPlanExecutor createTaskExecutorFactory(
        ParallelismConfigurationManager parallelismConfigurationManager,
        ExecutorFactory executorFactory,
        WorkerLeaseService workerLeaseService,
        BuildCancellationToken cancellationToken,
        ResourceLockCoordinationService coordinationService) {
        int parallelThreads = parallelismConfigurationManager.getParallelismConfiguration().getMaxWorkerCount();
        if (parallelThreads < 1) {
            throw new IllegalStateException(String.format("Cannot create executor for requested number of worker threads: %s.", parallelThreads));
        }

        // TODO: Make task plan executor respond to changes in parallelism configuration
        return new DefaultTaskPlanExecutor(
            parallelismConfigurationManager.getParallelismConfiguration(),
            executorFactory,
            workerLeaseService,
            cancellationToken,
            coordinationService
        );
    }

}
