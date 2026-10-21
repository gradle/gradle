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

package org.gradle.api.internal;

import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskDependencyInternal;

/**
 * Utilities for operating on {@link TaskInternal}.
 * <p>
 * {@link TaskInternal} is in practice a public type, as is leaks into the public API
 * via {@link org.gradle.api.DefaultTask}. For this reason, we make an effort to avoid leaking
 * additional internal types through {@link TaskInternal}. This class exposes the interface that
 * {@link TaskInternal} should if it were not effectively public.
 */
public class TaskInternalUtil {

    /**
     * @see Task#getTaskDependencies()
     */
    public static TaskDependencyInternal getTaskDependencies(Task task) {
        return (TaskDependencyInternal) task.getTaskDependencies();
    }

    /**
     * @see TaskInternal#getLifecycleDependencies()
     */
    public static TaskDependencyInternal getLifecycleDependencies(TaskInternal task) {
        return (TaskDependencyInternal) task.getLifecycleDependencies();
    }

    /**
     * @see Task#getFinalizedBy()
     */
    public static TaskDependencyInternal getFinalizedBy(Task task) {
        return (TaskDependencyInternal) task.getFinalizedBy();
    }

    /**
     * @see Task#getMustRunAfter()
     */
    public static TaskDependencyInternal getMustRunAfter(TaskInternal task) {
        return (TaskDependencyInternal) task.getMustRunAfter();
    }

    /**
     * @see Task#getShouldRunAfter()
     */
    public static TaskDependencyInternal getShouldRunAfter(TaskInternal task) {
        return (TaskDependencyInternal) task.getShouldRunAfter();
    }

}
