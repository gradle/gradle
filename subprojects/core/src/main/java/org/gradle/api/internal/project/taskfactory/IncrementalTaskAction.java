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
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.lang.reflect.Method;

class IncrementalTaskAction extends StandardTaskAction implements ContextAwareTaskAction {

    private TaskExecutionContext context;

    public IncrementalTaskAction(Class<? extends Task> type, Method method) {
        super(type, method);
    }

    public void contextualise(TaskExecutionContext context) {
        this.context = context;
    }

    @Override
    public void releaseContext() {
        this.context = null;
    }

    protected void doExecute(Task task, String methodName) {
        IncrementalTaskInputs inputChanges = context.getTaskArtifactState().getInputChanges();
        context.setTaskExecutedIncrementally(inputChanges.isIncremental());
        JavaReflectionUtil.method(task, Object.class, methodName, IncrementalTaskInputs.class).invoke(task, inputChanges);
    }
}
