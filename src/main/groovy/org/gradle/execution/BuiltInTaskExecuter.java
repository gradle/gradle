/*
 * Copyright 2007, 2008 the original author or authors.
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
package org.gradle.execution;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.diagnostics.TaskListTask;

import java.util.Collections;

/**
 * A {@link TaskExecuter} which executes the built-in tasks. Currently, the only built-in task is {@link TaskListTask}.
 */
public class BuiltInTaskExecuter implements TaskExecuter {
    private boolean selected;
    private TaskListTask task;

    public boolean hasNext() {
        return !selected;
    }

    public void select(Project project) {
        assert !selected;
        task = new TaskListTask(project, "taskList");
        selected = true;
    }

    public String getDescription() {
        return "taskList";
    }

    public Task getTask() {
        return task;
    }

    public void execute(DefaultTaskExecuter executer) {
        assert selected;
        executer.execute(Collections.singleton(task));
    }

    public boolean requiresProjectReload() {
        return false;
    }
}
