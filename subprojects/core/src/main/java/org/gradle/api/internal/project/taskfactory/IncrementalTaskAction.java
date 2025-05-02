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

import org.gradle.api.Task;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.work.InputChanges;

import java.lang.reflect.Method;

public class IncrementalTaskAction extends StandardTaskAction {
    private InputChangesInternal inputChanges;

    public IncrementalTaskAction(Class<? extends Task> type, Method method) {
        super(type, method);
    }

    @Override
    public void setInputChanges(InputChangesInternal inputChanges) {
        this.inputChanges = inputChanges;
    }

    @Override
    public void clearInputChanges() {
        this.inputChanges = null;
    }

    @Override
    protected void doExecute(Task task, String methodName) {
        JavaMethod.of(task, Object.class, methodName, InputChanges.class).invoke(task, inputChanges);
    }
}
