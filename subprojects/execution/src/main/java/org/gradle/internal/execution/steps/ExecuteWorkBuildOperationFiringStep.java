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

import com.google.common.collect.ImmutableList;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.operations.execution.ExecuteWorkBuildOperationType;

import javax.annotation.Nullable;
import java.util.List;
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
                        result.getReusedOutputOriginMetadata(),
                        result.getExecutionReasons()
                    );
                    operationContext.setResult(operationResult);
                    result.getExecution().getFailure().ifPresent(operationContext::failed);
                    return result;
                },
                BuildOperationDescriptor
                    .displayName("Execute unit of work")
                    .details(new ExecuteWorkDetails(workType, context.getIdentity().getUniqueId()))))
            .orElseGet(() -> delegate.execute(work, context));
    }

    private static class ExecuteWorkDetails implements ExecuteWorkBuildOperationType.Details {

        private final String workType;
        private final String identity;

        public ExecuteWorkDetails(String workType, String identity) {
            this.workType = workType;
            this.identity = identity;
        }

        @Nullable
        @Override
        public String getWorkType() {
            return workType;
        }

        @Override
        public String getIdentity() {
            return identity;
        }

    }

    private static class ExecuteWorkResult implements ExecuteWorkBuildOperationType.Result {

        private final Try<ExecutionEngine.Execution> execution;
        private final CachingState cachingState;
        private final Optional<OriginMetadata> originMetadata;
        private final ImmutableList<String> executionReasons;

        public ExecuteWorkResult(
            Try<ExecutionEngine.Execution> execution,
            CachingState cachingState,
            Optional<OriginMetadata> originMetadata,
            ImmutableList<String> executionReasons
        ) {
            this.execution = execution;
            this.cachingState = cachingState;
            this.originMetadata = originMetadata;
            this.executionReasons = executionReasons;
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

        @Override
        public List<String> getExecutionReasons() {
            return executionReasons;
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
                .map(ExecuteWorkResult::convertNoCacheReasonCategory)
                .map(Enum::name)
                .orElse(null);
        }

        private static org.gradle.operations.execution.CachingDisabledReasonCategory convertNoCacheReasonCategory(CachingDisabledReasonCategory category) {
            switch (category) {
                case UNKNOWN:
                    return org.gradle.operations.execution.CachingDisabledReasonCategory.UNKNOWN;
                case BUILD_CACHE_DISABLED:
                    return org.gradle.operations.execution.CachingDisabledReasonCategory.BUILD_CACHE_DISABLED;
                case NOT_CACHEABLE:
                    return org.gradle.operations.execution.CachingDisabledReasonCategory.NOT_CACHEABLE;
                case ENABLE_CONDITION_NOT_SATISFIED:
                    return org.gradle.operations.execution.CachingDisabledReasonCategory.CACHE_IF_SPEC_NOT_SATISFIED;
                case DISABLE_CONDITION_SATISFIED:
                    return org.gradle.operations.execution.CachingDisabledReasonCategory.DO_NOT_CACHE_IF_SPEC_SATISFIED;
                case NO_OUTPUTS_DECLARED:
                    return org.gradle.operations.execution.CachingDisabledReasonCategory.NO_OUTPUTS_DECLARED;
                case NON_CACHEABLE_OUTPUT:
                    return org.gradle.operations.execution.CachingDisabledReasonCategory.NON_CACHEABLE_TREE_OUTPUT;
                case OVERLAPPING_OUTPUTS:
                    return org.gradle.operations.execution.CachingDisabledReasonCategory.OVERLAPPING_OUTPUTS;
                case VALIDATION_FAILURE:
                    return org.gradle.operations.execution.CachingDisabledReasonCategory.VALIDATION_FAILURE;
                default:
                    throw new AssertionError();
            }
        }

        private Optional<CachingDisabledReason> getCachingDisabledReason() {
            return cachingState
                .whenDisabled()
                .map(CachingState.Disabled::getDisabledReasons)
                .map(reasons -> reasons.get(0));
        }
    }
}
