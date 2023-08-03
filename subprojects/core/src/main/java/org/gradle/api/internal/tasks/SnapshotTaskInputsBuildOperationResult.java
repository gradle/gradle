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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep;
import org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * This operation represents the work of analyzing the task's inputs plus the calculating the cache key.
 *
 * <p>
 * These two operations should be captured separately, but for historical reasons we don't yet do that.
 * To reproduce this composite operation we capture across executors by starting an operation
 * in {@link MarkSnapshottingInputsStartedStep} and finished in {@link MarkSnapshottingInputsFinishedStep}.
 * </p>
 */
public class SnapshotTaskInputsBuildOperationResult extends BaseSnapshotInputsBuildOperationResult implements SnapshotTaskInputsBuildOperationType.Result {

    private final Set<InputFilePropertySpec> inputFilePropertySpecs;

    public SnapshotTaskInputsBuildOperationResult(CachingState cachingState, Set<InputFilePropertySpec> inputFilePropertySpecs) {
        super(cachingState);
        this.inputFilePropertySpecs = inputFilePropertySpecs;
    }


    @Override
    public void visitInputFileProperties(final SnapshotTaskInputsBuildOperationType.Result.InputFilePropertyVisitor visitor) {
        getBeforeExecutionState()
            .map(BeforeExecutionState::getInputFileProperties)
            .ifPresent(inputFileProperties -> SnapshotTaskInputsResultFilePropertyVisitState.visitInputFileProperties(inputFileProperties, visitor, inputFilePropertySpecs));
    }

    @Nullable
    @SuppressWarnings("deprecation")
    @Override
    public Set<String> getInputPropertiesLoadedByUnknownClassLoader() {
        return null;
    }

    @Override
    protected Map<String, Object> fileProperties() {
        FilePropertyCollectingVisitor visitor = new FilePropertyCollectingVisitor();
        visitInputFileProperties(visitor);
        return visitor.getFileProperties();
    }

    private static class FilePropertyCollectingVisitor extends BaseFilePropertyCollectingVisitor<VisitState> implements InputFilePropertyVisitor {

        @SuppressWarnings("deprecation")
        @Override
        protected Property createProperty(VisitState state) {
            return new TaskProperty(HashCode.fromBytes(state.getPropertyHashBytes()).toString(), state.getPropertyNormalizationStrategyName(), state.getPropertyAttributes());
        }

        static class TaskProperty extends Property {
            private final String normalization;

            public TaskProperty(String hash, String normalization, Set<String> attributes) {
                super(hash, attributes);
                this.normalization = normalization;
            }

            public String getNormalization() {
                return normalization;
            }
        }
    }

}
