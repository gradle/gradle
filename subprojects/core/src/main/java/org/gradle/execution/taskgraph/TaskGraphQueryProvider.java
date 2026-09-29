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

import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.ValueSupplier;
import org.jspecify.annotations.NullMarked;

/**
 * A lazy question about the task graph: "is this task scheduled?" or "is any task of this type scheduled?".
 *
 * <p>It can only be answered once the task graph is ready. It always reports a changing execution time value,
 * so the configuration cache stores the provider itself rather than an answer. Its only non-trivial field is the
 * build-scoped {@link ScheduledTasks} service, which the configuration cache stores as a service reference,
 * so a loaded provider reads the scheduled tasks of the build that loads it.</p>
 */
@NullMarked
public final class TaskGraphQueryProvider extends AbstractMinimalProvider<Boolean> {

    public enum Kind {
        TASK_PATH,
        TASK_TYPE
    }

    private final ScheduledTasks scheduledTasks;
    private final Kind kind;
    private final String argument;

    public TaskGraphQueryProvider(ScheduledTasks scheduledTasks, Kind kind, String argument) {
        this.scheduledTasks = scheduledTasks;
        this.kind = kind;
        this.argument = argument;
    }

    @Override
    public Class<Boolean> getType() {
        return Boolean.class;
    }

    @Override
    protected Value<? extends Boolean> calculateOwnValue(ValueConsumer consumer) {
        ScheduledTasksIndex index = scheduledTasks.getIndex();
        if (index == null) {
            throw new IllegalStateException(
                "Cannot query " + describeQuery() + " before the task graph is ready. " +
                    "Query it during task execution instead, for example from onlyIf { } or as a task input."
            );
        }
        boolean answer = kind == Kind.TASK_PATH ? index.containsTaskPath(argument) : index.containsTaskType(argument);
        return Value.of(answer);
    }

    @Override
    public ValueSupplier.ExecutionTimeValue<? extends Boolean> calculateExecutionTimeValue() {
        return ValueSupplier.ExecutionTimeValue.changingValue(this);
    }

    private String describeQuery() {
        return kind == Kind.TASK_PATH
            ? "whether task '" + argument + "' is scheduled"
            : "whether any task of type '" + argument + "' is scheduled";
    }

    @Override
    protected String toStringNoReentrance() {
        return "task graph query: " + describeQuery();
    }
}
