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
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaMethod;

import java.lang.reflect.Method;

public class IncrementalTaskInputsTaskAction extends AbstractIncrementalTaskAction {
    private final Instantiator instantiator;

    public IncrementalTaskInputsTaskAction(Instantiator instantiator, Class<? extends Task> type, Method method) {
        super(type, method);
        this.instantiator = instantiator;
    }

    @Override
    protected void doExecute(Task task, String methodName) {
        InputChangesInternal inputChanges = getInputChanges();

        Iterable<InputFileDetails> allFileChanges = inputChanges.getAllFileChanges();
        IncrementalTaskInputs incrementalTaskInputs = inputChanges.isIncremental()
            ? createIncrementalInputs(allFileChanges)
            : createRebuildInputs(allFileChanges);

        JavaMethod.of(task, Object.class, methodName, IncrementalTaskInputs.class).invoke(task, incrementalTaskInputs);
    }

    private ChangesOnlyIncrementalTaskInputs createIncrementalInputs(Iterable<InputFileDetails> allFileChanges) {
        return instantiator.newInstance(ChangesOnlyIncrementalTaskInputs.class, allFileChanges);
    }

    private RebuildIncrementalTaskInputs createRebuildInputs(Iterable<InputFileDetails> allFileChanges) {
        return instantiator.newInstance(RebuildIncrementalTaskInputs.class, allFileChanges);
    }
}
