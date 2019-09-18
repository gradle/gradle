/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.execution.IncrementalChangesContext;
import org.gradle.internal.execution.InputChangesContext;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.work.InputChanges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ResolveInputChangesStep<C extends IncrementalChangesContext> implements Step<C, Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveInputChangesStep.class);

    private final Step<? super InputChangesContext, ? extends Result> delegate;

    public ResolveInputChangesStep(Step<? super InputChangesContext, ? extends Result> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Result execute(C context) {
        UnitOfWork work = context.getWork();
        Optional<InputChangesInternal> inputChanges = work.getInputChangeTrackingStrategy().requiresInputChanges()
            ? Optional.of(determineInputChanges(work, context))
            : Optional.empty();
        return delegate.execute(new InputChangesContext() {
            @Override
            public Optional<InputChangesInternal> getInputChanges() {
                return inputChanges;
            }

            @Override
            public boolean isIncrementalExecution() {
                return inputChanges.map(InputChanges::isIncremental).orElse(false);
            }

            @Override
            public Optional<String> getRebuildReason() {
                return context.getRebuildReason();
            }

            @Override
            public Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
                return context.getAfterPreviousExecutionState();
            }

            @Override
            public Optional<BeforeExecutionState> getBeforeExecutionState() {
                return context.getBeforeExecutionState();
            }

            @Override
            public UnitOfWork getWork() {
                return work;
            }
        });
    }

    private InputChangesInternal determineInputChanges(UnitOfWork work, IncrementalChangesContext context) {
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        ExecutionStateChanges changes = context.getChanges().get();
        InputChangesInternal inputChanges = changes.createInputChanges();
        if (!inputChanges.isIncremental()) {
            LOGGER.info("The input changes require a full rebuild for incremental {}.", work.getDisplayName());
        }
        return inputChanges;
    }
}
