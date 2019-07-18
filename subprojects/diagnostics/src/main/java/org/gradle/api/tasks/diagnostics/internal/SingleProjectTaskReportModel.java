/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks.diagnostics.internal;

import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;
import org.gradle.api.Task;
import org.gradle.util.GUtil;
import org.gradle.util.Path;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

public class SingleProjectTaskReportModel implements TaskReportModel {
    private final SetMultimap<String, TaskDetails> groups = TreeMultimap.create(new Comparator<String>() {
        @Override
        public int compare(String string1, String string2) {
            return string1.compareToIgnoreCase(string2);
        }
    }, new Comparator<TaskDetails>() {
        @Override
        public int compare(TaskDetails task1, TaskDetails task2) {
            return task1.getPath().compareTo(task2.getPath());
        }
    });
    private final TaskDetailsFactory factory;

    public SingleProjectTaskReportModel(TaskDetailsFactory factory) {
        this.factory = factory;
    }

    public void build(final Collection<? extends Task> tasks) {
        for (Task task : tasks) {
            String group = GUtil.isTrue(task.getGroup()) ? task.getGroup() : DEFAULT_GROUP;
            groups.put(group, new TaskDetailsImpl(task, factory.create(task)));
        }
    }

    @Override
    public Set<String> getGroups() {
        return groups.keySet();
    }

    @Override
    public Set<TaskDetails> getTasksForGroup(String group) {
        if (!groups.containsKey(group)) {
            throw new IllegalArgumentException(String.format("Unknown group '%s'", group));
        }
        return groups.get(group);
    }

    private static class TaskDetailsImpl implements TaskDetails {
        private final Task task;
        private final TaskDetails details;

        public TaskDetailsImpl(Task task, TaskDetails details) {
            this.task = task;
            this.details = details;
        }

        @Override
        public Path getPath() {
            return details.getPath();
        }

        @Override
        public String getDescription() {
            return details.getDescription();
        }

        @Override
        public String toString() {
            return task.toString();
        }

        public Task getTask() {
            return task;
        }
    }
}
