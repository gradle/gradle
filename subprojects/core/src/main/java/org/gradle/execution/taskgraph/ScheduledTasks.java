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

package org.gradle.execution.taskgraph;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Task;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The tasks scheduled in this build, available once the task graph is ready.
 *
 * <p>A build-scoped service, so that a {@link TaskGraphQueryProvider} stored by the configuration cache
 * refers to it as a service and is bound to the instance of the build that loads it.</p>
 *
 * <p>Task types are matched by the fully qualified name of the type each task was registered with,
 * including all supertypes and interfaces, so that the same type loaded by different class loaders matches.</p>
 */
@NullMarked
@ServiceScope(Scope.Build.class)
public class ScheduledTasks {

    /**
     * The tasks of the current task graph with their lazily computed lookups, swapped as one unit.
     * Null until the task graph is ready.
     */
    private volatile @Nullable Snapshot snapshot;

    public boolean isReady() {
        return snapshot != null;
    }

    public boolean containsTaskPath(String taskPath) {
        return current().taskPaths.get().contains(taskPath);
    }

    public boolean containsTaskType(String taskTypeName) {
        return current().taskTypeNames.get().contains(taskTypeName);
    }

    void set(List<Task> tasks) {
        this.snapshot = new Snapshot(ImmutableList.copyOf(tasks));
    }

    void clear() {
        this.snapshot = null;
    }

    private Snapshot current() {
        Snapshot current = snapshot;
        if (current == null) {
            throw new IllegalStateException("The task graph is not ready yet.");
        }
        return current;
    }

    private static final class Snapshot {
        final Supplier<Set<String>> taskPaths;
        final Supplier<Set<String>> taskTypeNames;

        Snapshot(List<Task> tasks) {
            this.taskPaths = Suppliers.memoize(() -> collectPaths(tasks));
            this.taskTypeNames = Suppliers.memoize(() -> collectTypeNames(tasks));
        }
    }

    private static Set<String> collectPaths(List<Task> tasks) {
        ImmutableSet.Builder<String> paths = ImmutableSet.builderWithExpectedSize(tasks.size());
        for (Task task : tasks) {
            paths.add(task.getPath());
        }
        return paths.build();
    }

    private static Set<String> collectTypeNames(List<Task> tasks) {
        Set<Class<?>> seen = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Task task : tasks) {
            addTypeAndSupertypes(registeredTypeOf(task), seen, names);
        }
        return ImmutableSet.copyOf(names);
    }

    private static Class<?> registeredTypeOf(Task task) {
        Class<?> type = task instanceof TaskInternal
            ? ((TaskInternal) task).getTaskIdentity().getTaskType()
            : task.getClass();
        return GeneratedSubclasses.unpack(type);
    }

    private static void addTypeAndSupertypes(@Nullable Class<?> type, Set<Class<?>> seen, Set<String> names) {
        if (type == null || type == Object.class || !seen.add(type)) {
            return;
        }
        names.add(type.getName());
        addTypeAndSupertypes(type.getSuperclass(), seen, names);
        for (Class<?> anInterface : type.getInterfaces()) {
            addTypeAndSupertypes(anInterface, seen, names);
        }
    }
}
