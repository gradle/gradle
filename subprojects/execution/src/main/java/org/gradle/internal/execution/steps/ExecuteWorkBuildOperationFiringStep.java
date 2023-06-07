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

import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.operations.execution.ExecuteWorkBuildOperationType;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A step that executes a unit of work and wraps it into a {@link ExecuteWorkBuildOperationType} build operation.
 */
public class ExecuteWorkBuildOperationFiringStep<C extends IdentityContext, R extends CachingResult> extends BuildOperationStep<C, R> implements Step<C, R> {

    private final Step<? super C, R> delegate;

    public ExecuteWorkBuildOperationFiringStep(BuildOperationExecutor buildOperationExecutor, Step<C, R> delegate) {
        super(buildOperationExecutor);
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        return work.getBuildOperationWorkType()
            .map(workType -> operation(
                operationContext -> {
                    R result = delegate.execute(work, context);
                    ExecuteWorkBuildOperationType.Result operationResult = new ExecuteWorkResult(
                        result.getExecution(),
                        result.getCachingState(),
                        result.getReusedOutputOriginMetadata()
                    );
                    operationContext.setResult(operationResult);
                    return result;
                },
                BuildOperationDescriptor
                    .displayName("Execute unit of work")
                    .details(new ExecuteWorkDetails(workType))))
            .orElseGet(() -> delegate.execute(work, context));
    }

    private static class ExecuteWorkDetails implements ExecuteWorkBuildOperationType.Details {

        private final String workType;

        public ExecuteWorkDetails(String workType) {
            this.workType = workType;
        }

        @Nullable
        @Override
        public String getWorkType() {
            return workType;
        }

    }

    private static class ExecuteWorkResult implements ExecuteWorkBuildOperationType.Result {

        private final Try<ExecutionEngine.Execution> execution;
        private final CachingState cachingState;
        private final Optional<OriginMetadata> originMetadata;

        public ExecuteWorkResult(Try<ExecutionEngine.Execution> execution, CachingState cachingState, Optional<OriginMetadata> originMetadata) {
            this.execution = execution;
            this.cachingState = cachingState;
            this.originMetadata = originMetadata;
        }

        @Nullable
        @Override
        public String getSkipMessage() {
            return execution.map(ExecuteWorkResult::getSkipMessage).getOrMapFailure(f -> null);
        }

        @Nullable
        @Override
        public String getOriginBuildInvocationId() {
            return originMetadata.map(OriginMetadata::getBuildInvocationId).orElse(null);
        }

        @Nullable
        @Override
        public Long getOriginExecutionTime() {
            return originMetadata.map(metadata -> metadata.getExecutionTime().toMillis()).orElse(null);
        }

        @Nullable
        private static String getSkipMessage(ExecutionEngine.Execution execution) {
            switch (execution.getOutcome()) {
                case SHORT_CIRCUITED:
                    return "NO-SOURCE";
                case FROM_CACHE:
                    return "FROM-CACHE";
                case UP_TO_DATE:
                    return "UP-TO-DATE";
                case EXECUTED_INCREMENTALLY:
                case EXECUTED_NON_INCREMENTALLY:
                    return null;
                default:
                    throw new IllegalArgumentException("Unknown execution outcome: " + execution.getOutcome());
            }
        }

        @Nullable
        @Override
        public String getCachingDisabledReasonMessage() {
            return getCachingDisabledReason()
                .map(CachingDisabledReason::getMessage)
                .orElse(null);
        }

        @Nullable
        @Override
        public String getCachingDisabledReasonCategory() {
            return getCachingDisabledReason()
                .map(CachingDisabledReason::getCategory)
                .map(Enum::name)
                .orElse(null);
        }

        private Optional<CachingDisabledReason> getCachingDisabledReason() {
            return cachingState
                .whenDisabled()
                .map(CachingState.Disabled::getDisabledReasons)
                .map(reasons -> reasons.get(0));
        }
    }
}
