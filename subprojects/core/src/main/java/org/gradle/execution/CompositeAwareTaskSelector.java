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

import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.util.Path;

import java.io.File;

public class CompositeAwareTaskSelector extends TaskSelector {
    private final GradleInternal gradle;
    private final BuildStateRegistry buildStateRegistry;
    private final ProjectConfigurer projectConfigurer;
    private final TaskNameResolver taskNameResolver;

    public CompositeAwareTaskSelector(GradleInternal gradle, BuildStateRegistry buildStateRegistry, ProjectConfigurer projectConfigurer, TaskNameResolver taskNameResolver) {
        this.gradle = gradle;
        this.buildStateRegistry = buildStateRegistry;
        this.projectConfigurer = projectConfigurer;
        this.taskNameResolver = taskNameResolver;
    }

    @Override
    public TaskFilter getFilter(String path) {
        Path taskPath = Path.path(path);
        BuildState build = findIncludedBuild(taskPath);
        if (build != null) {
            return getSelector(build).getFilter(taskPath.removeFirstSegments(1).toString());
        } else {
            return getUnqualifiedBuildSelector().getFilter(path);
        }
    }

    @Override
    public TaskSelection getSelection(String path) {
        if (gradle.isRootBuild()) {
            Path taskPath = Path.path(path);
            if (taskPath.isAbsolute()) {
                BuildState build = findIncludedBuild(taskPath);
                if (build != null) {
                    return getSelector(build).getSelection(taskPath.removeFirstSegments(1).toString());
                }
            }
        }

        return getUnqualifiedBuildSelector().getSelection(path);
    }

    @Override
    public TaskSelection getSelection(String projectPath, File root, String path) {
        if (gradle.isRootBuild()) {
            Path taskPath = Path.path(path);
            if (taskPath.isAbsolute()) {
                BuildState build = findIncludedBuild(taskPath);
                if (build != null) {
                    return getSelector(build).getSelection(projectPath, root, taskPath.removeFirstSegments(1).toString());
                }
            }
        }

        return getUnqualifiedBuildSelector().getSelection(projectPath, root, path);
    }

    private BuildState findIncludedBuild(Path taskPath) {
        if (buildStateRegistry.getIncludedBuilds().isEmpty() || taskPath.segmentCount() <= 1) {
            return null;
        }

        String buildName = taskPath.segment(0);
        for (IncludedBuildState build : buildStateRegistry.getIncludedBuilds()) {
            if (build.getName().equals(buildName)) {
                return build;
            }
        }

        return null;
    }

    private TaskSelector getSelector(BuildState buildState) {
        return new DefaultTaskSelector(buildState.getBuild(), taskNameResolver, projectConfigurer);
    }

    private TaskSelector getUnqualifiedBuildSelector() {
        return getSelector(gradle.getOwner());
    }
}
