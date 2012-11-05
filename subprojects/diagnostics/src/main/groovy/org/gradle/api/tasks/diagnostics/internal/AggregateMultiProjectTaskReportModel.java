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
import org.gradle.util.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class AggregateMultiProjectTaskReportModel implements TaskReportModel {
    private List<TaskReportModel> projects = new ArrayList<TaskReportModel>();
    private SetMultimap<String, TaskDetails> groups;
    private final boolean mergeTasksWithSameName;

    public AggregateMultiProjectTaskReportModel(boolean mergeTasksWithSameName) {
        this.mergeTasksWithSameName = mergeTasksWithSameName;
    }

    public void add(TaskReportModel project) {
        projects.add(project);
    }

    public void build() {
        groups = TreeMultimap.create(new Comparator<String>() {
            public int compare(String string1, String string2) {
                return string1.compareToIgnoreCase(string2);
            }
        }, new Comparator<TaskDetails>() {
            public int compare(TaskDetails task1, TaskDetails task2) {
                return task1.getPath().compareTo(task2.getPath());
            }
        });
        for (TaskReportModel project : projects) {
            for (String group : project.getGroups()) {
                for (final TaskDetails task : project.getTasksForGroup(group)) {
                    groups.put(group, mergeTasksWithSameName ? new MergedTaskDetails(task) : task);
                }
            }
        }
    }

    public Set<String> getGroups() {
        return groups.keySet();
    }

    public Set<TaskDetails> getTasksForGroup(String group) {
        return groups.get(group);
    }

    private static class MergedTaskDetails implements TaskDetails {
        private final TaskDetails task;

        public MergedTaskDetails(TaskDetails task) {
            this.task = task;
        }

        public Path getPath() {
            return Path.path(task.getPath().getName());
        }

        public Set<TaskDetails> getChildren() {
            return task.getChildren();
        }

        public String getDescription() {
            return task.getDescription();
        }

        public Set<TaskDetails> getDependencies() {
            return task.getDependencies();
        }
    }
}
