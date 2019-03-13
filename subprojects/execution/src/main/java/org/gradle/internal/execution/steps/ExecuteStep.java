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

import com.google.common.collect.ImmutableListMultimap;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.IncrementalChangesContext;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ExecuteStep<C extends IncrementalChangesContext> implements Step<C, Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteStep.class);

    @Override
    public Result execute(C context) {
        UnitOfWork work = context.getWork();
        InputChangesInternal inputChanges = work.isIncremental()
            ? determineInputChanges(work, context)
            : null;

        ExecutionOutcome outcome = work.execute(inputChanges);
        return new Result() {
            @Override
            public Try<ExecutionOutcome> getOutcome() {
                return Try.successful(outcome);
            }
        };
    }

    private InputChangesInternal determineInputChanges(UnitOfWork work, IncrementalChangesContext context) {
        Optional<ExecutionStateChanges> changes = context.getChanges();
        if (!changes.isPresent()) {
            throw new UnsupportedOperationException("Cannot use input changes when input tracking is disabled.");
        }

        ImmutableListMultimap<Object, String> incrementalInputs = determineIncrementalInputs(work);
        InputChangesInternal inputChanges = changes.get().getInputChanges(incrementalInputs);
        if (!inputChanges.isIncremental()) {
            LOGGER.info("All input files are considered out-of-date for incremental {}.", work.getDisplayName());
        }
        return inputChanges;
    }

    private ImmutableListMultimap<Object, String> determineIncrementalInputs(UnitOfWork work) {
        ImmutableListMultimap.Builder<Object, String> builder = ImmutableListMultimap.builder();
        work.visitIncrementalFileInputs((name, value) -> builder.put(value, name));
        return builder.build();
    }
}
