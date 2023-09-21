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

package org.gradle.problems.transformers;

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemTransformer;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.problems.internal.OperationListener;

import java.util.Optional;

public class TaskPathLocationTransformer implements ProblemTransformer {

    private final BuildOperationAncestryTracker buildOperationAncestryTracker;
    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();
    private final OperationListener operationListener = new OperationListener();


    public TaskPathLocationTransformer(
        BuildOperationAncestryTracker buildOperationAncestryTracker,
        BuildOperationListenerManager buildOperationListenerManager
    ) {
        this.buildOperationAncestryTracker = buildOperationAncestryTracker;
        buildOperationListenerManager.addListener(operationListener);
    }

    @Override
    public Problem transform(Problem problem) {
        Optional<OperationIdentifier> executeTask = buildOperationAncestryTracker.findClosestMatchingAncestor(
            currentBuildOperationRef.getId(),
            id -> operationListener.getOp(id, ExecuteTaskBuildOperationDetails.class) != null
        );

        executeTask.ifPresent(id -> {
            try {
                // TODO: Fix the inaccessibility problem
//                ExecuteTaskBuildOperationDetails executeTaskDetails = operationListener.getOp(id, ExecuteTaskBuildOperationDetails.class);
//                Objects.requireNonNull(executeTaskDetails, "executeTaskDetails should not be null");
//                Path taskPath = executeTaskDetails.getTask().getIdentityPath();
//                problem.getWhere().add(new TaskPathLocation(taskPath));
            } catch (Exception ex) {
                throw new GradleException("Problem meanwhile reporting problem", ex);
            }
        });

        return problem;
    }
}
