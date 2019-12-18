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

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.DefaultExecutionHistoryCacheAccess;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.caching.internal.command.BuildCacheCommandFactory;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.execution.plan.DefaultPlanExecutor;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.ExecutionRequestContext;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.ExecutionHistoryCacheAccess;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector;
import org.gradle.internal.execution.history.impl.DefaultExecutionHistoryStore;
import org.gradle.internal.execution.history.impl.DefaultOutputFilesRepository;
import org.gradle.internal.execution.impl.DefaultWorkExecutor;
import org.gradle.internal.execution.steps.BroadcastChangingOutputsStep;
import org.gradle.internal.execution.steps.CacheStep;
import org.gradle.internal.execution.steps.CancelExecutionStep;
import org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep;
import org.gradle.internal.execution.steps.CatchExceptionStep;
import org.gradle.internal.execution.steps.CleanupOutputsStep;
import org.gradle.internal.execution.steps.CreateOutputsStep;
import org.gradle.internal.execution.steps.ExecuteStep;
import org.gradle.internal.execution.steps.LoadExecutionStateStep;
import org.gradle.internal.execution.steps.RecordOutputsStep;
import org.gradle.internal.execution.steps.ResolveCachingStateStep;
import org.gradle.internal.execution.steps.ResolveChangesStep;
import org.gradle.internal.execution.steps.ResolveInputChangesStep;
import org.gradle.internal.execution.steps.SkipEmptyWorkStep;
import org.gradle.internal.execution.steps.SkipUpToDateStep;
import org.gradle.internal.execution.steps.SnapshotOutputsStep;
import org.gradle.internal.execution.steps.StoreExecutionStateStep;
import org.gradle.internal.execution.steps.TimeoutStep;
import org.gradle.internal.execution.steps.ValidateStep;
import org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep;
import org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep;
import org.gradle.internal.execution.timeout.TimeoutHandler;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputDetector;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.resources.SharedResourceLeaseRegistry;
import org.gradle.internal.scan.config.BuildScanPluginApplied;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.GradleVersion;

import java.util.Collections;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class ExecutionGradleServices {
    ExecutionHistoryCacheAccess createCacheAccess(Gradle gradle, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new DefaultExecutionHistoryCacheAccess(gradle, cacheRepository, inMemoryCacheDecoratorFactory);
    }

    ExecutionHistoryStore createExecutionHistoryStore(ExecutionHistoryCacheAccess executionHistoryCacheAccess, StringInterner stringInterner) {
        return new DefaultExecutionHistoryStore(executionHistoryCacheAccess, stringInterner);
    }

    OutputFilesRepository createOutputFilesRepository(CacheRepository cacheRepository, Gradle gradle, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        PersistentCache cacheAccess = cacheRepository
            .cache(gradle, "buildOutputCleanup")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withDisplayName("Build Output Cleanup Cache")
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .withProperties(Collections.singletonMap("gradle.version", GradleVersion.current().getVersion()))
            .open();
        return new DefaultOutputFilesRepository(cacheAccess, inMemoryCacheDecoratorFactory);
    }

    PlanExecutor createPlanExecutor(
        ParallelismConfigurationManager parallelismConfigurationManager,
        ExecutorFactory executorFactory,
        WorkerLeaseService workerLeaseService,
        BuildCancellationToken cancellationToken,
        ResourceLockCoordinationService coordinationService) {
        int parallelThreads = parallelismConfigurationManager.getParallelismConfiguration().getMaxWorkerCount();
        if (parallelThreads < 1) {
            throw new IllegalStateException(String.format("Cannot create executor for requested number of worker threads: %s.", parallelThreads));
        }

        // TODO: Make plan executor respond to changes in parallelism configuration
        return new DefaultPlanExecutor(
            parallelismConfigurationManager.getParallelismConfiguration(),
            executorFactory,
            workerLeaseService,
            cancellationToken,
            coordinationService
        );
    }

    OutputChangeListener createOutputChangeListener(ListenerManager listenerManager) {
        return listenerManager.getBroadcaster(OutputChangeListener.class);
    }

    public WorkExecutor<ExecutionRequestContext, CachingResult> createWorkExecutor(
        BuildCacheCommandFactory buildCacheCommandFactory,
        BuildCacheController buildCacheController,
        BuildCancellationToken cancellationToken,
        BuildInvocationScopeId buildInvocationScopeId,
        BuildOperationExecutor buildOperationExecutor,
        BuildScanPluginApplied buildScanPlugin,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        Deleter deleter,
        ExecutionStateChangeDetector changeDetector,
        OutputChangeListener outputChangeListener,
        OutputFilesRepository outputFilesRepository,
        OverlappingOutputDetector overlappingOutputDetector,
        TimeoutHandler timeoutHandler,
        ValidateStep.ValidationWarningReporter validationWarningReporter,
        ValueSnapshotter valueSnapshotter
    ) {
        // @formatter:off
        return new DefaultWorkExecutor<>(
            new LoadExecutionStateStep<>(
            new MarkSnapshottingInputsStartedStep<>(
            new SkipEmptyWorkStep<>(
            new ValidateStep<>(validationWarningReporter,
            new CaptureStateBeforeExecutionStep(buildOperationExecutor, classLoaderHierarchyHasher, valueSnapshotter, overlappingOutputDetector,
            new ResolveCachingStateStep(buildCacheController, buildScanPlugin.isBuildScanPluginApplied(),
            new MarkSnapshottingInputsFinishedStep<>(
            new ResolveChangesStep<>(changeDetector,
            new SkipUpToDateStep<>(
            new RecordOutputsStep<>(outputFilesRepository,
            new StoreExecutionStateStep<>(
            new CacheStep(buildCacheController, buildCacheCommandFactory, deleter,
            new BroadcastChangingOutputsStep<>(outputChangeListener,
            new SnapshotOutputsStep<>(buildOperationExecutor, buildInvocationScopeId.getId(),
            new CreateOutputsStep<>(
            new CatchExceptionStep<>(
            new TimeoutStep<>(timeoutHandler,
            new CancelExecutionStep<>(cancellationToken,
            new ResolveInputChangesStep<>(
            new CleanupOutputsStep<>(deleter, outputChangeListener,
            new ExecuteStep<>(
        ))))))))))))))))))))));
        // @formatter:on
    }

    SharedResourceLeaseRegistry createSharedResourceLeaseRegistry(ResourceLockCoordinationService coordinationService) {
        return new SharedResourceLeaseRegistry(coordinationService);
    }
}
