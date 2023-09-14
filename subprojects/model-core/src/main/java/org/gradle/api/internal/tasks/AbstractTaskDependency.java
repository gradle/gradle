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

import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.artifacts.transform.TransformNodeDependency;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static org.gradle.api.internal.tasks.WorkDependencyResolver.TASK_AS_TASK;

@NonNullApi
public abstract class AbstractTaskDependency implements TaskDependencyContainerInternal {

    @Nullable
    private final TaskDependencyUsageTracker dependencyUsageTracker;

    public AbstractTaskDependency(@Nullable TaskDependencyUsageTracker dependencyUsageTracker) {
        this.dependencyUsageTracker = dependencyUsageTracker;
    }

    private static final WorkDependencyResolver<Task> IGNORE_ARTIFACT_TRANSFORM_RESOLVER = new WorkDependencyResolver<Task>() {
        @Override
        public boolean resolve(Task task, Object node, Action<? super Task> resolveAction) {
            // Ignore artifact transforms
            return node instanceof TransformNodeDependency || node instanceof WorkNodeAction;
        }
    };

    private Supplier<String> toStringProvider = null;

    public void setToStringProvider(Supplier<String> toStringProvider) {
        this.toStringProvider = toStringProvider;
    }

    @Override
    public String toString() {
        return toStringProvider != null ? toStringProvider.get() : super.toString();
    }

    @Override
    public Set<? extends Task> getDependencies(@Nullable Task task) {
        Set<? extends Task> result = getDependenciesForInternalUse(task);
        if (dependencyUsageTracker != null) {
            dependencyUsageTracker.onTaskDependencyUsage(result);
        }
        return result;
    }

    public Set<? extends Task> getDependenciesForInternalUse(@Nullable Task task) {
        CachingTaskDependencyResolveContext<Task> context = new CachingTaskDependencyResolveContext<Task>(
            asList(TASK_AS_TASK, IGNORE_ARTIFACT_TRANSFORM_RESOLVER)
        );
        return context.getDependencies(task, this);
    }
}
