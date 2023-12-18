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
package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.util.Optional;

public interface AfterExecutionOutputFilter<C extends WorkspaceContext, B> {
    Optional<B> getBeforeExecutionState(C context);
    ImmutableSortedMap<String, FileSystemSnapshot> filterOutputs(C context, B beforeExecutionState, ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshotsAfterExecution);

    AfterExecutionOutputFilter<WorkspaceContext, Object> NO_FILTER = new AfterExecutionOutputFilter<WorkspaceContext, Object>() {
        @Override
        public Optional<Object> getBeforeExecutionState(WorkspaceContext context) {
            return Optional.of(Boolean.TRUE);
        }

        @Override
        public ImmutableSortedMap<String, FileSystemSnapshot> filterOutputs(WorkspaceContext context, Object beforeExecutionState, ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshotsAfterExecution) {
            return outputSnapshotsAfterExecution;
        }
    };
}
