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


class HelpTaskDetailFactory {

    private static final Comparator<Class> CLASS_COMPARATOR = (c1, c2) -> c1.getSimpleName().compareTo(c2.getSimpleName());

    private final TaskSelector taskSelector;
    private final OptionReader optionReader;

    HelpTaskDetailFactory(TaskSelector taskSelector, OptionReader optionReader) {
        this.taskSelector = taskSelector;
        this.optionReader = optionReader;
    }

    HelpTaskDetail createFor(String taskPath) {

        List<HelpTaskDetail.SelectedTaskType> selectedTaskTypes = new ArrayList<>();
        if (taskPath != null) {
            TaskSelector.TaskSelection selection = taskSelector.getSelection(taskPath);

            List<Task> tasks = sort(selection.getTasks());
            ListMultimap<Class, Task> classListMap = groupTasksByType(tasks);
            Set<Class> classes = classListMap.keySet();
            final List<Class> sortedClasses = sort(classes, CLASS_COMPARATOR);

            for (Class clazz : sortedClasses) {

                List<Task> tasksOfType = classListMap.get(clazz);
                String simpleName = clazz.getSimpleName();
                String qualifiedName = clazz.getName();

                List<String> paths = collect(tasksOfType, task -> task.getPath());

                Map<String, HelpTaskDetail.TaskAttributes> attributesByPath = new LinkedHashMap<>(paths.size());

                List<OptionDescriptor> allOptions = new ArrayList<>();
                for (Task task : tasksOfType) {
                    attributesByPath.put(task.getPath(), new HelpTaskDetail.TaskAttributes(task.getDescription(), task.getGroup()));
                    allOptions.addAll(optionReader.getOptions(task));
                }

                List<HelpTaskDetail.TaskOption> options = new ArrayList<>(paths.size());

                Map<String, Set<String>> optionToAvailableOptionsValues = optionToAvailableValues(allOptions);
                Map<String, String> optionToDescription = optionToDescription(allOptions);

                for (String optionName : optionToAvailableOptionsValues.keySet()) {
                    String description = optionToDescription.get(optionName);
                    Set<String> availableValues = optionToAvailableOptionsValues.get(optionName);
                    options.add(new HelpTaskDetail.TaskOption(optionName, description, availableValues));
                }

                selectedTaskTypes.add(new HelpTaskDetail.SelectedTaskType(simpleName, qualifiedName, attributesByPath, options));
            }
        }

        return new HelpTaskDetail(taskPath, selectedTaskTypes);
    }

    private static ListMultimap<Class, Task> groupTasksByType(List<Task> tasks) {
        final Set<Class> taskTypes = new TreeSet<>(CLASS_COMPARATOR);
        taskTypes.addAll(collect(tasks, (Task task) -> getDeclaredTaskType(task)));

        ListMultimap<Class, Task> tasksGroupedByType = ArrayListMultimap.create();
        for (final Class taskType : taskTypes) {
            tasksGroupedByType.putAll(taskType, filter(tasks, task -> getDeclaredTaskType(task).equals(taskType)));
        }
        return tasksGroupedByType;
    }

    private static Class getDeclaredTaskType(Task original) {
        Class clazz = new DslObject(original).getDeclaredType();
        if (clazz.equals(DefaultTask.class)) {
            return org.gradle.api.Task.class;
        } else {
            return clazz;
        }
    }

    private static Map<String, Set<String>> optionToAvailableValues(List<OptionDescriptor> allOptions) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (OptionDescriptor optionDescriptor : allOptions) {
            if (result.containsKey(optionDescriptor.getName())) {
                Collection<String> commonValues = Sets.intersection(optionDescriptor.getAvailableValues(), result.get(optionDescriptor.getName()));
                result.put(optionDescriptor.getName(), new TreeSet<>(commonValues));
            } else {
                result.put(optionDescriptor.getName(), optionDescriptor.getAvailableValues());
            }
        }
        return result;
    }

    private static Map<String, String> optionToDescription(List<OptionDescriptor> allOptions) {
        Map<String, String> result = new HashMap<>();
        for (OptionDescriptor optionDescriptor : allOptions) {
            result.put(optionDescriptor.getName(), optionDescriptor.getDescription());
        }
        return result;
    }
}
