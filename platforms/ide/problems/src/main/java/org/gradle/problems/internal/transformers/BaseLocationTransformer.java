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

package org.gradle.problems.internal.transformers;

import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemCloneBuilder;
import org.gradle.api.problems.ProblemTransformer;
import org.gradle.api.problems.locations.ProblemLocation;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.problems.internal.OperationListener;

import javax.annotation.Nonnull;
import java.util.Optional;

public abstract class BaseLocationTransformer implements ProblemTransformer {
    protected final BuildOperationAncestryTracker buildOperationAncestryTracker;
    protected final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();
    protected final OperationListener operationListener;

    public BaseLocationTransformer(BuildOperationAncestryTracker buildOperationAncestryTracker, OperationListener operationListener) {
        this.buildOperationAncestryTracker = buildOperationAncestryTracker;
        this.operationListener = operationListener;
    }

    protected static Problem cloneWithLocation(Problem problem, ProblemLocation location) {
        ProblemCloneBuilder cloneBuilder = problem.toBuilder();
        cloneBuilder.getLocations().add(location);
        return cloneBuilder.build();
    }

    @Nonnull
    protected Optional<OperationIdentifier> getExecuteTask(Class<?> operationDetailsClass) {
        return buildOperationAncestryTracker.findClosestMatchingAncestor(
            currentBuildOperationRef.getId(),
            id -> operationListener.getOp(id, operationDetailsClass) != null
        );
    }

}
