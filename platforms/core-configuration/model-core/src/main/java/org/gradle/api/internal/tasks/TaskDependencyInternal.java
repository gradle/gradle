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

package org.gradle.api.internal.tasks;

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

public interface TaskDependencyInternal extends TaskDependency {
    /**
     * {@inheritDoc}
     *
     * This method inherited from the internal interface should only be used by external code, such as plugins or build scripts,
     * as it may trigger warnings on usages that are considered "invalid" in the third-party code. If the usages
     * are still valid in the Gradle codebase, they should be changed to {@link TaskDependencyInternal#getDependenciesForInternalUse(Task)}.
     *
     * @see TaskDependencyUtil#getDependenciesForInternalUse(TaskDependency, Task)
     */
    @Override
    Set<? extends Task> getDependencies(@Nullable Task task);

    /**
     * This function is intended for calling from the Gradle implementation code. The implementation of this function should not perform
     * any validation that is specific to calls from third-party code.
     *
     * Semantically this function should be equivalent to {@link TaskDependency#getDependencies(Task)} without the additional contract of
     * {@link TaskDependencyInternal#getDependencies(Task)}.
     */
    Set<? extends Task> getDependenciesForInternalUse(@Nullable Task task);

    TaskDependencyInternal EMPTY = new TaskDependencyContainerInternal() {
        @Override
        public Set<? extends Task> getDependenciesForInternalUse(@Nullable Task task) {
            return Collections.emptySet();
        }

        @Override
        public Set<? extends Task> getDependencies(@Nullable Task task) {
            return getDependenciesForInternalUse(task);
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
        }
    };
}

