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

package org.gradle.language.base.internal;

import org.gradle.api.Task;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.BuildableModelElement;

import java.util.Collections;
import java.util.Set;

public class AbstractBuildableModelElement implements BuildableModelElement {
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
    private Task lifecycleTask;

    public void setLifecycleTask(Task lifecycleTask) {
        this.lifecycleTask = lifecycleTask;
        lifecycleTask.dependsOn(buildDependencies);
    }

    public TaskDependency getBuildDependencies() {
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task other) {
                if (lifecycleTask == null) {
                    return buildDependencies.getDependencies(other);
                }
                return Collections.singleton(lifecycleTask);
            }
        };
    }

    public void builtBy(Object... tasks) {
        buildDependencies.add(tasks);
    }

    public boolean hasBuildDependencies() {
        // TODO:DAZ There must be a better way to get independent of a Task
        return buildDependencies.getDependencies(lifecycleTask).size() > 0;
    }
}
