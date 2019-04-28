/*
 * Copyright 2018 the original author or authors.
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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class FailureCollectingTaskDependencyResolveContext implements TaskDependencyResolveContext {
    private final Set<Object> seen = new HashSet<Object>();
    private final TaskDependencyResolveContext context;
    private final Set<Throwable> failures = new LinkedHashSet<Throwable>();

    public FailureCollectingTaskDependencyResolveContext(TaskDependencyResolveContext context) {
        this.context = context;
    }

    public Set<Throwable> getFailures() {
        return failures;
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
            context.add(dep);
        }
    }

    @Override
    public void visitFailure(Throwable failure) {
        failures.add(failure);
    }

    @Override
    public Task getTask() {
        return context.getTask();
    }
}
