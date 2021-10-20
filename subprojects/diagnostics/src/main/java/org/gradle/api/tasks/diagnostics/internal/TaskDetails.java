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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Task;
import org.gradle.api.tasks.diagnostics.BuildEnvironmentReportTask;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Objects;

public interface TaskDetails {
    Path getPath();

    default String getName() {
        return Objects.requireNonNull(getPath().getName());
    }

    @Nullable
    String getDescription();

    String getType();

    boolean isDecoratedType();

    static TaskDetails of(Path path, Task task) {
        return new TaskDetails() {
            private static final String DECORATED_SUFFIX = "_Decorated";

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
                return isDecoratedType() ? StringUtils.removeEnd(fullTaskTypeName, DECORATED_SUFFIX) : fullTaskTypeName;
            }

            @Override
            public boolean isDecoratedType() {
                return fullTaskTypeName.endsWith(DECORATED_SUFFIX);
            }
        };
    }
}
