/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.internal.DefaultProblemProgressDetails;
import org.gradle.api.problems.internal.DefaultProblemsSummaryProgressDetails;
import org.gradle.api.problems.internal.ProblemLookup;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.types.AbstractOperationResult;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultFailureResult;
import org.gradle.internal.build.event.types.DefaultOperationDescriptor;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultRootOperationDescriptor;
import org.gradle.internal.build.event.types.DefaultSuccessResult;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.launcher.exec.RunBuildBuildOperationType;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.InternalFailure;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * Build listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 2.5
 */
@NonNullApi
class ClientForwardingBuildOperationListener implements BuildOperationListener {

    protected final ProgressEventConsumer eventConsumer;
    private final boolean problemsRequested;
    private final boolean genericRequested;
    private final boolean rootRequested;
    private final Supplier<OperationIdentifier> operationIdentifierSupplier;

    ClientForwardingBuildOperationListener(ProgressEventConsumer eventConsumer, BuildEventSubscriptions buildEventSubscriptions, Supplier<OperationIdentifier> operationIdentifierSupplier) {
        this.eventConsumer = eventConsumer;
        this.problemsRequested = buildEventSubscriptions.isRequested(OperationType.PROBLEMS);
        this.genericRequested = buildEventSubscriptions.isRequested(OperationType.GENERIC);
        this.rootRequested = buildEventSubscriptions.isRequested(OperationType.ROOT);
        this.operationIdentifierSupplier = operationIdentifierSupplier;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        // RunBuildBuildOperationType.Details is the type of the details object associated with the root build operation
        if ((rootRequested && buildOperation.getDetails() instanceof RunBuildBuildOperationType.Details) || genericRequested) {
            eventConsumer.started(new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), toBuildOperationDescriptor(buildOperation)));
        }
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        if (problemsRequested) {
            Object details = progressEvent.getDetails();
            if (details instanceof DefaultProblemProgressDetails) {
                eventConsumer.progress(ProblemsProgressEventUtils.createProblemEvent(eventConsumer.findStartedParentId(buildOperationId), (DefaultProblemProgressDetails) details, operationIdentifierSupplier));
            } else if (details instanceof DefaultProblemsSummaryProgressDetails) {
                eventConsumer.progress(ProblemsProgressEventUtils.createProblemSummaryEvent(eventConsumer.findStartedParentId(buildOperationId), (DefaultProblemsSummaryProgressDetails) details, operationIdentifierSupplier));
            }
        }
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent result) {
        // RunBuildBuildOperationType.Details is the type of the details object associated with the root build operation
        if (rootRequested && buildOperation.getDetails() instanceof RunBuildBuildOperationType.Details) {
            ProblemLookup problemLookup = ((RunBuildBuildOperationType.Details) buildOperation.getDetails()).getProblemLookup();
            eventConsumer.finished(new DefaultOperationFinishedProgressEvent(result.getEndTime(), createRootOperationDescriptor(buildOperation), ClientForwardingBuildOperationListener.toOperationResult(result, problemLookup)));
        } else if (genericRequested) {
            eventConsumer.finished(new DefaultOperationFinishedProgressEvent(result.getEndTime(), toBuildOperationDescriptor(buildOperation), toOperationResult(result)));
        }
    }

    public DefaultRootOperationDescriptor createRootOperationDescriptor(BuildOperationDescriptor buildOperation) {
        OperationIdentifier id = buildOperation.getId();
        String name = buildOperation.getName();
        String displayName = buildOperation.getDisplayName();
        return new DefaultRootOperationDescriptor(id, name, displayName, null);
    }

    protected DefaultOperationDescriptor toBuildOperationDescriptor(BuildOperationDescriptor buildOperation) {
        OperationIdentifier id = buildOperation.getId();
        String name = buildOperation.getName();
        String displayName = buildOperation.getDisplayName();
        OperationIdentifier parentId = eventConsumer.findStartedParentId(buildOperation);
        return new DefaultOperationDescriptor(id, name, displayName, parentId);
    }

    static AbstractOperationResult toOperationResult(OperationFinishEvent result) {
        return toOperationResult(result, null);
    }

    static AbstractOperationResult toOperationResult(OperationFinishEvent result, @Nullable ProblemLookup problemLookup) {
        Throwable failure = result.getFailure();
        long startTime = result.getStartTime();
        long endTime = result.getEndTime();
        if (failure != null) {
            if (problemLookup != null) {
                InternalFailure rootFailure = DefaultFailure.fromThrowable(failure, problemLookup, ProblemsProgressEventUtils::createDefaultProblemDetails);
                return new DefaultFailureResult(startTime, endTime, Collections.singletonList(rootFailure));
            } else {
                return new DefaultFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)));
            }
        }
        return new DefaultSuccessResult(startTime, endTime);
    }
}
