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

package org.gradle.problems.internal.emitters;

import org.gradle.api.Incubating;
import org.gradle.api.problems.internal.DefaultProblemBuilder;
import org.gradle.api.problems.internal.DefaultProblemProgressDetails;
import org.gradle.api.problems.internal.Problem;
import org.gradle.api.problems.internal.ProblemEmitter;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Emits problems as build operation progress events.
 *
 * @since 8.6
 */
@Incubating
public class BuildOperationBasedProblemEmitter implements ProblemEmitter, BuildOperationListener {

    private final Map<OperationIdentifier, String> taskNames = new HashMap<>();
    private final BuildOperationProgressEventEmitter eventEmitter;
    private final BuildOperationAncestryTracker ancestryTracker;

    public BuildOperationBasedProblemEmitter(
        BuildOperationProgressEventEmitter eventEmitter,
        BuildOperationAncestryTracker ancestryTracker,
        BuildOperationListenerManager buildOperationListenerManager
    ) {
        this.eventEmitter = eventEmitter;
        this.ancestryTracker = ancestryTracker;

        buildOperationListenerManager.addListener(this);
    }

    @SuppressWarnings("unused")
    @Override
    public void emit(Problem problem, @Nullable OperationIdentifier id) {
        // Conditionally, if we can find a task name for the operation, add a location to the problem
        Problem enrichedProblem = ancestryTracker
            .findClosestMatchingAncestor(id, taskNames::containsKey)
            .map(taskNames::get)
            .map(taskName -> new DefaultProblemBuilder(problem)
                .taskPathLocation(taskName)
                .build())
            .orElse(problem);

        // Emit the problem as a progress event
        eventEmitter.emitNow(id, new DefaultProblemProgressDetails(enrichedProblem));
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
//        Object details = buildOperation.getDetails();
//        if (details instanceof ExecuteTaskBuildOperationDetails) {
//            ExecuteTaskBuildOperationDetails taskDetails = (ExecuteTaskBuildOperationDetails) details;
//            taskNames.put(buildOperation.getId(), taskDetails.getBuildPath());
//        }
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
        // No-op: we don't care about progress events
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
//        taskNames.remove(buildOperation.getId());
    }

}
