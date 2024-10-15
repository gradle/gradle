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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;

public class AggregateMultiProjectTaskReportModel implements TaskReportModel {
    private final List<TaskReportModel> projects = new ArrayList<>();
    private SetMultimap<String, TaskDetails> groups;
    private final boolean mergeTasksWithSameName;
    private final boolean detail;
    private final List<String> groupsOfInterest;

    public AggregateMultiProjectTaskReportModel(boolean mergeTasksWithSameName, boolean detail, String group, List<String> groups) {
        this.mergeTasksWithSameName = mergeTasksWithSameName;
        this.detail = detail;
        this.groupsOfInterest = Stream.concat(isNullOrEmpty(group) ? Stream.empty() : Stream.of(group), groups.stream())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    public void add(TaskReportModel project) {
        projects.add(project);
    }

    public void build() {
        groups = TreeMultimap.create(String::compareToIgnoreCase, Comparator.comparing(TaskDetails::getPath));
        for (TaskReportModel project : projects) {
            for (String group : project.getGroups()) {
                if (isVisible(group)) {
                    for (final TaskDetails task : project.getTasksForGroup(group)) {
                        groups.put(group, mergeTasksWithSameName ? mergedTaskDetails(task) : task);
                    }
                }
            }
        }
    }

    private TaskDetails mergedTaskDetails(TaskDetails task) {
        return TaskDetails.of(
            Path.path(task.getPath().getName()),
            task.getTypeName(),
            task.getDescription()
        );
    }

    private boolean isVisible(String group) {
        if (Strings.isNullOrEmpty(group)) {
            return detail;
        } else {
            return groupsOfInterest.isEmpty() || groupsOfInterest.contains(group.toLowerCase(Locale.ROOT));
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
}
