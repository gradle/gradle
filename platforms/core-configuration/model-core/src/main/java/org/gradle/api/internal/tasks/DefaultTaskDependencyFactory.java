/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class DefaultTaskDependencyFactory implements TaskDependencyFactory {
    @Nullable
    private final TaskResolver taskResolver;

    @Nullable
    private final TaskDependencyUsageTracker taskDependencyUsageTracker;

    public static TaskDependencyFactory withNoAssociatedProject() {
        return new DefaultTaskDependencyFactory(null, null);
    }

    public static TaskDependencyFactory forProject(
        TaskResolver taskResolver,
        @Nullable TaskDependencyUsageTracker taskDependencyUsageTracker
    ) {
        return new DefaultTaskDependencyFactory(taskResolver, taskDependencyUsageTracker);
    }

    private DefaultTaskDependencyFactory(@Nullable TaskResolver taskResolver, @Nullable TaskDependencyUsageTracker taskDependencyUsageTracker) {
        this.taskResolver = taskResolver;
        this.taskDependencyUsageTracker = taskDependencyUsageTracker;
    }

    @Override
    public DefaultTaskDependency configurableDependency() {
        return new DefaultTaskDependency(taskResolver, taskDependencyUsageTracker);
    }

    @Override
    public DefaultTaskDependency configurableDependency(ImmutableSet<Object> dependencies) {
        return new DefaultTaskDependency(taskResolver, dependencies, taskDependencyUsageTracker);
    }

    @Override
    public DefaultTaskDependency visitingDependencies(Consumer<? super TaskDependencyResolveContext> visitDependencies) {
        return configurableDependency(ImmutableSet.of(new DefaultTaskDependency.VisitBehavior(visitDependencies)));
    }
}
