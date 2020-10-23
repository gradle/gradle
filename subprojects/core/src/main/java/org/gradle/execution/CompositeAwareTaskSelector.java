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
        String[] parts = path.split(Project.PATH_SEPARATOR);
        if (parts.length > 1) {
            String buildName = path.startsWith(Project.PATH_SEPARATOR) ? parts[1] : parts[0];
            BuildState build = findIncludedBuild(buildName);
            if (build != null) {
                return getSelector(build).getFilter(dropBuildName(buildName, parts));
            }
        }
        return getRootBuildSelector().getFilter(path);
    }

    @Override
    public TaskSelection getSelection(String path) {

        String[] parts = path.split(Project.PATH_SEPARATOR);
        if (parts.length > 1) {
            String buildName = path.startsWith(Project.PATH_SEPARATOR) ? parts[1] : parts[0];
            BuildState build = findIncludedBuild(buildName);
            if (build != null) {
                return getSelector(build).getSelection(dropBuildName(buildName, parts));
            }
        }

        return getRootBuildSelector().getSelection(path);
    }

    @Override
    public TaskSelection getSelection(String projectPath, File root, String path) {
        String[] parts = path.split(Project.PATH_SEPARATOR);
        if (parts.length > 1) {
            String buildName = path.startsWith(Project.PATH_SEPARATOR) ? parts[1] : parts[0];
            BuildState build = findIncludedBuild(buildName);
            if (build != null) {
                return getSelector(build).getSelection(projectPath, root, dropBuildName(buildName, parts));
            }
        }

        return getRootBuildSelector().getSelection(projectPath, root, path);
    }

    private BuildState findIncludedBuild(String name) {
        if (buildStateRegistry.getIncludedBuilds().isEmpty()) {
            return null;
        }

        Map<String, BuildState> builds = buildStateRegistry.getIncludedBuilds().stream().collect(Collectors.toMap(IncludedBuildState::getName, Function.identity()));
        NameMatcher matcher = new NameMatcher();
        return matcher.find(name, builds);
    }

    private TaskSelector getSelector(BuildState buildState) {
        return new DefaultTaskSelector(buildState.getBuild(), taskNameResolver, projectConfigurer);
    }

    private TaskSelector getRootBuildSelector() {
        return getSelector(buildStateRegistry.getRootBuild());
    }

    private String dropBuildName(String buildName, String[] parts) {
        StringBuilder sb = new StringBuilder();
        int start = 0;
        for (String part : parts) {
            start++;
            if (buildName.equals(part)) {
                break;
            }
        }
        for (int i=start; i<parts.length; i++) {
            sb.append(Project.PATH_SEPARATOR);
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
