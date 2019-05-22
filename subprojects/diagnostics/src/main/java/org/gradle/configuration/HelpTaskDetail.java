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

import java.util.List;
import java.util.Map;
import java.util.Set;


class HelpTaskDetail {

    private final String taskPath;

    private final List<SelectedTaskType> selectedTaskTypes;

    HelpTaskDetail(String taskPath, List<SelectedTaskType> selectedTaskTypes) {
        this.taskPath = taskPath;
        this.selectedTaskTypes = selectedTaskTypes;
    }

    String getTaskPath() {
        return taskPath;
    }

    List<SelectedTaskType> getSelectedTaskTypes() {
        return selectedTaskTypes;
    }

    static class SelectedTaskType {

        private final String simpleName;
        private final String qualifiedName;
        private final Map<String, TaskAttributes> attributesByPath;
        private final List<TaskOption> options;

        SelectedTaskType(String simpleName, String qualifiedName, Map<String, TaskAttributes> attributesByPath, List<TaskOption> options) {
            this.simpleName = simpleName;
            this.qualifiedName = qualifiedName;
            this.attributesByPath = attributesByPath;
            this.options = options;
        }

        String getSimpleName() {
            return simpleName;
        }

        String getQualifiedName() {
            return qualifiedName;
        }

        Map<String, TaskAttributes> getAttributesByPath() {
            return attributesByPath;
        }

        List<TaskOption> getOptions() {
            return options;
        }
    }

    static class TaskAttributes {

        private final String description;
        private final String group;

        TaskAttributes(String description, String group) {
            this.description = description;
            this.group = group;
        }

        String getDescription() {
            return description;
        }

        String getGroup() {
            return group;
        }
    }

    static class TaskOption {

        private final String name;
        private final String description;
        private final Set<String> availableValues;

        TaskOption(String name, String description, Set<String> availableValues) {
            this.name = name;
            this.description = description;
            this.availableValues = availableValues;
        }

        String getName() {
            return name;
        }

        String getDescription() {
            return description;
        }

        Set<String> getAvailableValues() {
            return availableValues;
        }
    }
}
