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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Task;
import org.gradle.api.internal.changedetection.changes.ChangesOnlyIncrementalTaskInputs;
import org.gradle.api.internal.changedetection.changes.RebuildIncrementalTaskInputs;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.CollectingChangeVisitor;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.InputFileChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaMethod;

import java.lang.reflect.Method;

class IncrementalTaskInputsTaskAction extends AbstractIncrementalTaskAction {

    private final Instantiator instantiator;

    public IncrementalTaskInputsTaskAction(Instantiator instantiator, Class<? extends Task> type, Method method) {
        super(type, method);
        this.instantiator = instantiator;
    }

    protected void doExecute(final Task task, String methodName) {
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        ExecutionStateChanges changes = getContext().getExecutionStateChanges().get();
        IncrementalTaskInputs incrementalTaskInputs = changes.visitInputFileChanges(new ExecutionStateChanges.IncrementalInputsVisitor<IncrementalTaskInputs>() {
            @Override
            public IncrementalTaskInputs visitRebuild(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> allFileInputs) {
                return createRebuildInputs(task, allFileInputs);
            }

            @Override
            public IncrementalTaskInputs visitIncrementalChange(InputFileChanges inputFileChanges) {
                return createIncrementalInputs(inputFileChanges);
            }
        });

        getContext().setTaskExecutedIncrementally(incrementalTaskInputs.isIncremental());
        JavaMethod.of(task, Object.class, methodName, IncrementalTaskInputs.class).invoke(task, incrementalTaskInputs);
    }

    private ChangesOnlyIncrementalTaskInputs createIncrementalInputs(InputFileChanges inputFilesChanges) {
        return instantiator.newInstance(ChangesOnlyIncrementalTaskInputs.class, collectInputFileChanges(inputFilesChanges));
    }

    private Iterable<Change> collectInputFileChanges(InputFileChanges inputFileChanges) {
        CollectingChangeVisitor visitor = new CollectingChangeVisitor();
        inputFileChanges.accept(visitor);
        return visitor.getChanges();
    }

    private RebuildIncrementalTaskInputs createRebuildInputs(Task task, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> currentInputs) {
        return instantiator.newInstance(RebuildIncrementalTaskInputs.class, task, currentInputs.values());
    }
}
