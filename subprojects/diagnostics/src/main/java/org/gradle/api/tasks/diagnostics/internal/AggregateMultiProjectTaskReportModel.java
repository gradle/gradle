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

import com.google.common.base.Strings;
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
    private final boolean detail;
    private final String group;

    public AggregateMultiProjectTaskReportModel(boolean mergeTasksWithSameName, boolean detail, String group) {
        this.mergeTasksWithSameName = mergeTasksWithSameName;
        this.detail = detail;
        this.group = Strings.isNullOrEmpty(group) ? null : group.toLowerCase();
    }

    public void add(TaskReportModel project) {
        projects.add(project);
    }

    public void build() {
        groups = TreeMultimap.create(new Comparator<String>() {
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
        for (TaskReportModel project : projects) {
            for (String group : project.getGroups()) {
                if (isVisible(group)) {
                    for (final TaskDetails task : project.getTasksForGroup(group)) {
                        groups.put(group, mergeTasksWithSameName ? new MergedTaskDetails(task) : task);
                    }
                }
            }
        }
    }

    private boolean isVisible(String group) {
        if (Strings.isNullOrEmpty(group)) {
            return detail;
        } else {
            return this.group == null || group.toLowerCase().equals(this.group);
        }
    }

    @Override
    public Set<String> getGroups() {
        return groups.keySet();
    }

    @Override
    public Set<TaskDetails> getTasksForGroup(String group) {
        return groups.get(group);
    }

    private static class MergedTaskDetails implements TaskDetails {
        private final TaskDetails task;
        private Path cachedPath;

        public MergedTaskDetails(TaskDetails task) {
            this.task = task;
        }

        @Override
        public Path getPath() {
            if (cachedPath == null) {
                cachedPath = Path.path(task.getPath().getName());
            }
            return cachedPath;
        }

        @Override
        public String getDescription() {
            return task.getDescription();
        }
    }
}
