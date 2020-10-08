/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.InputChangesContext;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Result.ExecutionResult;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.operations.CallableBuildOperation;

import javax.annotation.Nonnull;

public class ExecuteStep<C extends InputChangesContext> implements Step<C, Result> {

    private final BuildOperationExecutor buildOperationExecutor;

    public ExecuteStep(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public Result execute(C context) {
        UnitOfWork work = context.getWork();
        return buildOperationExecutor.call(new CallableBuildOperation<Result>() {
            @Override
            public Result call(BuildOperationContext operationContext) {
                Result result = executeOperation(work, context);
                operationContext.setResult(Operation.Result.INSTANCE);
                return result;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor
                    .displayName("Executing " + work.getDisplayName())
                    .details(Operation.Details.INSTANCE);
            }
        });
    }

    @Nonnull
    private Result executeOperation(UnitOfWork work, C context) {
        try {
            ExecutionResult executionResult = context.getInputChanges()
                .map(inputChanges -> determineResult(work.execute(inputChanges, context), inputChanges.isIncremental()))
                .orElseGet(() -> determineResult(work.execute(null, context), false));
            return () -> Try.successful(executionResult);
        } catch (Throwable t) {
            return () -> Try.failure(t);
        }
    }

    private static ExecutionResult determineResult(UnitOfWork.WorkOutput workOutput, boolean incremental) {
        ExecutionOutcome outcome;
        switch (workOutput.getDidWork()) {
            case DID_NO_WORK:
                outcome = ExecutionOutcome.UP_TO_DATE;
                break;
            case DID_WORK:
                outcome = incremental
                    ? ExecutionOutcome.EXECUTED_INCREMENTALLY
                    : ExecutionOutcome.EXECUTED_NON_INCREMENTALLY;
                break;
            default:
                throw new AssertionError();
        }
        return new ExecutionResult() {
            @Override
            public ExecutionOutcome getOutcome() {
                return outcome;
            }

            @Override
            public Object getOutput() {
                return workOutput.getOutput();
            }
        };
    }

    /*
     * This operation is only used here temporarily. Should be replaced with a more stable operation in the long term.
     */
    public interface Operation extends BuildOperationType<Operation.Details, Operation.Result> {
        interface Details {
            Operation.Details INSTANCE = new Operation.Details() {};
        }

        interface Result {
            Operation.Result INSTANCE = new Operation.Result() {};
        }
    }
}
