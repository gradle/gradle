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

import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.DescriptiveChange;
import org.gradle.internal.execution.IncrementalChangesContext;
import org.gradle.internal.execution.IncrementalContext;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChanges;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;

import java.util.Optional;

public class ResolveChangesStep<R extends Result> implements Step<IncrementalContext, R> {
    private final Step<? super IncrementalChangesContext, R> delegate;

    public ResolveChangesStep(Step<? super IncrementalChangesContext, R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(IncrementalContext context) {
        final UnitOfWork work = context.getWork();
        ExecutionStateChanges changes = context.getRebuildReason()
            .<ExecutionStateChanges>map(rebuildReason ->
                new RebuildExecutionStateChanges(rebuildReason)
            )
            .orElseGet(() ->
                context.getAfterPreviousExecutionState()
                    .flatMap(afterPreviousExecution -> context.getBeforeExecutionState()
                        .map(beforeExecution -> new DefaultExecutionStateChanges(
                            afterPreviousExecution,
                            beforeExecution,
                            work,
                            work.includeAddedOutputs())
                        )
                    )
                    .orElse(null)
            );

        return delegate.execute(new IncrementalChangesContext() {
            @Override
            public Optional<ExecutionStateChanges> getChanges() {
                return Optional.ofNullable(changes);
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

    private static class RebuildExecutionStateChanges implements ExecutionStateChanges {
        private final Change rebuildChange;

        public RebuildExecutionStateChanges(String rebuildReason) {
            this.rebuildChange = new DescriptiveChange(rebuildReason);
        }

        @Override
        public Iterable<Change> getInputFilesChanges() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitAllChanges(ChangeVisitor visitor) {
            visitor.visitChange(rebuildChange);
        }

        @Override
        public boolean isRebuildRequired() {
            return true;
        }

        @Override
        public AfterPreviousExecutionState getPreviousExecution() {
            throw new UnsupportedOperationException();
        }
    }
}
