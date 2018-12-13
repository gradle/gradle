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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.Describable;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.DescriptiveChange;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChanges;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class ResolveChangesTaskExecuter implements TaskExecuter {
    private final TaskExecuter delegate;

    public ResolveChangesTaskExecuter(TaskExecuter delegate) {
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public TaskExecuterResult execute(final TaskInternal task, TaskStateInternal state, final TaskExecutionContext context) {
        final AfterPreviousExecutionState afterPreviousExecution = context.getAfterPreviousExecution();
        final TaskArtifactState taskArtifactState = context.getTaskArtifactState();

        // Calculate initial state - note this is potentially expensive
        // We need to evaluate this even if we have no history, since every input property should be evaluated before the task executes
        final Optional<BeforeExecutionState> beforeExecutionState = context.getBeforeExecutionState();

        ExecutionStateChanges changes = taskArtifactState.getRebuildReason().map(new Function<String, ExecutionStateChanges>() {
            @Override
            public ExecutionStateChanges apply(String rebuildReason) {
                return new RebuildExecutionStateChanges(rebuildReason);
            }
        }).orElseGet(new Supplier<ExecutionStateChanges>() {
            @Nullable
            @Override
            public ExecutionStateChanges get() {
                if (afterPreviousExecution == null || context.isOutputRemovedBeforeExecution()) {
                    return null;
                } else {
                    // TODO We need a nicer describable wrapper around task here
                    return beforeExecutionState.map(new Function<BeforeExecutionState, ExecutionStateChanges>() {
                        @Override
                        public ExecutionStateChanges apply(BeforeExecutionState beforeExecution) {
                            return new DefaultExecutionStateChanges(afterPreviousExecution, beforeExecution, new Describable() {
                                @Override
                                public String getDisplayName() {
                                    // The value is cached, so we should be okay to call this many times
                                    return task.toString();
                                }
                            });
                        }
                    }).orElse(null);
                }
            }
        });

        context.setExecutionStateChanges(changes);

        return delegate.execute(task, state, context);
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
