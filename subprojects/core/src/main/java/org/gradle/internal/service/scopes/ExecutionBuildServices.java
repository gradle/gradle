/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.DefaultExecutionHistoryCacheAccess;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.BuildOutputCleanupRegistry;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.execution.history.ExecutionHistoryCacheAccess;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadataStore;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.execution.history.OutputsCleaner;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector;
import org.gradle.internal.execution.history.impl.DefaultExecutionHistoryStore;
import org.gradle.internal.execution.history.impl.DefaultOutputFilesRepository;
import org.gradle.internal.execution.impl.DefaultExecutionEngine;
import org.gradle.internal.execution.steps.AssignImmutableWorkspaceStep;
import org.gradle.internal.execution.steps.AssignMutableWorkspaceStep;
import org.gradle.internal.execution.steps.BroadcastChangingOutputsStep;
import org.gradle.internal.execution.steps.BuildCacheStep;
import org.gradle.internal.execution.steps.CancelExecutionStep;
import org.gradle.internal.execution.steps.CaptureIncrementalStateBeforeExecutionStep;
import org.gradle.internal.execution.steps.CaptureNonIncrementalStateBeforeExecutionStep;
import org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep;
import org.gradle.internal.execution.steps.ChangingOutputsContext;
import org.gradle.internal.execution.steps.ChoosePipelineStep;
import org.gradle.internal.execution.steps.ExecuteStep;
import org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep;
import org.gradle.internal.execution.steps.HandleStaleOutputsStep;
import org.gradle.internal.execution.steps.IdentifyStep;
import org.gradle.internal.execution.steps.IdentityCacheStep;
import org.gradle.internal.execution.steps.IdentityContext;
import org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep;
import org.gradle.internal.execution.steps.NeverUpToDateStep;
import org.gradle.internal.execution.steps.NoInputChangesStep;
import org.gradle.internal.execution.steps.OverlappingOutputsFilter;
import org.gradle.internal.execution.steps.PreCreateOutputParentsStep;
import org.gradle.internal.execution.steps.RemovePreviousOutputsStep;
import org.gradle.internal.execution.steps.ResolveChangesStep;
import org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep;
import org.gradle.internal.execution.steps.ResolveInputChangesStep;
import org.gradle.internal.execution.steps.ResolveNonIncrementalCachingStateStep;
import org.gradle.internal.execution.steps.Result;
import org.gradle.internal.execution.steps.SkipEmptyIncrementalWorkStep;
import org.gradle.internal.execution.steps.SkipUpToDateStep;
import org.gradle.internal.execution.steps.Step;
import org.gradle.internal.execution.steps.StoreExecutionStateStep;
import org.gradle.internal.execution.steps.TimeoutStep;
import org.gradle.internal.execution.steps.ValidateStep;
import org.gradle.internal.execution.steps.WorkspaceResult;
import org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep;
import org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep;
import org.gradle.internal.execution.timeout.TimeoutHandler;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.util.GradleVersion;

import java.util.Collections;
import java.util.function.Supplier;

import static org.gradle.internal.execution.steps.AfterExecutionOutputFilter.NO_FILTER;

public class ExecutionBuildServices implements ServiceRegistrationProvider {
    @Provides
    ExecutionHistoryCacheAccess createCacheAccess(BuildScopedCacheBuilderFactory cacheBuilderFactory) {
        return new DefaultExecutionHistoryCacheAccess(cacheBuilderFactory);
    }

    @Provides
    ExecutionHistoryStore createExecutionHistoryStore(
        ExecutionHistoryCacheAccess executionHistoryCacheAccess,
        InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
        StringInterner stringInterner,
        ClassLoaderHierarchyHasher classLoaderHasher
    ) {
        return new DefaultExecutionHistoryStore(
            executionHistoryCacheAccess,
            inMemoryCacheDecoratorFactory,
            stringInterner,
            classLoaderHasher
        );
    }

    @Provides
    OutputFilesRepository createOutputFilesRepository(BuildScopedCacheBuilderFactory cacheBuilderFactory, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        PersistentCache cacheAccess = cacheBuilderFactory
            .createCrossVersionCacheBuilder("buildOutputCleanup")
            .withDisplayName("Build Output Cleanup Cache")
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .withProperties(Collections.singletonMap("gradle.version", GradleVersion.current().getVersion()))
            .open();
        return new DefaultOutputFilesRepository(cacheAccess, inMemoryCacheDecoratorFactory);
    }

    @Provides
    OutputChangeListener createOutputChangeListener(ListenerManager listenerManager) {
        return listenerManager.getBroadcaster(OutputChangeListener.class);
    }

