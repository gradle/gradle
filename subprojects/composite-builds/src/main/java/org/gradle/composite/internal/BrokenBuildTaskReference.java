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

package org.gradle.composite.internal;

import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskReference;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.buildtree.ResilientTaskScheduling;
import org.jspecify.annotations.NullMarked;

/**
 * A reference to a task in an included build that tolerates the build failing to configure.
 * <p>
 * Looking the task up configures its build. When that fails, this reference contributes a node that fails
 * instead of the task, so only the tasks depending on it are skipped and the rest of the task run proceeds.
 */
@NullMarked
class BrokenBuildTaskReference implements TaskReference, TaskDependencyContainer {
    private final BuildState targetBuild;
    private final TaskReference delegate;
    private final ResilientTaskScheduling resilientTaskScheduling;
    // One instance per reference, so the same node is reused when the reference is visited more than once
    private final BrokenBuildTaskDependency brokenBuildTask;

    BrokenBuildTaskReference(BuildState targetBuild, String taskPath, TaskReference delegate, ResilientTaskScheduling resilientTaskScheduling) {
        this.targetBuild = targetBuild;
        this.delegate = delegate;
        this.resilientTaskScheduling = resilientTaskScheduling;
        this.brokenBuildTask = new BrokenBuildTaskDependency(targetBuild, taskPath);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (resilientTaskScheduling.canScheduleTasksFor(targetBuild)) {
            context.add(delegate);
        } else {
            context.add(brokenBuildTask);
        }
    }
}
