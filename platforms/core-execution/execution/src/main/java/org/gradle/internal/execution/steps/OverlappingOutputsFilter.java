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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.util.Optional;

import static org.gradle.internal.execution.history.impl.OutputSnapshotUtil.filterOutputsAfterExecution;

public class OverlappingOutputsFilter implements AfterExecutionOutputFilter<BeforeExecutionContext, BeforeExecutionState> {
    @Override
    public Optional<BeforeExecutionState> getBeforeExecutionState(BeforeExecutionContext context) {
        return context.getBeforeExecutionState();
    }

    @Override
    public ImmutableSortedMap<String, FileSystemSnapshot> filterOutputs(BeforeExecutionContext context, BeforeExecutionState beforeExecutionState, ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshotsAfterExecution) {
        if (beforeExecutionState.getDetectedOverlappingOutputs().isPresent()) {
            ImmutableSortedMap<String, FileSystemSnapshot> previousExecutionOutputSnapshots = context.getPreviousExecutionState()
                .map(PreviousExecutionState::getOutputFilesProducedByWork)
                .orElse(ImmutableSortedMap.of());

            ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshotsBeforeExecution = context.getBeforeExecutionState()
                .map(BeforeExecutionState::getOutputFileLocationSnapshots)
                .orElse(ImmutableSortedMap.of());

            return filterOutputsAfterExecution(previousExecutionOutputSnapshots, unfilteredOutputSnapshotsBeforeExecution, unfilteredOutputSnapshotsAfterExecution);
        } else {
            return unfilteredOutputSnapshotsAfterExecution;
        }
    }
}
