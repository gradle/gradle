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
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.InputFileValueSupplier;
import org.gradle.internal.execution.UnitOfWork.InputVisitor;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.changes.DefaultIncrementalInputProperties;
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.IncrementalInputProperties;
import org.gradle.internal.properties.InputBehavior;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

import static org.gradle.internal.execution.history.changes.ExecutionStateChanges.nonIncremental;

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
            .map(changes -> new IncrementalChangesContext(context, changes.getChangeDescriptions(), changes))
            .orElseGet(() -> {
                ImmutableList<String> rebuildReason = context.getNonIncrementalReason()
                    .map(ImmutableList::of)
                    .orElse(UNTRACKED);
                return new IncrementalChangesContext(context, rebuildReason, null);
            });

        return delegate.execute(work, delegateContext);
    }

    @Nonnull
    private ExecutionStateChanges resolveExecutionStateChanges(UnitOfWork work, CachingContext context, BeforeExecutionState beforeExecution) {
        IncrementalInputProperties incrementalInputProperties = createIncrementalInputProperties(work);
        return context.getNonIncrementalReason()
            .map(ImmutableList::of)
            .map(nonIncrementalReason -> nonIncremental(nonIncrementalReason, beforeExecution, incrementalInputProperties))
            .orElseGet(() -> context.getPreviousExecutionState()
                .map(previousExecution -> context.getValidationProblems().isEmpty()
                    ? changeDetector.detectChanges(work, previousExecution, beforeExecution, incrementalInputProperties)
                    : nonIncremental(VALIDATION_FAILED, beforeExecution, incrementalInputProperties)
                )
                .orElseGet(() -> nonIncremental(NO_HISTORY, beforeExecution, incrementalInputProperties))
            );
    }

    private static IncrementalInputProperties createIncrementalInputProperties(UnitOfWork work) {
        switch (work.getExecutionBehavior()) {
            case NON_INCREMENTAL:
                return IncrementalInputProperties.NONE;
            case INCREMENTAL:
                Set<String> alreadyVisitedProperty = new HashSet<>();
                ImmutableBiMap.Builder<String, Object> builder = ImmutableBiMap.builder();
                InputVisitor visitor = new InputVisitor() {
                    @Override
                    public void visitInputFileProperty(String propertyName, InputBehavior behavior, InputFileValueSupplier valueSupplier) {
                        if (behavior.shouldTrackChanges() && alreadyVisitedProperty.add(propertyName)) {
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
                throw new AssertionError();
        }
    }
}
