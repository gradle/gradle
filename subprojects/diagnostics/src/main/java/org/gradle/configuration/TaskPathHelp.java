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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;


class TaskPathHelp {

    final String taskPath;
    final List<TaskType> taskTypes;

    TaskPathHelp(String taskPath, List<TaskType> taskTypes) {
        this.taskPath = taskPath;
        this.taskTypes = taskTypes;
    }

    static class TaskType {

        final String simpleName;
        final String qualifiedName;
        final Map<String, TaskAttributes> attributesByPath;
        final List<TaskOption> options;

        TaskType(String simpleName, String qualifiedName, Map<String, TaskAttributes> attributesByPath, List<TaskOption> options) {
            this.simpleName = simpleName;
            this.qualifiedName = qualifiedName;
            this.attributesByPath = attributesByPath;
            this.options = options;
        }
    }

    static class TaskAttributes {

        @Nullable
        final String description;

        @Nullable
        final String group;

        TaskAttributes(@Nullable String description, @Nullable String group) {
            this.description = description;
            this.group = group;
        }
    }

    static class TaskOption {

        final String name;
        final String description;
        final Set<String> availableValues;

        TaskOption(String name, String description, Set<String> availableValues) {
            this.name = name;
            this.description = description;
            this.availableValues = availableValues;
        }
    }
}
