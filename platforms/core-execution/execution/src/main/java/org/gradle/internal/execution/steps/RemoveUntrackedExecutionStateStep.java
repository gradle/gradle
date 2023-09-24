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

import org.gradle.internal.execution.UnitOfWork;

public class RemoveUntrackedExecutionStateStep<C extends WorkspaceContext, R extends AfterExecutionResult> implements Step<C, R> {
    private final Step<? super C, ? extends R> delegate;

    public RemoveUntrackedExecutionStateStep(
        Step<? super C, ? extends R> delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        R result = delegate.execute(work, context);
        context.getHistory()
            .ifPresent(history -> {
                if (!result.getAfterExecutionState().isPresent()) {
                    history.remove(context.getIdentity().getUniqueId());
                }
            });
        return result;
    }
}
