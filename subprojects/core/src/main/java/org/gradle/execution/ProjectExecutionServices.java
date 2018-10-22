/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.execution;

import com.google.common.collect.ImmutableList;
import org.gradle.StartParameter;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.changes.DefaultTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.changes.ShortCircuitTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.state.CacheBackedTaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.TaskOutputFilesRepository;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.MutatingTaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter;
import org.gradle.api.internal.tasks.execution.CleanupStaleOutputsExecuter;
import org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter;
import org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter;
import org.gradle.api.internal.tasks.execution.ResolveBuildCacheKeyExecuter;
import org.gradle.api.internal.tasks.execution.ResolvePreviousStateExecuter;
import org.gradle.api.internal.tasks.execution.ResolveTaskArtifactStateTaskExecuter;
import org.gradle.api.internal.tasks.execution.ResolveTaskOutputCachingStateExecuter;
import org.gradle.api.internal.tasks.execution.SkipCachedTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipEmptySourceFilesTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter;
import org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter;
import org.gradle.api.internal.tasks.execution.SnapshotAfterExecutionTaskExecuter;
import org.gradle.api.internal.tasks.execution.ValidatingTaskExecuter;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.internal.tasks.properties.annotations.FileFingerprintingPropertyAnnotationHandler;
import org.gradle.caching.internal.command.BuildCacheCommandFactory;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.tasks.TaskCacheKeyCalculator;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.impl.DefaultWorkExecutor;
import org.gradle.internal.execution.impl.steps.Context;
import org.gradle.internal.execution.impl.steps.CreateOutputsStep;
import org.gradle.internal.execution.impl.steps.ExecuteStep;
import org.gradle.internal.execution.impl.steps.TimeoutStep;
import org.gradle.internal.execution.timeout.TimeoutHandler;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter;
import org.gradle.internal.fingerprint.classpath.impl.DefaultClasspathFingerprinter;
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.impl.IgnoredPathFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.NameOnlyFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.RelativePathFileCollectionFingerprinter;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.scan.config.BuildScanPluginApplied;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;

import java.util.List;

public class ProjectExecutionServices extends DefaultServiceRegistry {
    public ProjectExecutionServices(ProjectInternal project) {
        super("Configured project services for '" + project.getPath() + "'", project.getServices());
    }

    private static final ImmutableList<? extends Class<? extends FileCollectionFingerprinter>> BUILT_IN_FINGERPRINTER_TYPES = ImmutableList.of(
        AbsolutePathFileCollectionFingerprinter.class, RelativePathFileCollectionFingerprinter.class, NameOnlyFileCollectionFingerprinter.class, IgnoredPathFileCollectionFingerprinter.class, OutputFileCollectionFingerprinter.class);

    OutputChangeListener createOutputChangeListener(ListenerManager listenerManager) {
        return listenerManager.getBroadcaster(OutputChangeListener.class);
    }

    TaskActionListener createTaskActionListener(ListenerManager listenerManager) {
        return listenerManager.getBroadcaster(TaskActionListener.class);
    }

    WorkExecutor createWorkExecutor(
        BuildInvocationScopeId buildInvocationScopeId,
        BuildCancellationToken cancellationToken,
        OutputChangeListener outputChangeListener,
        TimeoutHandler timeoutHandler
    ) {
        return new DefaultWorkExecutor(
            new CreateOutputsStep<Context>(
                new TimeoutStep<Context>(timeoutHandler,
                    new ExecuteStep(buildInvocationScopeId.getId(), cancellationToken, outputChangeListener)
                )
            )
        );
    }

