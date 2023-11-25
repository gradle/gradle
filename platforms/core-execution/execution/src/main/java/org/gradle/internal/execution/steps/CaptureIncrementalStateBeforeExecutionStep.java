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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import javax.annotation.Nullable;

public class CaptureIncrementalStateBeforeExecutionStep<C extends PreviousExecutionContext, R extends CachingResult> extends AbstractCaptureStateBeforeExecutionStep<C, R> {
    private final OutputSnapshotter outputSnapshotter;
    private final OverlappingOutputDetector overlappingOutputDetector;

    public CaptureIncrementalStateBeforeExecutionStep(
        BuildOperationExecutor buildOperationExecutor,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        OutputSnapshotter outputSnapshotter,
        OverlappingOutputDetector overlappingOutputDetector,
        Step<? super BeforeExecutionContext, ? extends R> delegate
    ) {
        super(buildOperationExecutor, classLoaderHierarchyHasher, delegate);
        this.outputSnapshotter = outputSnapshotter;
        this.overlappingOutputDetector = overlappingOutputDetector;
    }

    @Override
    protected ImmutableSortedMap<String, FileSystemSnapshot> captureOutputSnapshots(UnitOfWork work, WorkspaceContext context) {
        return outputSnapshotter.snapshotOutputs(work, context.getWorkspace());
    }

    @Nullable
    @Override
    protected OverlappingOutputs detectOverlappingOutputs(UnitOfWork work, PreviousExecutionContext context, ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshots) {
        if (work.getOverlappingOutputHandling() == UnitOfWork.OverlappingOutputHandling.IGNORE_OVERLAPS) {
            return null;
        }
        ImmutableSortedMap<String, FileSystemSnapshot> previousOutputSnapshots = context.getPreviousExecutionState()
            .map(PreviousExecutionState::getOutputFilesProducedByWork)
            .orElse(ImmutableSortedMap.of());
        return overlappingOutputDetector.detect(previousOutputSnapshots, unfilteredOutputSnapshots);
    }
}
