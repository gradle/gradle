/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.internal.build.BuildState;
import org.gradle.util.Path;

/**
 * Resolves tasks within a single build, given some absolute task path.
 */
public class BuildScopedTaskResolver implements TaskResolver {

    private final BuildState build;

    public BuildScopedTaskResolver(BuildState build) {
        this.build = build;
    }

    @Override
    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    public Task resolveTask(Path path) {
        String targetTaskName = path.getName();
        if (targetTaskName == null) {
            assert path == Path.ROOT;
            throw new IllegalArgumentException("The root path is not a valid task path");
        }

        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(String.format("Cannot resolve task at path '%s' since the path is not absolute.", path));
        }

        build.ensureProjectsConfigured();

        Path targetProjectPath = path.getParent() == null ? Path.ROOT : path.getParent();
        ProjectState projectState = build.getProjects().getProject(targetProjectPath);
        projectState.ensureTasksDiscovered();
        return projectState.getMutableModel().getTasks().getByName(targetTaskName);
    }

}
