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
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.execution.history.PreviousExecutionState;

public class LoadPreviousExecutionStateStep<C extends WorkspaceContext, R extends AfterExecutionResult> implements Step<C, R> {
    private final Step<? super PreviousExecutionContext, ? extends R> delegate;

    public LoadPreviousExecutionStateStep(Step<? super PreviousExecutionContext, ? extends R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        Identity identity = context.getIdentity();
        PreviousExecutionState previousExecutionState = context.getHistory()
            .flatMap(history -> history.load(identity.getUniqueId()))
            .orElse(null);
        R result = delegate.execute(work, new PreviousExecutionContext(context, previousExecutionState));

        // If we did not capture any outputs after execution, remove them from history
        context.getHistory()
            .ifPresent(history -> {
                if (!result.getAfterExecutionState().isPresent()) {
                    history.remove(context.getIdentity().getUniqueId());
                }
            });
        return result;
    }
}
