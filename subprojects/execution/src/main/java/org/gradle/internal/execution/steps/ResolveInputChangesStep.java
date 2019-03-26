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
import org.gradle.internal.execution.history.changes.InputChangesInternal;
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
        final UnitOfWork work = context.getWork();
        Optional<InputChangesInternal> inputChanges = work.isRequiresInputChanges()
            ? Optional.of(determineInputChanges(work, context))
            : Optional.empty();
        return delegate.execute(new InputChangesContext() {
            @Override
            public Optional<InputChangesInternal> getInputChanges() {
                return inputChanges;
            }

            @Override
            public boolean isIncrementalExecution() {
                return inputChanges.map(changes -> changes.isIncremental()).orElse(false);
            }

            @Override
            public UnitOfWork getWork() {
                return work;
            }
        });
    }

    private InputChangesInternal determineInputChanges(UnitOfWork work, IncrementalChangesContext context) {
        return context.getChanges()
            .map(changes -> {
                InputChangesInternal inputChanges = changes.createInputChanges();
                if (!inputChanges.isIncremental()) {
                    LOGGER.info("All input files are considered out-of-date for incremental {}.", work.getDisplayName());
                }
                return inputChanges;
            })
            .orElseThrow(() -> new UnsupportedOperationException("Cannot use input changes when input tracking is disabled."));
    }
}
