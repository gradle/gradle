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

import com.google.common.collect.Iterables;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.diagnostics.BuildEnvironmentReportTask;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.stream.Collectors;

public interface TaskDetails {
    Path getPath();

    default String getName() {
        return Objects.requireNonNull(getPath().getName());
    }

    @Nullable
    String getDescription();

    String getType();

    static TaskDetails of(Path path, Task task) {
        return new TaskDetails() {
            private final String fullTaskTypeName;
            {
                if (BuildEnvironmentReportTask.class.isAssignableFrom(task.getClass())) {
                    fullTaskTypeName = ((BuildEnvironmentReportTask) task).getTaskIdentity().getTaskType().getName();
                } else {
                    fullTaskTypeName = task.getClass().getName();
                }
            }

            @Override
            public Path getPath() {
                return path;
            }

            @Override
            @Nullable
            public String getDescription() {
                return task.getDescription();
            }

            @Override
            public String getType() {
                return fullTaskTypeName;
            }
        };
    }

    default Task findTask(Project project) {
        final Project containingProject;
        if (getPath().getPath().contains(":")) {
            final String normalizedProjectPath = normalizePathToTaskProject(getPath().getPath());
            containingProject = Iterables.getOnlyElement(project.getAllprojects().stream()
                .filter(p -> p.getPath().equals(normalizedProjectPath))
                .collect(Collectors.toList()));
        } else {
            containingProject = project;
        }

        return Iterables.getOnlyElement(containingProject.getTasksByName(getName(), false));
    }

    default String normalizePathToTaskProject(String path) {
        final String pathWithExplicitRoot = path.startsWith(":") ? path : ":" + path;
        if (!path.contains(":")) {
            return pathWithExplicitRoot;
        } else {
            return pathWithExplicitRoot.substring(0, pathWithExplicitRoot.lastIndexOf(":"));
        }
    }
}
