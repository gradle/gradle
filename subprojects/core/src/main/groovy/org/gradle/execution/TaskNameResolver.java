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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.api.Project;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.tasks.TaskContainerInternal.TASK_MODEL_TYPE;

public class TaskNameResolver {

    /**
     * Non-exhaustively searches for at least one task with the given name, by not evaluating projects before searching.
     */
    public boolean tryFindUnqualifiedTaskCheaply(String name, ProjectInternal project) {
        // don't evaluate children, see if we know it's without validating it
        for (Project project1 : project.getAllprojects()) {
            if (project1.getTasks().getNames().contains(name)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Finds tasks that will have exactly the given name, without necessarily creating or configuring the tasks. Returns null if no such match found.
     */
    @Nullable
    public TaskSelectionResult selectWithName(final String taskName, final ProjectInternal project, boolean includeSubProjects) {
        if (includeSubProjects) {
            Set<Task> tasks = Sets.newLinkedHashSet();
            new MultiProjectTaskSelectionResult(taskName, project).collectTasks(tasks);
            if (!tasks.isEmpty()) {
                return new FixedTaskSelectionResult(tasks);
            }
        } else {
            if (hasTask(taskName, project)) {
                return new TaskSelectionResult() {
                    @Override
                    public void collectTasks(Collection<? super Task> tasks) {
                        tasks.add(getExistingTask(project, taskName));
                    }
                };
            }
        }

        return null;
    }

    /**
     * Finds the names of all tasks, without necessarily creating or configuring the tasks. Returns an empty map when none are found.
     */
    public Map<String, TaskSelectionResult> selectAll(ProjectInternal project, boolean includeSubProjects) {
        Map<String, TaskSelectionResult> selected = Maps.newLinkedHashMap();

        if (includeSubProjects) {
            Set<String> taskNames = Sets.newLinkedHashSet();
            collectTaskNames(project, taskNames);
            for (String taskName : taskNames) {
                selected.put(taskName, new MultiProjectTaskSelectionResult(taskName, project));
            }
        } else {
            for (String taskName : getTaskNames(project)) {
                selected.put(taskName, new SingleProjectTaskSelectionResult(taskName, project.getTasks()));
            }
        }

        return selected;
    }

    private static ModelNode selfClose(ModelRegistry modelRegistry, ModelPath modelPath) {
        ModelNode modelNode = modelRegistry.atStateOrLater(modelPath, ModelNode.State.SelfClosed);
        if (modelNode == null) {
            throw new IllegalStateException("Did not find " + modelPath + " in project model registry");
        }

        return modelNode;
    }

    private static ModelNode selfClosedTasksNode(ProjectInternal project) {
        ModelRegistry modelRegistry = project.getModelRegistry();
        ModelNode modelNode;
        try {
            project.fireDeferredConfiguration();
            modelNode = selfClose(modelRegistry, TaskContainerInternal.MODEL_PATH);
        } catch (Throwable e) {
            throw new ProjectConfigurationException(String.format("A problem occurred configuring %s.", project), e);
        }
        return modelNode;
    }

    private static Set<String> getTaskNames(ProjectInternal project) {
        return selfClosedTasksNode(project).getLinkNames(TASK_MODEL_TYPE);
    }

    private static boolean hasTask(String taskName, ProjectInternal project) {
        // ask registry first as tasks.findByName will fully realise the task if it is defined through the registry
        // i.e. avoid configuring tasks defined through the registry at this point, because we just need to know if it exists
        return selfClosedTasksNode(project).hasLink(taskName, TASK_MODEL_TYPE) || project.getTasks().findByName(taskName) != null; // look at task container to trigger rules / placeholders
    }

    private static TaskInternal getExistingTask(ProjectInternal project, String taskName) {
        ModelRegistry modelRegistry = project.getModelRegistry();
        ModelPath path = TaskContainerInternal.MODEL_PATH.child(taskName);
        try {
            if (modelRegistry.node(path) == null) {
                // The tasks exists but isn't in the model registry, was created after the registry was closed
                // This can happen for a few reasons, use of task container rules being a common one
                return (TaskInternal) project.getTasks().getByName(taskName);
            } else {
                // pull through the registry, even for tasks defined via legacy DSL, to ensure all configuration rules get applied
                return (TaskInternal) modelRegistry.realize(path, TASK_MODEL_TYPE);
            }
        } catch (Throwable e) {
            throw new ProjectConfigurationException(String.format("A problem occurred configuring %s.", project), e);
        }
    }

    private void collectTaskNames(ProjectInternal project, Set<String> result) {
        result.addAll(getTaskNames(project));
        for (Project subProject : project.getChildProjects().values()) {
            collectTaskNames((ProjectInternal) subProject, result);
        }
    }

    private static class FixedTaskSelectionResult implements TaskSelectionResult {
        private final Collection<Task> tasks;

        FixedTaskSelectionResult(Collection<Task> tasks) {
            this.tasks = tasks;
        }

        public void collectTasks(Collection<? super Task> tasks) {
            tasks.addAll(this.tasks);
        }
    }

    private static class SingleProjectTaskSelectionResult implements TaskSelectionResult {
        private final TaskContainer taskContainer;
        private final String taskName;

        SingleProjectTaskSelectionResult(String taskName, TaskContainer tasksContainer) {
            this.taskContainer = tasksContainer;
            this.taskName = taskName;
        }

        public void collectTasks(Collection<? super Task> tasks) {
            tasks.add(taskContainer.getByName(taskName));
        }
    }

    private static class MultiProjectTaskSelectionResult implements TaskSelectionResult {
        private final ProjectInternal project;
        private final String taskName;

        MultiProjectTaskSelectionResult(String taskName, ProjectInternal project) {
            this.project = project;
            this.taskName = taskName;
        }

        public void collectTasks(Collection<? super Task> tasks) {
            collect(project, tasks);
        }

        private void collect(ProjectInternal project, Collection<? super Task> tasks) {
            if (hasTask(taskName, project)) {
                TaskInternal task = getExistingTask(project, taskName);
                tasks.add(task);
                if (task.getImpliesSubProjects()) {
                    return;
                }
            }
            for (Project subProject : project.getChildProjects().values()) {
                collect((ProjectInternal) subProject, tasks);
            }
        }
    }
}
