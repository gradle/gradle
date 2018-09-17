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

import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;

/**
 * A {@link TaskExecuter} that notifies listeners interested in the actual actions being executed.
 */
public class ActionEventFiringTaskExecuter implements TaskExecuter {
    private final TaskExecuter delegate;
    private final TaskOutputChangesListener outputsGenerationListener;
    private final TaskActionListener listener;

    public ActionEventFiringTaskExecuter(TaskExecuter delegate, TaskOutputChangesListener outputsGenerationListener, TaskActionListener taskActionListener) {
        this.delegate = delegate;
        this.outputsGenerationListener = outputsGenerationListener;
        this.listener = taskActionListener;
    }

    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        listener.beforeActions(task);
        if (task.hasTaskActions()) {
            outputsGenerationListener.beforeTaskOutputChanged();
        }
        try {
            delegate.execute(task, state, context);
        } finally {
            listener.afterActions(task);
        }
    }
}
