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
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
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

public class ExecuteStep<C extends ChangingOutputsContext> implements Step<C, Result> {

    private final BuildOperationRunner buildOperationRunner;

    public ExecuteStep(BuildOperationRunner buildOperationRunner) {
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public Result execute(UnitOfWork work, C context) {
        Class<? extends UnitOfWork> workType = work.getClass();
        UnitOfWork.Identity identity = context.getIdentity();
        return buildOperationRunner.call(new CallableBuildOperation<Result>() {
            @Override
            public Result call(BuildOperationContext operationContext) {
                Result result = executeInternal(work, context);
                operationContext.setResult(Operation.Result.INSTANCE);
                return result;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor
                    .displayName("Executing " + work.getDisplayName())
                    .details(new Operation.Details() {
                        @Override
                        public Class<?> getWorkType() {
                            return workType;
                        }

                        @Override
                        public UnitOfWork.Identity getIdentity() {
                            return identity;
                        }
                    });
            }
        });
    }

    private static Result executeInternal(UnitOfWork work, InputChangesContext context) {
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
        UnitOfWork.WorkOutput workOutput;

        Timer timer = Time.startTimer();
        try {
            workOutput = work.execute(executionRequest);
        } catch (Throwable t) {
            return Result.failed(t, Duration.ofMillis(timer.getElapsedMillis()));
        }

        Duration duration = Duration.ofMillis(timer.getElapsedMillis());
        ExecutionOutcome mode = determineOutcome(context, workOutput);

        return Result.success(duration, new ExecutionResultImpl(mode, workOutput));
    }

    private static ExecutionOutcome determineOutcome(InputChangesContext context, UnitOfWork.WorkOutput workOutput) {
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
            Class<?> getWorkType();
            UnitOfWork.Identity getIdentity();
        }

        interface Result {
            Operation.Result INSTANCE = new Operation.Result() {
            };
        }
    }

    private static final class ExecutionResultImpl implements Execution {
        private final ExecutionOutcome mode;
        private final UnitOfWork.WorkOutput workOutput;

        public ExecutionResultImpl(ExecutionOutcome mode, UnitOfWork.WorkOutput workOutput) {
            this.mode = mode;
            this.workOutput = workOutput;
        }

        @Override
        public ExecutionOutcome getOutcome() {
            return mode;
        }

        @Override
        public Object getOutput(File workspace) {
            return workOutput.getOutput(workspace);
        }

        @Override
        public boolean canStoreOutputsInCache() {
            return workOutput.canStoreInCache();
        }
    }
}
