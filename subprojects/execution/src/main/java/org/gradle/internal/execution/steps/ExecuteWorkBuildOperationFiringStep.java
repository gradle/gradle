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
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.operations.execution.ExecuteWorkBuildOperationType;

import javax.annotation.Nullable;
import java.util.Optional;

public class ExecuteWorkBuildOperationFiringStep<C extends IdentityContext, R extends CachingResult> extends BuildOperationStep<C, R> implements Step<C, R> {

    private final Step<? super C, R> delegate;

    public ExecuteWorkBuildOperationFiringStep(BuildOperationExecutor buildOperationExecutor, Step<C, R> delegate) {
        super(buildOperationExecutor);
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        return operation(operationContext -> {
                R result = delegate.execute(work, context);
                ExecuteWorkBuildOperationType.Result operationResult = new ExecuteWorkResult(result.getExecution());
                operationContext.setResult(operationResult);
                return result;
            },
            BuildOperationDescriptor
                .displayName("Execute Unit of Work")
                .details(new ExecuteWorkDetails(work)));
    }

    private class ExecuteWorkDetails implements ExecuteWorkBuildOperationType.Details {

        private final UnitOfWork work;

        public ExecuteWorkDetails(UnitOfWork work) {
            this.work = work;
        }

        @Override
        public String getWorkType() {
            return work.getWorkType();
        }

    }

    private static class ExecuteWorkResult implements ExecuteWorkBuildOperationType.Result {

        private final Try<ExecutionEngine.Execution> execution;

        public ExecuteWorkResult(Try<ExecutionEngine.Execution> execution) {
            this.execution = execution;
        }

        @Nullable
        @Override
        public String getSkipMessage() {
            return execution.map(ExecuteWorkResult::getSkipMessage).getOrMapFailure(f -> null);
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
    }
}
