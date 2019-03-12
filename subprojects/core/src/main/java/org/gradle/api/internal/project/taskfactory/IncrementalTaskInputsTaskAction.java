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

import org.gradle.api.Task;
import org.gradle.api.internal.changedetection.changes.ChangesOnlyIncrementalTaskInputs;
import org.gradle.api.internal.changedetection.changes.RebuildIncrementalTaskInputs;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

class IncrementalTaskInputsTaskAction extends AbstractIncrementalTaskAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalTaskInputsTaskAction.class);

    private final Instantiator instantiator;

    public IncrementalTaskInputsTaskAction(Instantiator instantiator, Class<? extends Task> type, Method method) {
        super(type, method);
        this.instantiator = instantiator;
    }

    protected void doExecute(final Task task, String methodName) {
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        ExecutionStateChanges changes = getContext().getExecutionStateChanges().get();
        InputChangesInternal inputChanges = changes.getInputChanges();

        IncrementalTaskInputs incrementalTaskInputs = inputChanges.isIncremental()
            ? createIncrementalInputs(inputChanges)
            : createRebuildInputs(task, inputChanges);

        getContext().setTaskExecutedIncrementally(incrementalTaskInputs.isIncremental());
        JavaMethod.of(task, Object.class, methodName, IncrementalTaskInputs.class).invoke(task, incrementalTaskInputs);
    }

    private ChangesOnlyIncrementalTaskInputs createIncrementalInputs(InputChangesInternal inputChanges) {
        return instantiator.newInstance(ChangesOnlyIncrementalTaskInputs.class, inputChanges.getAllFileChanges());
    }

    private RebuildIncrementalTaskInputs createRebuildInputs(Task task, InputChangesInternal inputChanges) {
        LOGGER.info("All input files are considered out-of-date for incremental {}.", task);
        return instantiator.newInstance(RebuildIncrementalTaskInputs.class, inputChanges);
    }
}
