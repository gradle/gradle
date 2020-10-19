/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskReference;
import org.gradle.execution.taskpath.ResolvedTaskPath;
import org.gradle.execution.taskpath.TaskPathResolver;
import org.gradle.util.NameMatcher;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class TaskSelector {
    private final TaskNameResolver taskNameResolver;
    private final GradleInternal gradle;
    private final ProjectConfigurer configurer;
    private final TaskPathResolver taskPathResolver = new TaskPathResolver();

    public TaskSelector(GradleInternal gradle, ProjectConfigurer projectConfigurer) {
        this(gradle, new TaskNameResolver(), projectConfigurer);
    }

    public TaskSelector(GradleInternal gradle, TaskNameResolver taskNameResolver, ProjectConfigurer configurer) {
        this.taskNameResolver = taskNameResolver;
        this.gradle = gradle;
        this.configurer = configurer;
    }

    public TaskSelection getSelection(String path) {
        return getSelection(path, gradle.getDefaultProject());
    }

    public Spec<Task> getFilter(String path) {
        String[] paths = parsePath(path);
        final String projectName = paths[0];
        final String taskPath2 = paths[1];
        if (paths != null) {
            for (final IncludedBuild build : gradle.getDefaultProject().getGradle().getIncludedBuilds()) {
                if (build.getName().equals(projectName)) {

                    return new Spec<Task>() {
                        @Override
                        public boolean isSatisfiedBy(Task element) {
                            System.out.println("!!! taskPath=" + taskPath2);
                            System.out.println("!!! element.getProject().getPath()=" + element.getProject().getPath());
                            System.out.println("!!! element.getPath()=" + element.getPath());
                            return false;
                        }
                    };
                }
            }
        }


        final ResolvedTaskPath taskPath = taskPathResolver.resolvePath(path, gradle.getDefaultProject());
        if (!taskPath.isQualified()) {
            ProjectInternal targetProject = taskPath.getProject();
            configurer.configure(targetProject);
            if (taskNameResolver.tryFindUnqualifiedTaskCheaply(taskPath.getTaskName(), taskPath.getProject())) {
                // An exact match in the target project - can just filter tasks by path to avoid configuring sub-projects at this point
                return new TaskPathSpec(targetProject, taskPath.getTaskName());
            }
        }

        final Set<Task> selectedTasks = getSelection(path, gradle.getDefaultProject()).getTasks();
        return new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return !selectedTasks.contains(element);
            }
        };
    }

    public TaskSelection getSelection(@Nullable String projectPath, @Nullable File root, String path) {
        if (root != null) {
            ensureNotFromIncludedBuild(root);
        }
        ProjectInternal project = projectPath != null
            ? gradle.getRootProject().findProject(projectPath)
            : gradle.getDefaultProject();
        return getSelection(path, project);
    }

    private void ensureNotFromIncludedBuild(File root) {
        Set<File> includedRoots = new HashSet<File>();
        for (IncludedBuild includedBuild : gradle.getIncludedBuilds()) {
            includedRoots.add(includedBuild.getProjectDir());
        }
        if (includedRoots.contains(root)) {
            throw new TaskSelectionException("Can't launch tasks from included builds");
        }
    }

    private TaskSelection getSelection(String path, ProjectInternal project) {
        String[] paths = parsePath(path);
        if (paths != null) {
            String includedBuildName = paths[0];
            String taskInIncludedBuildPath = paths[1];

            for (final IncludedBuild build : project.getGradle().getIncludedBuilds()) {
                if (build.getName().equals(includedBuildName)) {
                    return new TaskSelection(includedBuildName, taskInIncludedBuildPath, build.task(taskInIncludedBuildPath));
                }
            }
        }

        ResolvedTaskPath taskPath = taskPathResolver.resolvePath(path, project);
        ProjectInternal targetProject = taskPath.getProject();
        if (taskPath.isQualified()) {
            configurer.configure(targetProject);
        } else {
            configurer.configureHierarchy(targetProject);
        }

        TaskSelectionResult tasks = taskNameResolver.selectWithName(taskPath.getTaskName(), taskPath.getProject(), !taskPath.isQualified());
        if (tasks != null) {
            // An exact match
            return new TaskSelection(taskPath.getProject().getPath(), path, tasks);
        }

        Map<String, TaskSelectionResult> tasksByName = taskNameResolver.selectAll(taskPath.getProject(), !taskPath.isQualified());
        NameMatcher matcher = new NameMatcher();
        String actualName = matcher.find(taskPath.getTaskName(), tasksByName.keySet());
        if (actualName != null) {
            return new TaskSelection(taskPath.getProject().getPath(), taskPath.getPrefix() + actualName, tasksByName.get(actualName));
        }

        throw new TaskSelectionException(matcher.formatErrorMessage("task", taskPath.getProject()));
    }

    private String[] parsePath(String path) {
        if (path.startsWith(Project.PATH_SEPARATOR)) {
            int idx = path.indexOf(Project.PATH_SEPARATOR, 1);

            if (idx >= 0) {
                String projectName = path.substring(1, idx);
                String taskPath = path.substring(idx);
                return new String[]{ projectName, taskPath };
            }
        }
        return null;
    }

    public static class TaskSelection {
        private final String projectPath;
        private final String taskName;
        private final TaskSelectionResult taskSelectionResult;
        private final TaskReference taskReference;

        public TaskSelection(String projectPath, String taskName, TaskSelectionResult tasks) {
            this.projectPath = projectPath;
            this.taskName = taskName;
            this.taskSelectionResult = tasks;
            this.taskReference = null;
        }

        public TaskSelection(String projectPath, String taskName, TaskReference taskReference) {
            this.projectPath = projectPath;
            this.taskName = taskName;
            this.taskSelectionResult = null;
            this.taskReference = taskReference;
        }

        public String getProjectPath() {
            return projectPath;
        }

        public String getTaskName() {
            return taskName;
        }

        public Set<Task> getTasks() {
            if (taskReference == null) {
                LinkedHashSet<Task> result = new LinkedHashSet<Task>();
                taskSelectionResult.collectTasks(result);
                return result;
            } else {
                return Collections.singleton(taskReference.resolveTask());
            }
        }
    }

    private static class IncludedBuildTaskPathSpec implements Spec<Task> {

        private final String buildName;
        private final String taskPath;

        IncludedBuildTaskPathSpec(String buildName, String taskPath) {
            this.buildName = buildName;
            this.taskPath = taskPath;
        }

        @Override
        public boolean isSatisfiedBy(Task task) {
            return false;
        }
    }

    private static class TaskPathSpec implements Spec<Task> {
        private final ProjectInternal targetProject;
        private final String taskName;

        public TaskPathSpec(ProjectInternal targetProject, String taskName) {
            this.targetProject = targetProject;
            this.taskName = taskName;
        }

        @Override
        public boolean isSatisfiedBy(Task element) {
            if (!element.getName().equals(taskName)) {
                return true;
            }
            for (Project current = element.getProject(); current != null; current = current.getParent()) {
                if (current.equals(targetProject)) {
                    return false;
                }
            }
            return true;
        }
    }
}
