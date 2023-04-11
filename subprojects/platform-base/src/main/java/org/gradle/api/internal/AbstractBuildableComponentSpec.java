/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.BuildableComponentSpec;
import org.gradle.api.CheckableComponentSpec;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.platform.base.component.internal.AbstractComponentSpec;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;

import javax.annotation.Nullable;

public abstract class AbstractBuildableComponentSpec extends AbstractComponentSpec implements BuildableComponentSpec, CheckableComponentSpec {
    private final DefaultTaskDependency buildTaskDependencies = new DefaultTaskDependency();
    private Task buildTask;
    private final DefaultTaskDependency checkTaskDependencies = new DefaultTaskDependency();
    private Task checkTask;

    public AbstractBuildableComponentSpec(ComponentSpecIdentifier identifier, Class<? extends BuildableComponentSpec> publicType) {
        super(identifier, publicType);
    }

    @Override
    @Nullable
    public Task getBuildTask() {
        return buildTask;
    }

    @Override
    public void setBuildTask(@Nullable Task buildTask) {
        this.buildTask = buildTask;
        if (buildTask != null) {
            buildTask.dependsOn(buildTaskDependencies);
        }
    }

    @Override
    public TaskDependency getBuildDependencies() {
        // TODO maybe inject the task dependency factory into this type hierarchy, too
        final TaskDependencyFactory taskDependencyFactory = DefaultTaskDependencyFactory.withNoAssociatedProject();
        return taskDependencyFactory.visitingDependencies(context -> {
            if (buildTask == null) {
                context.add(buildTaskDependencies);
            } else {
                context.add(buildTask);
            }
        });
    }

    @Override
    public void builtBy(Object... tasks) {
        buildTaskDependencies.add(tasks);
    }

    @Override
    public boolean hasBuildDependencies() {
        return buildTaskDependencies.getDependenciesForInternalUse(buildTask).size() > 0;
    }

    @Nullable
    @Override
    public Task getCheckTask() {
        return checkTask;
    }

    @Override
    public void setCheckTask(@Nullable Task checkTask) {
        this.checkTask = checkTask;
        if (checkTask != null) {
            checkTask.dependsOn(checkTaskDependencies);
        }
    }

    @Override
    public void checkedBy(Object... tasks) {
        checkTaskDependencies.add(tasks);
    }
}
