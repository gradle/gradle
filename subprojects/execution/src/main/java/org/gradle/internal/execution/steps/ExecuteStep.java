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

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.work.InputChanges;

import java.io.File;
import java.time.Duration;
import java.util.Optional;

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.EXECUTED_INCREMENTALLY;
import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY;
import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE;

public class ExecuteStep<C extends ChangingOutputsContext> implements Result.Step<C> {

    private final BuildOperationExecutor buildOperationExecutor;

    public ExecuteStep(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public <T> Result<T> execute(UnitOfWork<T> work, C context) {
        return buildOperationExecutor.call(new CallableBuildOperation<Result<T>>() {
            @Override
            public Result<T> call(BuildOperationContext operationContext) {
                Result<T> result = executeInternal(work, context);
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

    private static <T> Result<T> executeInternal(UnitOfWork<T> work, InputChangesContext context) {
        UnitOfWork.ExecutionRequest executionRequest = new UnitOfWork.ExecutionRequest() {
            @Override
            public File getWorkspace() {
                return context.getWorkspace();
            }

            @Override
            public Optional<InputChangesInternal> getInputChanges() {
                return context.getInputChanges();
            }

            @Override
            public Optional<ImmutableSortedMap<String, FileSystemSnapshot>> getPreviouslyProducedOutputs() {
                return context.getPreviousExecutionState()
                    .map(PreviousExecutionState::getOutputFilesProducedByWork);
            }
        };
        UnitOfWork.WorkOutput<T> workOutput;

        Timer timer = Time.startTimer();
        try {
            workOutput = work.execute(executionRequest);
        } catch (Throwable t) {
            return ResultImpl.failed(t, Duration.ofMillis(timer.getElapsedMillis()));
        }

        Duration duration = Duration.ofMillis(timer.getElapsedMillis());
        ExecutionOutcome mode = determineOutcome(context, workOutput);

        return ResultImpl.success(duration, new ExecutionResultImpl<>(mode, workOutput));
    }

    private static ExecutionOutcome determineOutcome(InputChangesContext context, UnitOfWork.WorkOutput<?> workOutput) {
        switch (workOutput.getDidWork()) {
            case DID_NO_WORK:
                return UP_TO_DATE;
            case DID_WORK:
                return context.getInputChanges()
                    .filter(InputChanges::isIncremental)
                    .map(Functions.constant(EXECUTED_INCREMENTALLY))
                    .orElse(EXECUTED_NON_INCREMENTALLY);
            default:
                throw new AssertionError();
        }
    }

    /*
     * This operation is only used here temporarily. Should be replaced with a more stable operation in the long term.
     */
    public interface Operation extends BuildOperationType<Operation.Details, Operation.Result> {
        interface Details {
            Operation.Details INSTANCE = new Operation.Details() {
            };
        }

        interface Result {
            Operation.Result INSTANCE = new Operation.Result() {
            };
        }
    }

    private static final class ResultImpl<T> implements Result<T> {

        private final Duration duration;
        private final Try<Execution<T>> outcome;

        private ResultImpl(Duration duration, Try<Execution<T>> outcome) {
            this.duration = duration;
            this.outcome = outcome;
        }

        private static <T> Result<T> failed(Throwable t, Duration duration) {
            return new ResultImpl<>(duration, Try.failure(t));
        }

        private static <T> Result<T> success(Duration duration, Execution<T> outcome) {
            return new ResultImpl<T>(duration, Try.successful(outcome));
        }

        @Override
        public Duration getDuration() {
            return duration;
        }

        @Override
        public Try<Execution<T>> getExecution() {
            return outcome;
        }
    }

    private static final class ExecutionResultImpl<T> implements Execution<T> {
        private final ExecutionOutcome mode;
        private final UnitOfWork.WorkOutput<T> workOutput;

        public ExecutionResultImpl(ExecutionOutcome mode, UnitOfWork.WorkOutput<T> workOutput) {
            this.mode = mode;
            this.workOutput = workOutput;
        }

        @Override
        public ExecutionOutcome getOutcome() {
            return mode;
        }

        @Override
        public T getOutput() {
            return workOutput.getOutput();
        }
    }
}
