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

package org.gradle.api.problems.internal;

import org.gradle.api.GradleException;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.problems.ProblemBuilderSpec;
import org.gradle.api.problems.ReportableProblem;
import org.gradle.api.problems.locations.TaskPathLocation;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

import java.util.Optional;

@ServiceScope(Scope.Global.class)
public class DefaultProblems implements InternalProblems {
    private final BuildOperationProgressEventEmitter buildOperationProgressEventEmitter;
    private final BuildOperationAncestryTracker buildOperationAncestryTracker;

    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();
    private final OperationListener operationListener = new OperationListener();

    public DefaultProblems(
        BuildOperationProgressEventEmitter buildOperationProgressEventEmitter,
        BuildOperationAncestryTracker buildOperationAncestryTracker,
        BuildOperationListenerManager buildOperationListenerManager
    ) {
        this.buildOperationProgressEventEmitter = buildOperationProgressEventEmitter;
        this.buildOperationAncestryTracker = buildOperationAncestryTracker;
        buildOperationListenerManager.addListener(operationListener);

    }

    @Override
    public DefaultBuildableProblemBuilder createProblemBuilder() {
        return new DefaultBuildableProblemBuilder(this);
    }

    @Override
    public RuntimeException throwing(ProblemBuilderSpec action) {
        DefaultBuildableProblemBuilder defaultProblemBuilder = createProblemBuilder();
        action.apply(defaultProblemBuilder);
        throw throwError(defaultProblemBuilder.getException(), defaultProblemBuilder.build());
    }

    @Override
    public RuntimeException rethrowing(RuntimeException e, ProblemBuilderSpec action) {
        DefaultBuildableProblemBuilder defaultProblemBuilder = createProblemBuilder();
        ProblemBuilder problemBuilder = action.apply(defaultProblemBuilder);
        problemBuilder.withException(e);
        throw throwError(e, defaultProblemBuilder.build());
    }

    @Override
    public ReportableProblem createProblem(ProblemBuilderSpec action) {
        DefaultBuildableProblemBuilder defaultProblemBuilder = createProblemBuilder();
        action.apply(defaultProblemBuilder);
        return defaultProblemBuilder.build();
    }

    public RuntimeException throwError(RuntimeException exception, Problem problem) {
        reportAsProgressEvent(problem);
        throw exception;
    }

    @Override
    public void reportAsProgressEvent(Problem problem) {
//        System.out.println("XXX: " + buildOperationAncestryTracker
//            .findClosestExistingAncestor(
//                currentBuildOperationRef.getId(),
//                operationListener.runningOps::get
//            ));
        // TODO: This would require a new dependency on "core", which would normally cause a cycle.
        //       We should consider moving this to a separate project.
        Optional<?> executeTask = buildOperationAncestryTracker.findClosestMatchingAncestor(
            currentBuildOperationRef.getId(),
            id -> operationListener
                .runningOps
                .get(id)
                .getClass()
                .getName()
                .equals("org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails")
        );

        executeTask.ifPresent(id -> {
            try {
                Object executeTaskDetails = operationListener
                    .runningOps
                    .get(id);

                Object taskInternal = executeTaskDetails
                    .getClass()
                    .getDeclaredMethod("getTask")
                    .invoke(executeTaskDetails);

                // TODO: Implement this reflective access by some means other than reflection.
                // Call the "getIdentityPath" method on the taskInternal object by reflection.
                Path taskPath = (Path) taskInternal
                    .getClass()
                    .getMethod("getIdentityPath")
                    .invoke(taskInternal);

                problem.getWhere().add(new TaskPathLocation(taskPath));
            } catch (Exception ex) {
                throw new GradleException("Problem meanwhile reporting problem", ex);
            }
        });

//        operationListener.runningOps.forEach((k,v) -> System.out.println(k + ": " + v));
        buildOperationProgressEventEmitter.emitNowIfCurrent(new DefaultProblemProgressDetails(problem));
    }
}
