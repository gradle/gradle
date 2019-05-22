/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.options.OptionDescriptor;
import org.gradle.api.internal.tasks.options.OptionReader;
import org.gradle.execution.TaskSelector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.gradle.util.CollectionUtils.collect;
import static org.gradle.util.CollectionUtils.filter;
import static org.gradle.util.CollectionUtils.sort;


class TaskPathHelpFactory {

    private static final Comparator<Class> CLASS_COMPARATOR = (c1, c2) -> c1.getSimpleName().compareTo(c2.getSimpleName());

    private final TaskSelector taskSelector;
    private final OptionReader optionReader;

    TaskPathHelpFactory(TaskSelector taskSelector, OptionReader optionReader) {
        this.taskSelector = taskSelector;
        this.optionReader = optionReader;
    }

    TaskPathHelp createFor(String taskPath) {

        List<Task> selectedTasks = sort(taskSelector.getSelection(taskPath).getTasks());
        ListMultimap<Class, Task> tasksByType = groupTasksByType(selectedTasks);

        List<TaskPathHelp.TaskType> taskTypes = new ArrayList<>();

        for (Class taskClass : sort(tasksByType.keySet(), CLASS_COMPARATOR)) {

            List<Task> tasks = tasksByType.get(taskClass);

            taskTypes.add(new TaskPathHelp.TaskType(
                taskClass.getSimpleName(),
                taskClass.getName(),
                collectAttributesByPath(tasks),
                collectTaskOptions(tasks)
            ));
        }

        return new TaskPathHelp(taskPath, taskTypes);
    }

    private static ListMultimap<Class, Task> groupTasksByType(List<Task> tasks) {
        Set<Class> taskTypes = new TreeSet<>(CLASS_COMPARATOR);
        taskTypes.addAll(collect(tasks, (Task task) -> getDeclaredTaskType(task)));

        ListMultimap<Class, Task> tasksByType = ArrayListMultimap.create();
        for (Class taskType : taskTypes) {
            tasksByType.putAll(taskType, filter(tasks, task -> getDeclaredTaskType(task).equals(taskType)));
        }
        return tasksByType;
    }

    private static Class getDeclaredTaskType(Task original) {
        Class clazz = new DslObject(original).getDeclaredType();
        if (clazz.equals(DefaultTask.class)) {
            return org.gradle.api.Task.class;
        } else {
            return clazz;
        }
    }

    private Map<String, TaskPathHelp.TaskAttributes> collectAttributesByPath(List<Task> tasks) {
        Map<String, TaskPathHelp.TaskAttributes> attributesByPath = new LinkedHashMap<>(tasks.size());
        for (Task task : tasks) {
            attributesByPath.put(task.getPath(), new TaskPathHelp.TaskAttributes(task.getDescription(), task.getGroup()));
        }
        return attributesByPath;
    }

    private List<TaskPathHelp.TaskOption> collectTaskOptions(List<Task> tasks) {

        List<OptionDescriptor> allOptions = new ArrayList<>();
        for (Task task : tasks) {
            allOptions.addAll(optionReader.getOptions(task));
        }
        Map<String, Set<String>> availableValuesByOption = groupAvailableValuesByOption(allOptions);
        Map<String, String> descriptionByOption = groupDescriptionByOption(allOptions);

        List<TaskPathHelp.TaskOption> taskOptions = new ArrayList<>(availableValuesByOption.size());
        for (String optionName : availableValuesByOption.keySet()) {
            String description = descriptionByOption.get(optionName);
            Set<String> availableValues = availableValuesByOption.get(optionName);
            taskOptions.add(new TaskPathHelp.TaskOption(optionName, description, availableValues));
        }
        return taskOptions;
    }

    private static Map<String, Set<String>> groupAvailableValuesByOption(List<OptionDescriptor> allOptions) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (OptionDescriptor option : allOptions) {
            if (result.containsKey(option.getName())) {
                Collection<String> commonValues = Sets.intersection(option.getAvailableValues(), result.get(option.getName()));
                result.put(option.getName(), new TreeSet<>(commonValues));
            } else {
                result.put(option.getName(), option.getAvailableValues());
            }
        }
        return result;
    }

    private static Map<String, String> groupDescriptionByOption(List<OptionDescriptor> allOptions) {
        Map<String, String> result = new HashMap<>(allOptions.size());
        for (OptionDescriptor option : allOptions) {
            result.put(option.getName(), option.getDescription());
        }
        return result;
    }
}
