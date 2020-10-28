/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.util.NameMatcher;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CompositeAwareTaskSelector implements TaskSelector {
    private final BuildStateRegistry buildStateRegistry;
    private final ProjectConfigurer projectConfigurer;
    private final TaskNameResolver taskNameResolver;

    public CompositeAwareTaskSelector(BuildStateRegistry buildStateRegistry, ProjectConfigurer projectConfigurer, TaskNameResolver taskNameResolver) {
        this.buildStateRegistry = buildStateRegistry;
        this.projectConfigurer = projectConfigurer;
        this.taskNameResolver = taskNameResolver;
    }

    @Override
    public Spec<Task> getFilter(String path) {
        TaskPath taskPath = TaskPath.from(path);
        BuildState build = findIncludedBuild(taskPath);
        if (build != null) {
            return getSelector(build).getFilter(taskPath.dropBuildName());
        } else {
            return getRootBuildSelector().getFilter(path);
        }
    }

    @Override
    public TaskSelection getSelection(String path) {
        TaskPath taskPath = TaskPath.from(path);
        BuildState build = findIncludedBuild(taskPath);
        if (build != null) {
            return getSelector(build).getSelection(taskPath.dropBuildName());
        } else {
            return getRootBuildSelector().getSelection(path);
        }
    }

    @Override
    public TaskSelection getSelection(String projectPath, File root, String path) {
        TaskPath taskPath = TaskPath.from(path);
        BuildState build = findIncludedBuild(taskPath);
        if (build != null) {
            return getSelector(build).getSelection(projectPath, root, taskPath.dropBuildName());
        } else {
            return getRootBuildSelector().getSelection(projectPath, root, path);
        }
    }

    private BuildState findIncludedBuild(TaskPath taskPath) {
        if (buildStateRegistry.getIncludedBuilds().isEmpty() || !taskPath.hasBuildName()) {
            return null;
        }

        Map<String, BuildState> builds = buildStateRegistry.getIncludedBuilds().stream().collect(Collectors.toMap(IncludedBuildState::getName, Function.identity()));
        NameMatcher matcher = new NameMatcher();
        return matcher.find(taskPath.getBuildName(), builds);
    }

    private TaskSelector getSelector(BuildState buildState) {
        return new DefaultTaskSelector(buildState.getBuild(), taskNameResolver, projectConfigurer);
    }

    private TaskSelector getRootBuildSelector() {
        return getSelector(buildStateRegistry.getRootBuild());
    }

    private static class TaskPath {
        private final String[] parts;

        private TaskPath(String[] parts) {
            this.parts = parts;
        }

        boolean hasBuildName() {
            return parts.length > 1;
        }

        String getBuildName() {
            return parts[0];
        }

        String dropBuildName() {
            return String.join(Project.PATH_SEPARATOR, Arrays.copyOfRange(parts, 1, parts.length));
        }

        static TaskPath from(String path) {
            if (path.startsWith(Project.PATH_SEPARATOR)) {
                return new TaskPath(path.substring(Project.PATH_SEPARATOR.length()).split(Project.PATH_SEPARATOR));
            } else {
                return new TaskPath(path.split(Project.PATH_SEPARATOR));
            }
        }
    }
}
