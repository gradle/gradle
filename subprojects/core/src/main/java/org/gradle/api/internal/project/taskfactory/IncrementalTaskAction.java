/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import com.google.common.collect.ImmutableCollection;
import org.gradle.api.Task;
import org.gradle.api.internal.changedetection.changes.ChangesOnlyIncrementalTaskInputs;
import org.gradle.api.internal.changedetection.changes.RebuildIncrementalTaskInputs;
import org.gradle.api.internal.changedetection.changes.StatefulIncrementalTaskInputs;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.change.Change;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaMethod;

import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.function.Supplier;

class IncrementalTaskAction extends StandardTaskAction implements ContextAwareTaskAction {

    private final Instantiator instantiator;
    private TaskExecutionContext context;

    public IncrementalTaskAction(Instantiator instantiator, Class<? extends Task> type, Method method) {
        super(type, method);
        this.instantiator = instantiator;
    }

    public void contextualise(TaskExecutionContext context) {
        this.context = context;
    }

    @Override
    public void releaseContext() {
        this.context = null;
    }

    protected void doExecute(final Task task, String methodName) {
        IncrementalTaskInputs incrementalInputs = context.getExecutionStateChanges()
            .map(new Function<ExecutionStateChanges, StatefulIncrementalTaskInputs>() {
                @Override
                public StatefulIncrementalTaskInputs apply(ExecutionStateChanges changes) {
                    return changes.getInputFilesChanges().map(new Function<Iterable<Change>, StatefulIncrementalTaskInputs>() {
                        @Override
                        public StatefulIncrementalTaskInputs apply(Iterable<Change> changes) {
                            return createIncrementalInputs(changes);
                        }
                    }).orElseGet(new Supplier<StatefulIncrementalTaskInputs>() {
                        @Override
                        public StatefulIncrementalTaskInputs get() {
                            return createRebuildInputs(task);
                        }
                    });
                }
            }).orElseGet(new Supplier<StatefulIncrementalTaskInputs>() {
                @Override
                public StatefulIncrementalTaskInputs get() {
                    return createRebuildInputs(task);
                }
            });

        context.setTaskExecutedIncrementally(incrementalInputs.isIncremental());
        JavaMethod.of(task, Object.class, methodName, IncrementalTaskInputs.class).invoke(task, incrementalInputs);
    }

    private ChangesOnlyIncrementalTaskInputs createIncrementalInputs(Iterable<Change> inputFilesChanges) {
        return instantiator.newInstance(ChangesOnlyIncrementalTaskInputs.class, inputFilesChanges);
    }

    private RebuildIncrementalTaskInputs createRebuildInputs(Task task) {
        ImmutableCollection<CurrentFileCollectionFingerprint> currentInputs = context.getBeforeExecutionState().get().getInputFileProperties().values();
        return instantiator.newInstance(RebuildIncrementalTaskInputs.class, task, currentInputs);
    }
}
