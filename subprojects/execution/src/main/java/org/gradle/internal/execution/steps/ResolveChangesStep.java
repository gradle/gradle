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

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.fingerprint.InputFingerprinter.FileValueSupplier;
import org.gradle.internal.execution.fingerprint.InputFingerprinter.InputPropertyType;
import org.gradle.internal.execution.fingerprint.InputFingerprinter.InputVisitor;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.changes.DefaultIncrementalInputProperties;
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.IncrementalInputProperties;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Optional;

public class ResolveChangesStep<C extends CachingContext, R extends Result> implements Step<C, R> {
    private static final ImmutableList<String> NO_HISTORY = ImmutableList.of("No history is available.");
    private static final ImmutableList<String> UNTRACKED = ImmutableList.of("Change tracking is disabled.");
    private static final ImmutableList<String> VALIDATION_FAILED = ImmutableList.of("Incremental execution has been disabled to ensure correctness. Please consult deprecation warnings for more details.");

    private final ExecutionStateChangeDetector changeDetector;

    private final Step<? super IncrementalChangesContext, R> delegate;

    public ResolveChangesStep(
        ExecutionStateChangeDetector changeDetector,
        Step<? super IncrementalChangesContext, R> delegate
    ) {
        this.changeDetector = changeDetector;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        IncrementalChangesContext delegateContext = context.getBeforeExecutionState()
            .map(beforeExecution -> resolveExecutionStateChanges(work, context, beforeExecution))
            .map(changes -> createDelegateContext(context, changes.getChangeDescriptions(), changes))
            .orElseGet(() -> {
                ImmutableList<String> rebuildReason = context.getNonIncrementalReason()
                    .map(ImmutableList::of)
                    .orElse(UNTRACKED);
                return createDelegateContext(context, rebuildReason, null);
            });

        return delegate.execute(work, delegateContext);
    }

    @Nonnull
    private ExecutionStateChanges resolveExecutionStateChanges(UnitOfWork work, CachingContext context, BeforeExecutionState beforeExecution) {
        IncrementalInputProperties incrementalInputProperties = createIncrementalInputProperties(work);
        return context.getNonIncrementalReason()
            .map(ImmutableList::of)
            .map(nonIncrementalReason -> ExecutionStateChanges.nonIncremental(
                nonIncrementalReason,
                beforeExecution,
                incrementalInputProperties))
            .orElseGet(() -> context.getPreviousExecutionState()
                .map(previousExecution -> context.getValidationProblems()
                    .map(__ -> ExecutionStateChanges.nonIncremental(
                        VALIDATION_FAILED,
                        beforeExecution,
                        incrementalInputProperties
                    ))
                    .orElseGet(() -> changeDetector.detectChanges(
                        work,
                        previousExecution,
                        beforeExecution,
                        incrementalInputProperties)))
                .orElseGet(() -> ExecutionStateChanges.nonIncremental(
                    NO_HISTORY,
                    beforeExecution,
                    incrementalInputProperties
                )));
    }

    private static IncrementalInputProperties createIncrementalInputProperties(UnitOfWork work) {
        UnitOfWork.InputChangeTrackingStrategy inputChangeTrackingStrategy = work.getInputChangeTrackingStrategy();
        switch (inputChangeTrackingStrategy) {
            case NONE:
                return IncrementalInputProperties.NONE;
            //noinspection deprecation
            case ALL_PARAMETERS:
                // When using IncrementalTaskInputs, keep the old behaviour of all file inputs being incremental
                return IncrementalInputProperties.ALL;
            case INCREMENTAL_PARAMETERS:
                ImmutableBiMap.Builder<String, Object> builder = ImmutableBiMap.builder();
                InputVisitor visitor = new InputVisitor() {
                    @Override
                    public void visitInputFileProperty(String propertyName, InputPropertyType type, FileValueSupplier valueSupplier) {
                        if (type.isIncremental()) {
                            Object value = valueSupplier.getValue();
                            if (value == null) {
                                throw new InvalidUserDataException("Must specify a value for incremental input property '" + propertyName + "'.");
                            }
                            builder.put(propertyName, value);
                        }
                    }
                };
                work.visitIdentityInputs(visitor);
                work.visitRegularInputs(visitor);
                return new DefaultIncrementalInputProperties(builder.build());
            default:
                throw new AssertionError("Unknown InputChangeTrackingStrategy: " + inputChangeTrackingStrategy);
        }
    }

    private static IncrementalChangesContext createDelegateContext(CachingContext context, ImmutableList<String> rebuildReasons, @Nullable ExecutionStateChanges changes) {
        return new IncrementalChangesContext() {
            @Override
            public ImmutableList<String> getRebuildReasons() {
                return rebuildReasons;
            }

            @Override
            public Optional<ExecutionStateChanges> getChanges() {
                return Optional.ofNullable(changes);
            }

            @Override
            public CachingState getCachingState() {
                return context.getCachingState();
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
        };
    }
}
