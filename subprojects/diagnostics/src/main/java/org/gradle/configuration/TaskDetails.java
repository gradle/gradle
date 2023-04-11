/*
 * Copyright 2022 the original author or authors.
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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.TaskOptionsGenerator;
import org.gradle.api.internal.tasks.options.OptionDescriptor;
import org.gradle.api.internal.tasks.options.OptionReader;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A configuration-cache friendly view of a {@link Task} that only projects information
 * that is relevant for the {@link Help} task.
 */
@NonNullApi
class TaskDetails {
    final static Comparator<TaskDetails> DEFAULT_COMPARATOR = (o1, o2) -> {
        // tasks in higher-up projects first
        int depthCompare = o1.getProjectDepth() - o2.getProjectDepth();
        if (depthCompare != 0) {
            return depthCompare;
        }
        return o1.getPath().compareTo(o2.getPath());
    };

    /**
     * A read-only projection for OptionDescriptor details relevant for the help task.
     */
    public static class OptionDetails {
        private final String name;

        private final String description;

        private final Set<String> availableValues;
        public OptionDetails(String name, String description, Set<String> availableValues) {
            this.name = name;
            this.description = description;
            this.availableValues = availableValues;
        }

        private static OptionDetails from(OptionDescriptor option) {
            return new OptionDetails(option.getName(), option.getDescription(), option.getAvailableValues());
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public Set<String> getAvailableValues() {
            return availableValues;
        }
    }

    private final String path;

    private final String taskType;

    private final String shortTypeName;

    private final String description;

    private final String group;

    private final int projectDepth;

    private final List<OptionDetails> options;

    private TaskDetails(String path, String taskType, String shortTypeName, @Nullable String description, @Nullable String group, int projectDepth, List<OptionDetails> options) {
        this.path = path;
        this.taskType = taskType;
        this.shortTypeName = shortTypeName;
        this.description = description;
        this.group = group;
        this.projectDepth = projectDepth;
        this.options = options;
    }

    /**
     * The task type implementation Java class.
     */
    public String getTaskType() {
        return taskType;
    }

    public String getShortTypeName() {
        return shortTypeName;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Nullable
    public String getGroup() {
        return group;
    }

    public String getPath() {
        return path;
    }

    public int getProjectDepth() {
        return projectDepth;
    }

    public List<OptionDetails> getOptions() {
        return options;
    }

    public static TaskDetails from(Task task, OptionReader optionReader) {
        String path = task.getPath();
        int projectDepth = StringUtils.countMatches(path, Project.PATH_SEPARATOR);
        List<OptionDetails> options = TaskOptionsGenerator.generate(task, optionReader).getAll().stream().map(OptionDetails::from).collect(Collectors.toList());
        Class<?> declaredTaskType = getDeclaredTaskType(task);
        String taskType = declaredTaskType.getName();
        String shortTypeName = declaredTaskType.getSimpleName();
        return new TaskDetails(path, taskType, shortTypeName, task.getDescription(), task.getGroup(), projectDepth, options);
    }

    private static Class<?> getDeclaredTaskType(Task original) {
        Class<?> clazz = new DslObject(original).getDeclaredType();
        if (clazz.equals(DefaultTask.class)) {
            return org.gradle.api.Task.class;
        } else {
            return clazz;
        }
    }
}
