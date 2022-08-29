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
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction;
import org.gradle.internal.execution.history.changes.InputChangesInternal;

import java.lang.reflect.Method;

public abstract class AbstractIncrementalTaskAction extends StandardTaskAction implements InputChangesAwareTaskAction {
    private InputChangesInternal inputChanges;

    public AbstractIncrementalTaskAction(Class<? extends Task> type, Method method) {
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

    protected InputChangesInternal getInputChanges() {
        return inputChanges;
    }
}
