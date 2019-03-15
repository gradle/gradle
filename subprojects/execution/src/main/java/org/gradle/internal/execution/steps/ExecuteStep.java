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

import com.google.common.collect.ImmutableMultimap;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.IncrementalChangesContext;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecuteStep<C extends IncrementalChangesContext> implements Step<C, Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteStep.class);

    @Override
    public Result execute(C context) {
        UnitOfWork work = context.getWork();
        InputChangesInternal inputChanges = work.isRequiresInputChanges()
            ? determineInputChanges(work, context)
            : null;

        boolean incremental = inputChanges != null && inputChanges.isIncremental();
        UnitOfWork.WorkResult result = work.execute(inputChanges);
        ExecutionOutcome outcome = determineOutcome(result, incremental);
        return new Result() {
            @Override
            public Try<ExecutionOutcome> getOutcome() {
                return Try.successful(outcome);
            }
        };
    }

    private ExecutionOutcome determineOutcome(UnitOfWork.WorkResult result, boolean incremental) {
        switch (result) {
            case DID_NO_WORK:
                return ExecutionOutcome.UP_TO_DATE;
            case DID_WORK:
                return incremental ? ExecutionOutcome.EXECUTED_INCREMENTALLY : ExecutionOutcome.EXECUTED_NON_INCREMENTALLY;
            default:
                throw new IllegalArgumentException("Unknown result: " + result);
        }
    }

    private InputChangesInternal determineInputChanges(UnitOfWork work, IncrementalChangesContext context) {
        return context.getChanges()
            .map(changes -> {
                ImmutableMultimap<Object, String> incrementalParameterNamesByValue = determineIncrementalParameterNamesByValue(work);
                InputChangesInternal inputChanges = changes.createInputChanges(incrementalParameterNamesByValue);
                if (!inputChanges.isIncremental()) {
                    LOGGER.info("All input files are considered out-of-date for incremental {}.", work.getDisplayName());
                }
                return inputChanges;
            })
        .orElseThrow(() -> new UnsupportedOperationException("Cannot use input changes when input tracking is disabled."));
    }

    private ImmutableMultimap<Object, String> determineIncrementalParameterNamesByValue(UnitOfWork work) {
        ImmutableMultimap.Builder<Object, String> builder = ImmutableMultimap.builder();
        work.visitInputFileProperties((name, value, incremental) -> {
            if (incremental) {
                builder.put(value, name);
            }
        });
        return builder.build();
    }
}
