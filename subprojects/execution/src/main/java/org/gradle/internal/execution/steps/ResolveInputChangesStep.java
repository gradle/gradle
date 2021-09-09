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
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.work.InputChanges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;

public class ResolveInputChangesStep<C extends IncrementalChangesContext, R extends Result> implements Step<C, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveInputChangesStep.class);

    private final Step<? super InputChangesContext, ? extends R> delegate;

    public ResolveInputChangesStep(Step<? super InputChangesContext, ? extends R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        Optional<InputChangesInternal> inputChanges = determineInputChanges(work, context);
        return delegate.execute(work, new InputChangesContext() {
            @Override
            public Optional<InputChangesInternal> getInputChanges() {
                return inputChanges;
            }

            @Override
            public boolean isIncrementalExecution() {
                return inputChanges.map(InputChanges::isIncremental).orElse(false);
            }

            @Override
            public Optional<String> getNonIncrementalReason() {
                return context.getNonIncrementalReason();
            }

            @Override
            public WorkValidationContext getValidationContext() {
                return context.getValidationContext();
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
            public UnitOfWork.Identity getIdentity() {
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

            @Override
            public Optional<PreviousExecutionState> getPreviousExecutionState() {
                return context.getPreviousExecutionState();
            }

            @Override
            public Optional<ValidationResult> getValidationProblems() {
                return context.getValidationProblems();
            }

            @Override
            public Optional<BeforeExecutionState> getBeforeExecutionState() {
                return context.getBeforeExecutionState();
            }
        });
    }

    private static Optional<InputChangesInternal> determineInputChanges(UnitOfWork work, IncrementalChangesContext context) {
        if (!work.getInputChangeTrackingStrategy().requiresInputChanges()) {
            return Optional.empty();
        }
        ExecutionStateChanges changes = context.getChanges()
            .orElseThrow(() -> new IllegalStateException("Changes are not tracked, unable determine incremental changes."));
        InputChangesInternal inputChanges = changes.createInputChanges();
        if (!inputChanges.isIncremental()) {
            LOGGER.info("The input changes require a full rebuild for incremental {}.", work.getDisplayName());
        }
        return Optional.of(inputChanges);
    }
}
