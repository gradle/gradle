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

import org.gradle.api.Task;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link TaskDependencyResolveContext} that records the dependencies and failures
 * it visited, and replays them in order when visited as a {@link TaskDependencyContainer}.
 */
public final class RecordingTaskDependencyResolveContext implements TaskDependencyResolveContext, TaskDependencyContainer {

    private final @Nullable Task task;
    private @Nullable List<Object> recorded;

    public RecordingTaskDependencyResolveContext(@Nullable Task task) {
        this.task = task;
    }

    @Override
    public void add(Object dependency) {
        if (recorded == null) {
            recorded = new ArrayList<>();
        }
        recorded.add(dependency);
    }

    @Override
    public void visitFailure(Throwable failure) {
        if (recorded == null) {
            recorded = new ArrayList<>();
        }
        recorded.add(failure);
    }

    @Override
    public @Nullable Task getTask() {
        return task;
    }

    /**
     * Returns true if no dependencies or failures have been recorded.
     */
    public boolean isEmpty() {
        return recorded == null;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (recorded == null) {
            return;
        }

        for (Object item : recorded) {
            if (item instanceof Throwable) {
                context.visitFailure((Throwable) item);
            } else {
                context.add(item);
            }
        }
    }

}
