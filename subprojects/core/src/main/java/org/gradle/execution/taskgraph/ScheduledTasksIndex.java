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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An immutable snapshot of the tasks scheduled in a build, answering path and type questions.
 *
 * <p>Types are matched by fully qualified name of the type each task was registered with, including all
 * supertypes and interfaces, so that the same type loaded by different class loaders matches.</p>
 */
@NullMarked
public final class ScheduledTasksIndex {

    private final Supplier<Set<String>> taskPaths;
    private final Supplier<Set<String>> taskTypeNames;

    public ScheduledTasksIndex(List<Task> tasks) {
        List<Task> snapshot = ImmutableList.copyOf(tasks);
        this.taskPaths = Suppliers.memoize(() -> collectPaths(snapshot));
        this.taskTypeNames = Suppliers.memoize(() -> collectTypeNames(snapshot));
    }

    public boolean containsTaskPath(String taskPath) {
        return taskPaths.get().contains(taskPath);
    }

    public boolean containsTaskType(String taskTypeName) {
        return taskTypeNames.get().contains(taskTypeName);
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
