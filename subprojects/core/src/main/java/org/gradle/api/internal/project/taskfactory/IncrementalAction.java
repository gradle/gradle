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

package org.gradle.api.internal.project.taskfactory;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Task;
import org.gradle.api.execution.incremental.IncrementalInputs;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.Cast;
import org.gradle.internal.change.CollectingChangeVisitor;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaMethod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

class IncrementalAction extends StandardTaskAction implements ContextAwareTaskAction {

    private final Instantiator instantiator;
    private TaskExecutionContext context;

    public IncrementalAction(Instantiator instantiator, Class<? extends Task> type, Method method) {
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
        final Map<Object, String> propertyNameByValue = new HashMap<Object, String>();
        for (InputFilePropertySpec inputFileProperty : context.getTaskProperties().getInputFileProperties()) {
            propertyNameByValue.put(inputFileProperty.getValue(), inputFileProperty.getPropertyName());
        }
        IncrementalInputs incrementalInputs = context.getExecutionStateChanges()
            .map(new Function<ExecutionStateChanges, IncrementalInputs>() {
                @Override
                public IncrementalInputs apply(ExecutionStateChanges changes) {
                    return changes.isRebuildRequired()
                        ? createRebuildInputs(propertyNameByValue)
                        : createIncrementalInputs(changes, propertyNameByValue);
                }
            }).orElseGet(new Supplier<IncrementalInputs>() {
                @Override
                public IncrementalInputs get() {
                    return createRebuildInputs(propertyNameByValue);
                }
            });

        context.setTaskExecutedIncrementally(incrementalInputs.isIncremental());
        JavaMethod.of(task, Object.class, methodName, IncrementalInputs.class).invoke(task, incrementalInputs);
    }

    private IncrementalInputs createIncrementalInputs(final ExecutionStateChanges changes, final Map<Object, String> propertyNameByValue) {
        return new IncrementalInputs() {
            @Override
            public boolean isIncremental() {
                return true;
            }

            @Override
            public Iterable<InputFileDetails> getChanges(Object property) {
                String propertyName = propertyNameByValue.get(property);
                if (propertyName == null) {
                    throw new UnsupportedOperationException("Cannot query incremental changes: No property found for " + property + ".");
                }
                return changes.getInputFilePropertyChanges(propertyName);
            }
        };
    }

    private IncrementalInputs createRebuildInputs(final Map<Object, String> propertyNameByValue) {
        final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> currentInputs = context.getBeforeExecutionState().get().getInputFileProperties();
        return new IncrementalInputs() {

            @Override
            public boolean isIncremental() {
                return false;
            }

            @Override
            public Iterable<InputFileDetails> getChanges(Object property) {
                String propertyName = propertyNameByValue.get(property);
                if (propertyName == null) {
                    throw new UnsupportedOperationException("Cannot query incremental changes: No property found for " + property + ".");
                }
                CurrentFileCollectionFingerprint currentFileCollectionFingerprint = currentInputs.get(propertyName);
                CollectingChangeVisitor visitor = new CollectingChangeVisitor();
                currentFileCollectionFingerprint.visitChangesSince(FileCollectionFingerprint.EMPTY, "Input", true, visitor);
                return Cast.uncheckedNonnullCast(visitor.getChanges());
            }
        };
    }
}
