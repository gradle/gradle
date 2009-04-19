/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Rule;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

/**
 * <p>The {@code TaskReportTask} prints out the list of tasks in the project, and its subprojects. It is used when you
 *  use the task list command-line option.</p>
 */
public class TaskReportTask extends AbstractReportTask {
    private TaskReportRenderer renderer = new TaskReportRenderer();

    public TaskReportTask(Project project, String name) {
        super(project, name);
    }

    public TaskReportRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(TaskReportRenderer renderer) {
        this.renderer = renderer;
    }

    public void generate(Project project) throws IOException {
        renderer.addDefaultTasks(project.getDefaultTasks());
        Set<Task> tasks = new TreeSet<Task>(project.getTasks().getAll());
        for (Task task : tasks) {
            renderer.addTask(task);
        }
        for (Rule rule : project.getTasks().getRules()) {
            renderer.addRule(rule);
        }
    }
}
