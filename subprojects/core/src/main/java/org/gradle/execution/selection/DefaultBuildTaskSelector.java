/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.selection;

import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.execution.TaskSelection;
import org.gradle.execution.TaskSelectionException;
import org.gradle.execution.TaskSelector;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultBuildTaskSelector implements BuildTaskSelector {
    private final BuildStateRegistry buildRegistry;

    public DefaultBuildTaskSelector(BuildStateRegistry buildRegistry) {
        this.buildRegistry = buildRegistry;
    }

    @Override
    public Filter resolveExcludedTaskName(String taskName, BuildState defaultBuild) {
        Path taskPath = Path.path(taskName);
        if (taskPath.isAbsolute() && taskPath.segmentCount() > 1) {
            BuildState build = findIncludedBuild(taskPath);
            // Exclusion was for an included build, use it
            if (build != null) {
                return new Filter(build, new LazyFilter(build, taskPath.removeFirstSegments(1)));
            }
        }
        // Exclusion didn't match an included build, so it might be a subproject of the root build or a relative path
        return new Filter(defaultBuild, new LazyFilter(defaultBuild, taskPath));
    }

    @Override
    public TaskSelection resolveTaskName(@Nullable File rootDir, @Nullable String projectPath, BuildState defaultBuild, String taskName) {
        if (rootDir != null) {
            RootBuildState rootBuild = buildRegistry.getRootBuild();
            if (rootDir.equals(rootBuild.getBuildRootDir())) {
                return getSelectorForBuild(rootBuild).getSelection(projectPath, taskName);
            }
            BuildState build = findIncludedBuild(rootDir);
            if (build != null) {
                return getSelectorForBuild(build).getSelection(projectPath, taskName);
            }
            throw new TaskSelectionException(String.format("Could not find included build with root directory '%s'.", rootDir));
        }

        Path taskPath = Path.path(taskName);
        if (taskPath.isAbsolute() && taskPath.segmentCount() > 1) {
            BuildState build = findIncludedBuild(taskPath);
            if (build != null) {
                return getSelectorForBuild(build).getSelection(projectPath, taskPath.removeFirstSegments(1).getPath());
            }
        }
        return getSelectorForBuild(defaultBuild).getSelection(projectPath, taskName);
    }

    @Override
    public BuildSpecificSelector relativeToBuild(BuildState target) {
        return taskName -> DefaultBuildTaskSelector.this.resolveTaskName(null, null, target, taskName);
    }

    private BuildState findIncludedBuild(File rootDir) {
        for (IncludedBuildState build : buildRegistry.getIncludedBuilds()) {
            if (build.getRootDirectory().equals(rootDir)) {
                return build;
            }
        }
        return null;
    }

    @Nullable
    private BuildState findIncludedBuild(Path taskPath) {
        String buildName = taskPath.segment(0);
        for (IncludedBuildState build : buildRegistry.getIncludedBuilds()) {
            if (build.getName().equals(buildName)) {
                return build;
            }
        }

        return null;
    }

    private static TaskSelector getSelectorForBuild(BuildState target) {
        if (!(target instanceof RootBuildState)) {
            target.ensureProjectsConfigured();
        }
        return target.getMutableModel().getServices().get(TaskSelector.class);
    }

    private static class LazyFilter implements Spec<Task> {
        private final Path path;
        private final BuildState build;
        private Spec<Task> spec;

        public LazyFilter(BuildState build, Path path) {
            this.build = build;
            this.path = path;
        }

        @Override
        public boolean isSatisfiedBy(Task element) {
            if (spec == null) {
                spec = getSelectorForBuild(build).getFilter(path.getPath());
            }
            return spec.isSatisfiedBy(element);
        }
    }
}
