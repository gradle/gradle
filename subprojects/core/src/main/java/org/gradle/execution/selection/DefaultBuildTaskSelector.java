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
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GeneralDataSpec;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblemSpec;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.configuration.project.BuiltInCommand;
import org.gradle.execution.ProjectSelectionException;
import org.gradle.execution.TaskSelection;
import org.gradle.execution.TaskSelectionException;
import org.gradle.execution.TaskSelector;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.util.Path;
import org.gradle.util.internal.NameMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class DefaultBuildTaskSelector implements BuildTaskSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildTaskSelector.class);
    private final BuildStateRegistry buildRegistry;
    private final TaskSelector taskSelector;
    private final List<BuiltInCommand> commands;
    private final InternalProblems problemsService;

    @Inject
    public DefaultBuildTaskSelector(BuildStateRegistry buildRegistry, TaskSelector taskSelector, List<BuiltInCommand> commands, InternalProblems problemsService) {
        this.buildRegistry = buildRegistry;
        this.taskSelector = taskSelector;
        this.commands = commands;
        this.problemsService = problemsService;
    }

    @Override
    public Filter resolveExcludedTaskName(BuildState defaultBuild, String taskName) {
        if (!defaultBuild.isProjectsCreated()) {
            // Too early to resolve excludes
            return new Filter(defaultBuild, Specs.satisfyNone());
        }
        TaskSelector.SelectionContext selection = sanityCheckPath(taskName, "excluded tasks");
        ProjectResolutionResult resolutionResult = resolveProject(selection, selection.getOriginalPath(), defaultBuild);
        return new Filter(resolutionResult.build, new LazyFilter(selection, resolutionResult));
    }

    @Override
    public TaskSelection resolveTaskName(@Nullable File rootDir, @Nullable String projectPath, BuildState targetBuild, String taskName) {
        TaskSelector.SelectionContext selection = sanityCheckPath(taskName, "tasks");

        BuildState defaultBuild = targetBuild;
        if (rootDir != null) {
            // When a root dir is specified, the project path and task name have come from a `Launchable` request from the tooling API client
            // Use exact lookup rather than pattern matching
            RootBuildState rootBuild = buildRegistry.getRootBuild();
            if (rootDir.equals(rootBuild.getBuildRootDir())) {
                defaultBuild = rootBuild;
            } else {
                BuildState build = findIncludedBuild(rootDir);
                if (build == null) {
                    throw new TaskSelectionException(String.format("Could not find included build with root directory '%s'.", rootDir));
                }
                defaultBuild = build;
            }
            if (projectPath != null) {
                ProjectState project = defaultBuild.getProjects().getProject(Path.path(projectPath));
                return getSelectorForBuild(defaultBuild).getSelection(selection, project, selection.getOriginalPath().getName(), true);
            } else {
                Path projectPrefix = selection.getOriginalPath().getParent();
                ProjectState project = defaultBuild.getProjects().getProject(projectPrefix);
                return getSelectorForBuild(defaultBuild).getSelection(selection, project, selection.getOriginalPath().getName(), false);
            }
        }

        ProjectResolutionResult resolutionResult = resolveProject(selection, selection.getOriginalPath(), defaultBuild);
        return getSelectorForBuild(resolutionResult.build).getSelection(selection, resolutionResult.project, resolutionResult.taskName, resolutionResult.includeSubprojects);
    }

    @Override
    public BuildSpecificSelector relativeToBuild(BuildState target) {
        return taskName -> DefaultBuildTaskSelector.this.resolveTaskName(null, null, target, taskName);
    }

    private ProjectResolutionResult resolveProject(TaskSelector.SelectionContext context, Path path, BuildState targetBuild) {
        // Just a name -> use default project + select tasks with matching name in default project and subprojects
        if (!path.isAbsolute() && path.segmentCount() == 1) {
            return new ProjectResolutionResult(targetBuild, targetBuild.getMutableModel().getDefaultProject().getOwner(), true, path.getName());
        }

        // <path>:name -> resolve <path> to a project + select task with matching name in that project
        // when <path> is absolute -> resolve <path> relative to root project
        // when <path> is relative -> resolve <path> relative to default project

        Path projectPath = path.getParent();
        ProjectState matchingProject;
        if (projectPath.isAbsolute()) {
            matchingProject = buildRegistry.getRootBuild().getProjects().getRootProject();
        } else {
            matchingProject = targetBuild.getMutableModel().getDefaultProject().getOwner();
        }
        while (projectPath.segmentCount() > 0) {
            String next = projectPath.segment(0);
            matchingProject = selectProject(context, matchingProject, next);
            if (projectPath.segmentCount() == 1) {
                projectPath = Path.ROOT;
            } else {
                projectPath = projectPath.removeFirstSegments(1);
            }
        }
        LOGGER.info("Task path '{}' matched project '{}'", context.getOriginalPath(), matchingProject.getIdentityPath());
        return new ProjectResolutionResult(matchingProject.getOwner(), matchingProject, false, path.getName());
    }

    private ProjectState selectProject(TaskSelector.SelectionContext context, ProjectState project, String childName) {
        Map<String, ProjectState> candidates = new LinkedHashMap<>();
        if (project.getProjectPath().equals(Path.ROOT)) {
            // Project is the root of a build, so include the root projects of any builds nested under that build
            buildRegistry.visitBuilds(build -> {
                if (build.isImportableBuild() && build.isProjectsLoaded()) {
                    ProjectState rootProject = build.getProjects().getRootProject();
                    Path rootProjectIdentityPath = rootProject.getIdentityPath();
                    Path buildIdentityPath = rootProjectIdentityPath.getParent();
                    if (Objects.equals(buildIdentityPath, project.getIdentityPath())) {
                        candidates.put(rootProjectIdentityPath.getName(), rootProject);
                    }
                }
            });
        }
        for (ProjectState child : project.getChildProjects()) {
            ProjectState previous = candidates.put(child.getIdentityPath().getName(), child);
            if (previous != null) {
                throw new IllegalStateException("Duplicate child project names for " + project.getDisplayName());
            }
        }
        ProjectState child = candidates.get(childName);
        if (child != null) {
            return child;
        }
        NameMatcher nameMatcher = new NameMatcher();
        child = nameMatcher.find(childName, candidates);
        if (child != null) {
            return child;
        }

        throw problemsService.getInternalReporter().throwing(spec -> {
            nameMatcher.configureProblemId(spec);
            String message = String.format("Cannot locate %s that match '%s' as %s", context.getType(), context.getOriginalPath(), nameMatcher.formatErrorMessage("project", project.getDisplayName()));
            configureProblem(spec, message, context.getOriginalPath().getPath(), new ProjectSelectionException(message));
        });
    }

    private static void configureProblem(ProblemSpec spec, String message, String requestedPath, RuntimeException e) {
        spec.contextualLabel(message);
        spec.severity(Severity.ERROR);
        ((InternalProblemSpec) spec).additionalData(GeneralDataSpec.class, data -> data.put("requestedPath", Objects.requireNonNull(requestedPath)));
        spec.withException(e);
    }

    private TaskSelector.SelectionContext sanityCheckPath(String name, String type) {
        // Don't allow paths that are:
        // - empty or blank
        // - the root path
        // - have empty or blank segments (eg `::a`, `a::b`, `a:  :b`, etc)

        if (name.isEmpty() || StringUtils.isBlank(name)) {
            throw problemsService.getInternalReporter().throwing(spec -> {
                spec.id("empty-path", "Empty path", GradleCoreProblemGroup.taskSelection());
                String message = String.format("Cannot locate matching %s for an empty path. The path should include a task name (for example %s).", type, examplePaths());
                configureProblem(spec, message, name, new TaskSelectionException(message));
            });
        }
        Path path = Path.path(name);
        Pattern root = Pattern.compile("\\s*:(\\s*:)*\\s*");
        if (root.matcher(name).matches()) {
            throw problemsService.getInternalReporter().throwing(spec -> {
                spec.id("missing-task-name", "Missing task name", GradleCoreProblemGroup.taskSelection());
                String message = String.format("Cannot locate %s that match '%s'. The path should include a task name (for example %s).", type, name, examplePaths());
                configureProblem(spec, message, name, new TaskSelectionException(message));
            });
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

            throw problemsService.getInternalReporter().throwing(spec -> {
                spec.id("empty-segments", "Empty segments", GradleCoreProblemGroup.taskSelection());
                String message = String.format("Cannot locate %s that match '%s'. The path should not include an empty segment (try '%s' instead).", type, name, normalized);
                configureProblem(spec, message, name, new TaskSelectionException(message));
            });
        }
        return new TaskSelector.SelectionContext(path, type);
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

    private TaskSelector getSelectorForBuild(BuildState target) {
        if (!(target instanceof RootBuildState)) {
            target.ensureProjectsConfigured();
        }
        return taskSelector;
    }

    private static class ProjectResolutionResult {
        final BuildState build;
        final ProjectState project;
        final boolean includeSubprojects;
        final String taskName;

        public ProjectResolutionResult(BuildState build, ProjectState project, boolean includeSubprojects, String taskName) {
            this.build = build;
            this.project = project;
            this.includeSubprojects = includeSubprojects;
            this.taskName = taskName;
        }
    }

    private class LazyFilter implements Spec<Task> {
        private final TaskSelector.SelectionContext selection;
        private final ProjectResolutionResult resolutionResult;
        private Spec<Task> spec;

        public LazyFilter(TaskSelector.SelectionContext selection, ProjectResolutionResult resolutionResult) {
            this.selection = selection;
            this.resolutionResult = resolutionResult;
        }

        @Override
        public boolean isSatisfiedBy(Task element) {
            if (spec == null) {
                spec = getSelectorForBuild(resolutionResult.build).getFilter(selection, resolutionResult.project, resolutionResult.taskName, resolutionResult.includeSubprojects);
            }
            return spec.isSatisfiedBy(element);
        }
    }
}
