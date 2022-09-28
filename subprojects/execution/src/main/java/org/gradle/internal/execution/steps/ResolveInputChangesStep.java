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

import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static org.gradle.internal.execution.UnitOfWork.ExecutionBehavior.NON_INCREMENTAL;

public class ResolveInputChangesStep<C extends IncrementalChangesContext, R extends Result> implements Step<C, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveInputChangesStep.class);

    private final Step<? super InputChangesContext, ? extends R> delegate;

    public ResolveInputChangesStep(Step<? super InputChangesContext, ? extends R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        return delegate.execute(work, new InputChangesContext(context, determineInputChanges(work, context)));
    }

    @Nullable
    private static InputChangesInternal determineInputChanges(UnitOfWork work, IncrementalChangesContext context) {
        if (work.getExecutionBehavior() == NON_INCREMENTAL) {
            return null;
        }
        ExecutionStateChanges changes = context.getChanges()
            .orElseThrow(() -> new IllegalStateException("Changes are not tracked, unable determine incremental changes."));
        InputChangesInternal inputChanges = changes.createInputChanges();
        if (!inputChanges.isIncremental()) {
            LOGGER.info("The input changes require a full rebuild for incremental {}.", work.getDisplayName());
        }
        return inputChanges;
    }
}
