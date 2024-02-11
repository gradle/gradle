/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.internal.tasks.AbstractTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link TaskDependencyResolveContext} which visits incoming dependencies if they are {@link TaskDependencyContainer} instances.
 */
public abstract class AbstractTaskDependencyContainerVisitingContext extends AbstractTaskDependencyResolveContext {
    private final Set<Object> seen = new HashSet<>();
    protected final TaskDependencyResolveContext delegate;

    public AbstractTaskDependencyContainerVisitingContext(TaskDependencyResolveContext context) {
        this.delegate = context;
    }

    @Override
    public void add(Object dep) {
        if (!seen.add(dep)) {
            return;
        }
        if (dep instanceof TaskDependencyContainer) {
            TaskDependencyContainer container = (TaskDependencyContainer) dep;
            container.visitDependencies(this);
        } else {
            delegate.add(dep);
        }
    }

    @Nullable
    @Override
    public Task getTask() {
        return delegate.getTask();
    }
}
