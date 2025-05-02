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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult;
import org.gradle.api.internal.tasks.FilePropertyVisitState;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.hash.HashCode;
import org.gradle.operations.dependencies.transforms.SnapshotTransformInputsBuildOperationType;
import org.gradle.operations.execution.FilePropertyVisitor;

import java.util.Map;
import java.util.Set;

public class SnapshotTransformInputsBuildOperationResult extends BaseSnapshotInputsBuildOperationResult implements SnapshotTransformInputsBuildOperationType.Result {

    private final Set<InputFilePropertySpec> inputFilePropertySpecs;

    public SnapshotTransformInputsBuildOperationResult(CachingState cachingState, Set<InputFilePropertySpec> inputFilePropertySpecs) {
        super(cachingState);
        this.inputFilePropertySpecs = inputFilePropertySpecs;
    }

    @Override
    public void visitInputFileProperties(FilePropertyVisitor visitor) {
        getBeforeExecutionState()
            .map(BeforeExecutionState::getInputFileProperties)
            .ifPresent(inputFileProperties -> FilePropertyVisitState.visitInputFileProperties(inputFileProperties, visitor, inputFilePropertySpecs));
    }

    @Override
    protected Map<String, Object> fileProperties() {
        FilePropertyCollectingVisitor visitor = new FilePropertyCollectingVisitor();
        visitInputFileProperties(visitor);
        return visitor.getFileProperties();
    }

    private static class FilePropertyCollectingVisitor extends BaseFilePropertyCollectingVisitor<FilePropertyVisitor.VisitState> implements FilePropertyVisitor {

        @Override
        protected Property createProperty(FilePropertyVisitor.VisitState state) {
            return new Property(HashCode.fromBytes(state.getPropertyHashBytes()).toString(), state.getPropertyAttributes());
        }
    }
}
