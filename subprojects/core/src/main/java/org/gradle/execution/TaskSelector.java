/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.specs.Spec;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

@ServiceScope(Scopes.BuildTree.class)
public interface TaskSelector {
    Spec<Task> getFilter(SelectionContext context, ProjectState project, String taskName, boolean selectAllMatching);

    TaskSelection getSelection(SelectionContext context, ProjectState project, String taskName, boolean selectAllMatching);

    class SelectionContext {
        private final Path originalPath;
        private final String type;

        public SelectionContext(Path originalPath, String type) {
            this.originalPath = originalPath;
            this.type = type;
        }

        public Path getOriginalPath() {
            return originalPath;
        }

        public String getType() {
            return type;
        }
    }
}
