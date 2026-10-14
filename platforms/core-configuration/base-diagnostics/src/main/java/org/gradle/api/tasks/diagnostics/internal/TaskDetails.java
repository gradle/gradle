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

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskProvenanceUtil;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

public interface TaskDetails {
    Path getPath();

    @Nullable
    String getDescription();

    String getTypeName();

    @Nullable
    String getProvenance();

    /**
     * The build tree path of the task, or null when this entry stands for tasks with the same name in several projects.
     */
    @Nullable
    Path getBuildTreePath();

    static TaskDetails of(Path path, Task task) {
        TaskInternal taskInternal = (TaskInternal) task;
        return of(path, new DslObject(task).getPublicType().getFullyQualifiedName(), task.getDescription(), TaskProvenanceUtil.getProvenance(taskInternal).orElse(null), taskInternal.getTaskIdentity().getBuildTreePath());
    }

    static TaskDetails of(Path path, String typeName, @Nullable String description, @Nullable String provenance) {
        return of(path, typeName, description, provenance, null);
    }

    static TaskDetails of(Path path, String typeName, @Nullable String description, @Nullable String provenance, @Nullable Path buildTreePath) {
        return new DefaultTaskDetails(path, typeName, description, provenance, buildTreePath);
    }

    final class DefaultTaskDetails implements TaskDetails {
        private final Path path;
        private final String typeName;
        @Nullable private final String provenance;
        @Nullable private final String description;
        @Nullable private final Path buildTreePath;

        private DefaultTaskDetails(Path path, String typeName, @Nullable String description, @Nullable String provenance, @Nullable Path buildTreePath) {
            this.path = path;
            this.typeName = typeName;
            this.provenance = provenance;
            this.description = description;
            this.buildTreePath = buildTreePath;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Nullable
        @Override
        public Path getBuildTreePath() {
            return buildTreePath;
        }

        @Override
        public String getTypeName() {
            return typeName;
        }

        @Override
        public String getProvenance() {
            return provenance;
        }

        @Nullable
        @Override
        public String getDescription() {
            return description;
        }
    }
}
