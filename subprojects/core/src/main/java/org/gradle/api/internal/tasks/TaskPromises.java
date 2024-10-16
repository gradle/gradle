/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.project.ProjectInternal;

public class TaskPromises {
    public static TaskPromise of(Task task) {
        return new TaskPromise() {
            @Override
            public Task get() {
                return task;
            }
        };
    }

    public static TaskPromise missing(String path, ProjectInternal project) {
        return new TaskPromise() {
            @Override
            public Task get() {
                throw unknownTaskException(path, project);
            }
        };
    }

    public static TaskPromise of(ProjectInternal project, String path) {
        return new TaskPromise() {
            @Override
            public Task get() {
                project.getOwner().ensureTasksDiscovered();
                String taskName = StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR);
                Task task = project.getTasks().findByName(taskName);
                if (task == null) {
                    throw unknownTaskException(taskName, project);
                }
                return task;
            }
        };
    }

    private static UnknownTaskException unknownTaskException(String path, ProjectInternal project) {
        return new UnknownTaskException(String.format("Task with path '%s' not found in %s.", path, project));
    }
}
