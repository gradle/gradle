/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.tooling.internal.protocol.TaskVersion1;

import java.util.ArrayList;
import java.util.List;

public class TasksFactory {
    public List<TaskVersion1> create(Project project) {
        List<TaskVersion1> tasks = new ArrayList<TaskVersion1>();
        for (final Task task : project.getTasks()) {
            tasks.add(new DefaultTaskVersion1(task.getPath(), task.getName(), task.getDescription()));
        }
        return tasks;
    }

    private static class DefaultTaskVersion1 implements TaskVersion1 {
        private final String path;
        private final String name;
        private final String description;

        public DefaultTaskVersion1(String path, String name, String description) {
            this.path = path;
            this.name = name;
            this.description = description;
        }

        @Override
        public String toString() {
            return String.format("task '%s'", path);
        }

        public String getPath() {
            return path;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }
}
