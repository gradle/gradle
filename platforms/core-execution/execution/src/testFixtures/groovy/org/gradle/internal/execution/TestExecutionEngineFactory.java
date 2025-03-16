/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector;
import org.gradle.internal.execution.impl.DefaultExecutionEngine;
import org.gradle.internal.execution.steps.AssignMutableWorkspaceStep;
import org.gradle.internal.execution.steps.BroadcastChangingOutputsStep;
import org.gradle.internal.execution.steps.CaptureIncrementalStateBeforeExecutionStep;
import org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep;
import org.gradle.internal.execution.steps.ExecuteStep;
import org.gradle.internal.execution.steps.IdentifyStep;
import org.gradle.internal.execution.steps.IdentityCacheStep;
import org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep;
import org.gradle.internal.execution.steps.PreCreateOutputParentsStep;
import org.gradle.internal.execution.steps.RemovePreviousOutputsStep;
import org.gradle.internal.execution.steps.ResolveChangesStep;
import org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep;
import org.gradle.internal.execution.steps.ResolveInputChangesStep;
import org.gradle.internal.execution.steps.SkipUpToDateStep;
import org.gradle.internal.execution.steps.StoreExecutionStateStep;
import org.gradle.internal.execution.steps.ValidateStep;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.NoOpBuildOperationProgressEventEmitter;
import org.gradle.internal.vfs.VirtualFileSystem;

import static org.gradle.internal.execution.steps.AfterExecutionOutputFilter.NO_FILTER;

/**
 * Note that this is kept as a Java source file as a workaround for IntelliJ editing
 * being very slow with deeply nested constructor calls in Groovy source files.
 */
public class TestExecutionEngineFactory {
    public static ExecutionEngine createExecutionEngine(
        UniqueId buildId,
        BuildCacheController buildCacheController,
        BuildOperationRunner buildOperationRunner,
        ClassLoaderHierarchyHasher classloaderHierarchyHasher,
        Deleter deleter,
        ExecutionStateChangeDetector changeDetector,
        OutputChangeListener outputChangeListener,
        OutputSnapshotter outputSnapshotter,
        OverlappingOutputDetector overlappingOutputDetector,
        ValidateStep.ValidationWarningRecorder validationWarningReporter,
        VirtualFileSystem virtualFileSystem
    ) {
        NoOpBuildOperationProgressEventEmitter progressEventEmitter = new NoOpBuildOperationProgressEventEmitter();
        // @formatter:off
        return new DefaultExecutionEngine(
            new IdentifyStep<>(buildOperationRunner,
            new IdentityCacheStep<>(progressEventEmitter,
            new AssignMutableWorkspaceStep<>(
            new LoadPreviousExecutionStateStep<>(
            new CaptureIncrementalStateBeforeExecutionStep<>(buildOperationRunner, classloaderHierarchyHasher, outputSnapshotter, overlappingOutputDetector,
            new ValidateStep<>(virtualFileSystem, validationWarningReporter,
            new ResolveChangesStep<>(changeDetector,
            new ResolveIncrementalCachingStateStep<>(buildCacheController, false,
            new SkipUpToDateStep<>(
            new StoreExecutionStateStep<>(
            new ResolveInputChangesStep<>(
            new CaptureOutputsAfterExecutionStep<>(buildOperationRunner, buildId, outputSnapshotter, NO_FILTER,
            new BroadcastChangingOutputsStep<>(outputChangeListener,
            new PreCreateOutputParentsStep<>(
            new RemovePreviousOutputsStep<>(deleter, outputChangeListener,
            new ExecuteStep<>(buildOperationRunner
        )))))))))))))))));
        // @formatter:on
    }
}
