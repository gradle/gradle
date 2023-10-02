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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;
import org.gradle.api.specs.Spec;
import org.gradle.util.internal.NameMatcher;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DefaultTaskSelector implements TaskSelector {
    private static final Logger LOGGER = Logging.getLogger(DefaultTaskSelector.class);

    private final TaskNameResolver taskNameResolver;
    private final ProjectConfigurer configurer;

    @Inject
    public DefaultTaskSelector(TaskNameResolver taskNameResolver, ProjectConfigurer configurer) {
        this.taskNameResolver = taskNameResolver;
        this.configurer = configurer;
    }

    @Inject
    protected Problems getProblemService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Spec<Task> getFilter(SelectionContext context, ProjectState project, String taskName, boolean includeSubprojects) {
        if (includeSubprojects) {
            // Try to delay configuring all the subprojects
            configurer.configure(project.getMutableModel());
            if (taskNameResolver.tryFindUnqualifiedTaskCheaply(taskName, project.getMutableModel())) {
                // An exact match in the target project - can just filter tasks by path to avoid configuring subprojects at this point
                return new TaskPathSpec(project.getMutableModel(), taskName);
            }
        }

        Set<Task> selectedTasks = getSelection(context, project, taskName, includeSubprojects).getTasks();
        return element -> !selectedTasks.contains(element);
    }

    @Override
    public TaskSelection getSelection(SelectionContext context, ProjectState targetProject, String taskName, boolean includeSubprojects) {
        if (!includeSubprojects) {
            configurer.configure(targetProject.getMutableModel());
        } else {
            configurer.configureHierarchy(targetProject.getMutableModel());
        }

        TaskSelectionResult tasks = taskNameResolver.selectWithName(taskName, targetProject.getMutableModel(), includeSubprojects);
        if (tasks != null) {
            LOGGER.info("Task name matched '{}'", taskName);
            return new TaskSelection(targetProject.getProjectPath().getPath(), taskName, tasks);
        }

        Map<String, TaskSelectionResult> tasksByName = taskNameResolver.selectAll(targetProject.getMutableModel(), includeSubprojects);
        NameMatcher matcher = new NameMatcher();
        String actualName = matcher.find(taskName, tasksByName.keySet());

        if (actualName == null) {
            throw throwTaskSelectionException(context, targetProject, taskName, includeSubprojects, matcher);
        }
        LOGGER.info("Abbreviated task name '{}' matched '{}'", taskName, actualName);
        return new TaskSelection(targetProject.getProjectPath().getPath(), taskName, tasksByName.get(actualName));
    }

    private RuntimeException throwTaskSelectionException(SelectionContext context, ProjectState targetProject, String taskName, boolean includeSubprojects, NameMatcher matcher) {
        String searchContext = getSearchContext(targetProject, includeSubprojects);

        if (context.getOriginalPath().getPath().equals(taskName)) {
            throw new TaskSelectionException(matcher.formatErrorMessage("Task", searchContext));
        }
        String message = String.format("Cannot locate %s that match '%s' as %s", context.getType(), context.getOriginalPath(),
            matcher.formatErrorMessage("task", searchContext));

        throw getProblemService().throwing(builder -> builder
            .label(message)
            .undocumented()
            .location(Objects.requireNonNull(context.getOriginalPath().getName()), -1)
            .category("task_selection")
            .severity(Severity.ERROR)
            .withException(new TaskSelectionException(message)) // this instead of cause
        );
    }

    @Nonnull
    private static String getSearchContext(ProjectState targetProject, boolean includeSubprojects) {
        if (includeSubprojects && !targetProject.getChildProjects().isEmpty()) {
            return targetProject.getDisplayName() + " and its subprojects";
        }
        return targetProject.getDisplayName().getDisplayName();
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