    TaskExecuter createTaskExecuter(TaskArtifactStateRepository repository,
                                    BuildCacheCommandFactory commandFactory,
                                    BuildCacheController buildCacheController,
                                    TaskInputsListener inputsListener,
                                    TaskActionListener actionListener,
                                    OutputChangeListener outputChangeListener,
                                    BuildOperationExecutor buildOperationExecutor,
                                    AsyncWorkTracker asyncWorkTracker,
                                    BuildOutputCleanupRegistry cleanupRegistry,
                                    ExecutionHistoryStore executionHistoryStore,
                                    TaskOutputFilesRepository taskOutputFilesRepository,
                                    BuildScanPluginApplied buildScanPlugin,
                                    PathToFileResolver resolver,
                                    PropertyWalker propertyWalker,
                                    TaskExecutionGraphInternal taskExecutionGraph,
                                    BuildInvocationScopeId buildInvocationScopeId,
                                    TaskExecutionListener taskExecutionListener,
                                    WorkExecutor workExecutor
    ) {

        boolean buildCacheEnabled = buildCacheController.isEnabled();
        boolean scanPluginApplied = buildScanPlugin.isBuildScanPluginApplied();

        MutatingTaskExecuter mutatingExecuter = new ExecuteActionsTaskExecuter(
            buildOperationExecutor,
            asyncWorkTracker,
            actionListener,
            workExecutor
        );
        if (buildCacheEnabled) {
            mutatingExecuter = new SkipCachedTaskExecuter(
                buildCacheController,
                outputChangeListener,
                commandFactory,
                mutatingExecuter
            );
        }
        TaskExecuter executer = new SnapshotAfterExecutionTaskExecuter(taskOutputFilesRepository, mutatingExecuter);
        executer = new SkipUpToDateTaskExecuter(executer);
        executer = new ResolveTaskOutputCachingStateExecuter(buildCacheEnabled, executer);
        if (buildCacheEnabled || scanPluginApplied) {
            executer = new ResolveBuildCacheKeyExecuter(executer, buildOperationExecutor, buildCacheController.isEmitDebugLogging());
        }
        executer = new ValidatingTaskExecuter(executer);
        executer = new SkipEmptySourceFilesTaskExecuter(inputsListener, cleanupRegistry, outputChangeListener, executer, buildInvocationScopeId);
        executer = new ResolvePreviousStateExecuter(executionHistoryStore, executer);
        executer = new CleanupStaleOutputsExecuter(cleanupRegistry, taskOutputFilesRepository, buildOperationExecutor, outputChangeListener, executer);
        executer = new FinalizePropertiesTaskExecuter(executer);
        executer = new ResolveTaskArtifactStateTaskExecuter(repository, resolver, propertyWalker, executer);
        executer = new SkipTaskWithNoActionsExecuter(taskExecutionGraph, executer);
        executer = new SkipOnlyIfTaskExecuter(executer);
        executer = new CatchExceptionTaskExecuter(executer);
        executer = new EventFiringTaskExecuter(buildOperationExecutor, taskExecutionListener, executer);
        return executer;
    }

    ClasspathFingerprinter createClasspathFingerprinter(ResourceSnapshotterCacheService resourceSnapshotterCacheService, FileSystemSnapshotter fileSystemSnapshotter, StringInterner stringInterner, InputNormalizationHandlerInternal inputNormalizationHandler) {
        return new DefaultClasspathFingerprinter(
            resourceSnapshotterCacheService,
            fileSystemSnapshotter,
            inputNormalizationHandler.getRuntimeClasspath().getResourceFilter(),
            stringInterner
        );
    }

    FileCollectionFingerprinterRegistry createFileCollectionFingerprinterRegistry(
        ServiceRegistry serviceRegistry,
        List<FileFingerprintingPropertyAnnotationHandler> handlers
    ) {
        ImmutableList.Builder<FileCollectionFingerprinter> fingerprinterImplementations = ImmutableList.builder();
        for (Class<? extends FileCollectionFingerprinter> builtInFingerprinterType : BUILT_IN_FINGERPRINTER_TYPES) {
            fingerprinterImplementations.add(serviceRegistry.get(builtInFingerprinterType));
        }
        for (FileFingerprintingPropertyAnnotationHandler handler : handlers) {
            fingerprinterImplementations.add(serviceRegistry.get(handler.getFingerprinterImplementationType()));
        }
        return new DefaultFileCollectionFingerprinterRegistry(fingerprinterImplementations.build());
    }

    TaskHistoryRepository createTaskHistoryRepository(
        ExecutionHistoryStore executionHistoryStore,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        FileCollectionFingerprinterRegistry fingerprinterRegistry) {

        return new CacheBackedTaskHistoryRepository(
            executionHistoryStore,
            classLoaderHierarchyHasher,
            valueSnapshotter,
            fingerprinterRegistry
        );
    }

    TaskArtifactStateRepository createTaskArtifactStateRepository(
        Instantiator instantiator,
        StartParameter startParameter,
        TaskHistoryRepository taskHistoryRepository,
        TaskOutputFilesRepository taskOutputsRepository,
        FileCollectionFingerprinterRegistry fingerprinterRegistry
    ) {
        TaskCacheKeyCalculator taskCacheKeyCalculator = new TaskCacheKeyCalculator(startParameter.isBuildCacheDebugLogging());

        return new ShortCircuitTaskArtifactStateRepository(
            startParameter,
            instantiator,
            new DefaultTaskArtifactStateRepository(
                fingerprinterRegistry,
                taskHistoryRepository,
                instantiator,
                taskOutputsRepository,
                taskCacheKeyCalculator
            )
        );
    }
}
