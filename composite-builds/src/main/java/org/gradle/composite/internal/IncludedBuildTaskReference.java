/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskReference;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.util.Path;

public class IncludedBuildTaskReference implements TaskReference, TaskDependencyContainer {
    private final String taskPath;
    private final IncludedBuildState includedBuild;

    public IncludedBuildTaskReference(IncludedBuildState includedBuild, String taskPath) {
        this.includedBuild = includedBuild;
        this.taskPath = taskPath;
    }

    @Override
    public String getName() {
        return Path.path(taskPath).getName();
    }

    public BuildIdentifier getBuildIdentifier() {
        return includedBuild.getBuildIdentifier();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(resolveTask());
    }

    private Task resolveTask() {
        includedBuild.ensureProjectsConfigured();
        return includedBuild.getMutableModel().getRootProject().getTasks().getByPath(taskPath);
    }
}
