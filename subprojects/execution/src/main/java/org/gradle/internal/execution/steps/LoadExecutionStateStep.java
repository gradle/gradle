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

import org.gradle.internal.execution.AfterPreviousExecutionContext;
import org.gradle.internal.execution.ExecutionRequestContext;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;

import java.util.Optional;

public class LoadExecutionStateStep<C extends ExecutionRequestContext, R extends Result> implements Step<C, R> {
    private final Step<? super AfterPreviousExecutionContext, ? extends R> delegate;

    public LoadExecutionStateStep(Step<? super AfterPreviousExecutionContext, ? extends R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(C context) {
        UnitOfWork work = context.getWork();
        Optional<AfterPreviousExecutionState> afterPreviousExecutionState = work.getExecutionHistoryStore()
            .flatMap(executionHistoryStore -> executionHistoryStore.load(work.getIdentity()));
        return delegate.execute(new AfterPreviousExecutionContext() {
            @Override
            public Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
                return afterPreviousExecutionState;
            }

            @Override
            public Optional<String> getRebuildReason() {
                return context.getRebuildReason();
            }

            @Override
            public UnitOfWork getWork() {
                return work;
            }
        });
    }
}
