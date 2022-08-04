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

import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.util.Path;

import javax.annotation.Nullable;
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
    public Spec<Task> getFilter(String path) {
        Path taskPath = Path.path(path);
        if (taskPath.isAbsolute()) {
            BuildState build = findIncludedBuild(taskPath);
            // Exclusion was for an included build, use it
            if (build != null) {
                return getSelectorForChildBuild(build).getFilter(taskPath.removeFirstSegments(1).toString());
            }
        }
        // Exclusion didn't match an included build, so it might be a subproject of the root build or a relative path
        if (gradle.isRootBuild()) {
            return getUnqualifiedBuildSelector().getFilter(path);
        } else {
            // Included build ignores this exclusion since it doesn't apply directly to it
            return Specs.satisfyAll();
        }
    }

    @Override
    public TaskSelection getSelection(String path) {
        if (gradle.isRootBuild()) {
            Path taskPath = Path.path(path);
            if (taskPath.isAbsolute()) {
                BuildState build = findIncludedBuild(taskPath);
                if (build != null) {
                    return getSelectorForChildBuild(build).getSelection(taskPath.removeFirstSegments(1).toString());
                }
            }
        }

        return getUnqualifiedBuildSelector().getSelection(path);
    }

    @Override
    public TaskSelection getSelection(@Nullable String projectPath, @Nullable File root, String path) {
        if (gradle.isRootBuild()) {
            Path taskPath = Path.path(path);
            if (taskPath.isAbsolute()) {
                BuildState build = findIncludedBuild(taskPath);
                if (build != null) {
                    return getSelectorForChildBuild(build).getSelection(projectPath, root, taskPath.removeFirstSegments(1).toString());
                }
                build = findIncludedBuild(root);
                if (build != null) {
                    return getSelectorForChildBuild(build).getSelection(projectPath, root, path);
                }
            }
        }

        return getUnqualifiedBuildSelector().getSelection(projectPath, root, path);
    }

    @Nullable
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

    @Nullable
    private BuildState findIncludedBuild(@Nullable File root) {
        if (root == null) {
            return null;
        }

        for (IncludedBuildState build : buildStateRegistry.getIncludedBuilds()) {
            if (build.getRootDirectory().equals(root)) {
                return build;
            }
        }

        return null;
    }


    private TaskSelector getSelectorForChildBuild(BuildState buildState) {
        buildState.ensureProjectsConfigured();
        return getSelector(buildState);
    }

    private TaskSelector getSelector(BuildState buildState) {
        return new DefaultTaskSelector(buildState.getMutableModel(), taskNameResolver, projectConfigurer);
    }

    private TaskSelector getUnqualifiedBuildSelector() {
        return getSelector(gradle.getOwner());
    }
}
