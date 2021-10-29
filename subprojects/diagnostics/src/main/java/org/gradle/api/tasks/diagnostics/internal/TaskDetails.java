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
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.util.Path;

import javax.annotation.Nullable;

public interface TaskDetails {
    Path getPath();

    @Nullable
    String getDescription();

    String getTypeName();

    static TaskDetails of(Path path, Task task) {
        return of(path, new DslObject(task).getPublicType().getFullyQualifiedName(), task.getDescription());
    }

    static TaskDetails of(Path path, String typeName, @Nullable String description) {
        return new DefaultTaskDetails(path, typeName, description);
    }

    final class DefaultTaskDetails implements TaskDetails {
        private final Path path;
        private final String typeName;
        @Nullable private final String description;

        private DefaultTaskDetails(Path path, String typeName, @Nullable String description) {
            this.path = path;
            this.typeName = typeName;
            this.description = description;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public String getTypeName() {
            return typeName;
        }

        @Nullable
        @Override
        public String getDescription() {
            return description;
        }

    }
}