    @Provides
    public ExecutionEngine createExecutionEngine(
        BuildCacheController buildCacheController,
        BuildCancellationToken cancellationToken,
        BuildInvocationScopeId buildInvocationScopeId,
        BuildOperationRunner buildOperationRunner,
        BuildOperationProgressEventEmitter buildOperationProgressEventEmitter,
        BuildOutputCleanupRegistry buildOutputCleanupRegistry,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        CurrentBuildOperationRef currentBuildOperationRef,
        Deleter deleter,
        ExecutionStateChangeDetector changeDetector,
        FileSystemAccess fileSystemAccess,
        ImmutableWorkspaceMetadataStore immutableWorkspaceMetadataStore,
        OutputChangeListener outputChangeListener,
        WorkInputListeners workInputListeners, OutputFilesRepository outputFilesRepository,
        OutputSnapshotter outputSnapshotter,
        OverlappingOutputDetector overlappingOutputDetector,
        StartParameter startParameter,
        TimeoutHandler timeoutHandler,
        ValidateStep.ValidationWarningRecorder validationWarningRecorder,
        VirtualFileSystem virtualFileSystem
    ) {
        UniqueId buildId = buildInvocationScopeId.getId();
        Supplier<OutputsCleaner> skipEmptyWorkOutputsCleanerSupplier = () -> new OutputsCleaner(deleter, buildOutputCleanupRegistry::isOutputOwnedByBuild, buildOutputCleanupRegistry::isOutputOwnedByBuild);
        boolean emitBuildCacheDebugLogging = startParameter.isBuildCacheDebugLogging();

        // @formatter:off
        // CHECKSTYLE:OFF
        Step<ChangingOutputsContext,Result> sharedExecutionPipeline =
            new PreCreateOutputParentsStep<>(
            new TimeoutStep<>(timeoutHandler, currentBuildOperationRef,
            new CancelExecutionStep<>(cancellationToken,
            new ExecuteStep<>(buildOperationRunner
        ))));

        Step<IdentityContext,WorkspaceResult> immutablePipeline =
            new AssignImmutableWorkspaceStep<>(deleter, fileSystemAccess, immutableWorkspaceMetadataStore, outputSnapshotter,
            new MarkSnapshottingInputsStartedStep<>(
            new CaptureNonIncrementalStateBeforeExecutionStep<>(buildOperationRunner, classLoaderHierarchyHasher,
            new ValidateStep<>(virtualFileSystem, validationWarningRecorder,
            new ResolveNonIncrementalCachingStateStep<>(buildCacheController, emitBuildCacheDebugLogging,
            new MarkSnapshottingInputsFinishedStep<>(
            new NeverUpToDateStep<>(
            new BuildCacheStep<>(buildCacheController, deleter, fileSystemAccess, outputChangeListener,
            new CaptureOutputsAfterExecutionStep<>(buildOperationRunner, buildId, outputSnapshotter, NO_FILTER,
            new NoInputChangesStep<>(
            new BroadcastChangingOutputsStep<>(outputChangeListener,
            sharedExecutionPipeline
        )))))))))));

        Step<IdentityContext,WorkspaceResult> mutablePipeline =
            new AssignMutableWorkspaceStep<>(
            new HandleStaleOutputsStep<>(buildOperationRunner, buildOutputCleanupRegistry,  deleter, outputChangeListener, outputFilesRepository,
            new LoadPreviousExecutionStateStep<>(
            new MarkSnapshottingInputsStartedStep<>(
            new SkipEmptyIncrementalWorkStep(outputChangeListener, workInputListeners, skipEmptyWorkOutputsCleanerSupplier,
            new CaptureIncrementalStateBeforeExecutionStep<>(buildOperationRunner, classLoaderHierarchyHasher, outputSnapshotter, overlappingOutputDetector,
            new ValidateStep<>(virtualFileSystem, validationWarningRecorder,
            new ResolveChangesStep<>(changeDetector,
            new ResolveIncrementalCachingStateStep<>(buildCacheController, emitBuildCacheDebugLogging,
            new MarkSnapshottingInputsFinishedStep<>(
            new SkipUpToDateStep<>(
            new StoreExecutionStateStep<>(
            new BuildCacheStep<>(buildCacheController, deleter, fileSystemAccess, outputChangeListener,
            new ResolveInputChangesStep<>(
            new CaptureOutputsAfterExecutionStep<>(buildOperationRunner, buildId, outputSnapshotter, new OverlappingOutputsFilter(),
            new BroadcastChangingOutputsStep<>(outputChangeListener,
            new RemovePreviousOutputsStep<>(deleter, outputChangeListener,
            sharedExecutionPipeline
        )))))))))))))))));

        return new DefaultExecutionEngine(
            new IdentifyStep<>(buildOperationRunner,
            new IdentityCacheStep<>(buildOperationProgressEventEmitter,
            new ExecuteWorkBuildOperationFiringStep<>(buildOperationRunner,
            new ChoosePipelineStep<>(
                immutablePipeline,
                mutablePipeline
        )))));
        // CHECKSTYLE:ON
        // @formatter:on
    }
}
