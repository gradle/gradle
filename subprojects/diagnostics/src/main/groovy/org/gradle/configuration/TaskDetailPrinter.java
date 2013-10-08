/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.execution.TaskSelector;
import org.gradle.logging.StyledTextOutput;
import org.gradle.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.gradle.logging.StyledTextOutput.Style.UserInput;

public class TaskDetailPrinter {
    private final String taskPath;
    private final TaskSelector.TaskSelection selection;
    private static final String INDENT = "     ";

    public TaskDetailPrinter(String taskPath, TaskSelector.TaskSelection selection) {
        this.taskPath = taskPath;
        this.selection = selection;
    }

    public void print(StyledTextOutput output) {
        final List<Task> tasks = CollectionUtils.sort(selection.getTasks(), new Comparator<Task>() {
            public int compare(Task o1, Task o2) {
                return o1.getPath().compareTo(o2.getPath());
            }
        });

        output.text("Detailed task description for ").withStyle(UserInput).println(taskPath);
        final Set<Class> taskTypes = new TreeSet<Class>(new Comparator<Class>() {
            public int compare(Class o1, Class o2) {
                return o1.getSimpleName().compareTo(o2.getSimpleName());
            }
        });
        taskTypes.addAll(CollectionUtils.collect(tasks, new Transformer<Class, Task>() {
            public Class transform(Task original) {
                return original.getClass().getSuperclass();
            }
        }));
        for (final Class taskType : taskTypes) {
            final Set<Task> tasksByType = new TreeSet<Task>(CollectionUtils.filter(tasks, new Spec<Task>() {
                public boolean isSatisfiedBy(Task element) {
                    return element.getClass().getSuperclass().equals(taskType);
                }
            }));
            output.println();
            output.text(tasksByType.size() > 1 ? "Paths" : "Path").println();
            for (Task task : tasksByType) {
                output.text(INDENT).withStyle(UserInput).println(task.getPath());
            }
            output.println();
            output.text("Type").println();
            output.text(INDENT).withStyle(UserInput).text(taskType.getSimpleName());
            output.println(" (" + taskType + ")");
            output.println();
            printTaskDescription(output, tasksByType);
            output.println();
            if (taskTypes.size() > 1) {
                output.println("----------------------");
            }
        }

    }

    private void printTaskDescription(StyledTextOutput output, Set<Task> tasksByType) {
        final Set<String> descriptions = CollectionUtils.collect(tasksByType, new Transformer<String, Task>() {
            public String transform(Task original) {
                return original.getDescription();
            }
        });
        output.text(descriptions.size() > 1 ? "Descriptions" : "Description").println();
        if (descriptions.size() == 1) {
            // all tasks have the same description
            output.text(INDENT).println(descriptions.iterator().next());
        } else {
            for (final String description : descriptions) {
                output.text(INDENT).println(description);
            }
        }
    }
}
