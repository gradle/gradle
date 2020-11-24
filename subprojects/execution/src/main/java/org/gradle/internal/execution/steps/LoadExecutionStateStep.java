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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;

import java.io.File;
import java.util.Optional;

public class LoadExecutionStateStep<C extends WorkspaceContext, R extends Result> implements Step<C, R> {
    private final Step<? super AfterPreviousExecutionContext, ? extends R> delegate;

    public LoadExecutionStateStep(Step<? super AfterPreviousExecutionContext, ? extends R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        Identity identity = context.getIdentity();
        Optional<AfterPreviousExecutionState> afterPreviousExecutionState = context.getHistory()
            .flatMap(history -> history.load(identity.getUniqueId()));
        return delegate.execute(work, new AfterPreviousExecutionContext() {
            @Override
            public Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
                return afterPreviousExecutionState;
            }

            @Override
            public Optional<String> getRebuildReason() {
                return context.getRebuildReason();
            }

            @Override
            public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
                return context.getInputProperties();
            }

            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
                return context.getInputFileProperties();
            }

            @Override
            public Identity getIdentity() {
                return context.getIdentity();
            }

            @Override
            public File getWorkspace() {
                return context.getWorkspace();
            }

            @Override
            public Optional<ExecutionHistoryStore> getHistory() {
                return context.getHistory();
            }
        });
    }
}
