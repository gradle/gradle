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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.configuration.project.BuiltInCommand;
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
import java.util.List;
import java.util.regex.Pattern;

public class DefaultBuildTaskSelector implements BuildTaskSelector {
    private final BuildStateRegistry buildRegistry;
    private final List<BuiltInCommand> commands;

    public DefaultBuildTaskSelector(BuildStateRegistry buildRegistry, List<BuiltInCommand> commands) {
        this.buildRegistry = buildRegistry;
        this.commands = commands;
    }

    @Override
    public Filter resolveExcludedTaskName(BuildState defaultBuild, String taskName) {
        Path taskPath = sanityCheckPath(taskName, "excluded tasks");

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
        Path taskPath = sanityCheckPath(taskName, "tasks");

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

    private Path sanityCheckPath(String name, String type) {
        // Don't allow paths that are:
        // - empty or blank
        // - the root path
        // - have empty segments (eg ::a, a::b, etc)

        if (name.isEmpty() || StringUtils.isBlank(name)) {
            throw new TaskSelectionException(String.format("Cannot locate matching %s for an empty path. The path should include a task name (for example %s).", type, examplePaths()));
        }
        Path path = Path.path(name);
        Pattern root = Pattern.compile("\\s*:(\\s*:)*\\s*");
        if (root.matcher(name).matches()) {
            throw new TaskSelectionException(String.format("Cannot locate matching %s for path '%s'. The path should include a task name (for example %s).", type, name, examplePaths()));
        }
        Pattern emptySegment = Pattern.compile("(:\\s*:)|(^\\s+:)|(:\\s*$)");
        if (emptySegment.matcher(name).find()) {
            Pattern emptyFirstSegment = Pattern.compile("^\\s*:");
            boolean isAbsolute = emptyFirstSegment.matcher(name).find();
            StringBuilder normalized = new StringBuilder();
            for (int i = 0; i < path.segmentCount(); i++) {
                if (!StringUtils.isBlank(path.segment(i))) {
                    if (isAbsolute || normalized.length() > 0) {
                        normalized.append(":");
                    }
                    normalized.append(path.segment(i));
                }
            }
            throw new TaskSelectionException(String.format("Cannot locate matching %s for path '%s'. The path should not include an empty segment (try '%s' instead).", type, name, normalized));
        }
        return path;
    }

    private String examplePaths() {
        for (BuiltInCommand command : commands) {
            if (command.asDefaultTask().isEmpty()) {
                continue;
            }
            String task = command.asDefaultTask().get(0);
            return String.format("':%s' or '%s'", task, task);
        }
        throw new IllegalStateException("No built-in tasks available.");
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
