/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Marker type passed to {@link TaskDependencyResolveContext#add} during parallel task
 * dependency resolution to defer cross-project dependency lookups. Intercepted by
 * {@link ParallelCachingTaskDependencyResolveContext#add} before reaching the graph walker.
 */
@NullMarked
public abstract class DeferredCrossProjectDependency {

    private DeferredCrossProjectDependency() {
    }

    /**
     * Find a task by name in a specific project, identified by its build-tree identity path.
     */
    public static class ByProjectTask extends DeferredCrossProjectDependency {
        private final Path targetProjectIdentityPath;
        private final String taskName;

        public ByProjectTask(Path targetProjectIdentityPath, String taskName) {
            this.targetProjectIdentityPath = targetProjectIdentityPath;
            this.taskName = taskName;
        }

        public Path getTargetProjectIdentityPath() {
            return targetProjectIdentityPath;
        }

        public String getTaskName() {
            return taskName;
        }
    }

    /**
     * Deferred resolution that needs access to multiple projects.
     * Carries the original resolution logic as a {@link Consumer} so it can be
     * re-executed later under proper locking without duplicating implementation details.
     */
    public static class AllProjectsSearch extends DeferredCrossProjectDependency {
        private final Consumer<TaskDependencyResolveContext> resolutionAction;

        public AllProjectsSearch(Consumer<TaskDependencyResolveContext> resolutionAction) {
            this.resolutionAction = resolutionAction;
        }

        public Consumer<TaskDependencyResolveContext> getResolutionAction() {
            return resolutionAction;
        }
    }
}
