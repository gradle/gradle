/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.execution.plan;


import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.composite.internal.IncludedBuildTaskGraph;
import org.gradle.composite.internal.IncludedBuildTaskResource.State;
import org.gradle.internal.Actions;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.resources.ResourceLock;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TaskNodeFactory {
    private final Map<Task, TaskNode> nodes = new HashMap<Task, TaskNode>();
    private final IncludedBuildTaskGraph taskGraph;
    private final GradleInternal thisBuild;
    private final BuildIdentifier currentBuildId;
    private final Map<File, String> canonicalizedFileCache = Maps.newIdentityHashMap();

    public TaskNodeFactory(GradleInternal thisBuild, IncludedBuildTaskGraph taskGraph) {
        this.thisBuild = thisBuild;
        currentBuildId = thisBuild.getServices().get(BuildState.class).getBuildIdentifier();
        this.taskGraph = taskGraph;
    }

    public Set<Task> getTasks() {
        return nodes.keySet();
    }

    public TaskNode getOrCreateNode(Task task) {
        TaskNode node = nodes.get(task);
        if (node == null) {
            if (task.getProject().getGradle() == thisBuild) {
                node = new LocalTaskNode((TaskInternal) task, canonicalizedFileCache);
            } else {
                node = new TaskInAnotherBuild((TaskInternal) task, currentBuildId, taskGraph);
            }
            nodes.put(task, node);
        }
        return node;
    }

    public void clear() {
        nodes.clear();
    }

    private static class TaskInAnotherBuild extends TaskNode {
        private final BuildIdentifier thisBuild;
        private final IncludedBuildTaskGraph taskGraph;
        private final BuildIdentifier targetBuild;
        private final TaskInternal task;
        private State state = State.WAITING;

        TaskInAnotherBuild(TaskInternal task, BuildIdentifier thisBuild, IncludedBuildTaskGraph taskGraph) {
            this.thisBuild = thisBuild;
            this.task = task;
            this.taskGraph = taskGraph;
            this.targetBuild = ((ProjectInternal) task.getProject()).getServices().get(BuildState.class).getBuildIdentifier();
            doNotRequire();
        }

        @Override
        public TaskInternal getTask() {
            // Expose the task to build logic (for now)
            return task;
        }

        @Nullable
        @Override
        public ResourceLock getProjectToLock() {
            // Ignore, as the node in the other build's execution graph takes care of this
            return null;
        }

        @Nullable
        @Override
        public ProjectInternal getOwningProject() {
            // Ignore, as the node in the other build's execution graph takes care of this
            return null;
        }

        @Override
        public List<ResourceLock> getResourcesToLock() {
            // Ignore, as the node in the other build's execution graph will take care of this
            return Collections.emptyList();
        }

        @Override
        public Throwable getNodeFailure() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rethrowNodeFailure() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendPostAction(Action<? super Task> action) {
            // Ignore. Currently the actions don't need to run, it's just better if they do
            // By the time this node is notified that the task in the other build has completed, it's too late to run the action
            // Instead, the action should be attached to the task in the other build rather than here
        }

        @Override
        public Action<? super Task> getPostAction() {
            return Actions.doNothing();
        }

        @Override
        public void prepareForExecution() {
            // Should get back some kind of reference that can be queried below instead of looking the task up every time
            taskGraph.addTask(thisBuild, targetBuild, task.getPath());
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
        }

        @Override
        public boolean requiresMonitoring() {
            return true;
        }

        @Override
        public void require() {
            // Ignore
        }

        @Override
        public boolean isSuccessful() {
            return state == State.SUCCESS;
        }

        @Override
        public boolean isFailed() {
            return state == State.FAILED;
        }

        @Override
        public boolean isComplete() {
            if (state != State.WAITING) {
                return true;
            }

            state = taskGraph.getTaskState(targetBuild, task.getPath());
            return state != State.WAITING;
        }

        @Override
        public int compareTo(Node other) {
            if (getClass() != other.getClass()) {
                return getClass().getName().compareTo(other.getClass().getName());
            }
            TaskInAnotherBuild taskNode = (TaskInAnotherBuild) other;
            return task.getIdentityPath().compareTo(taskNode.task.getIdentityPath());
        }

        @Override
        public void resolveMutations() {
            // Assume for now that no task in the consuming build will destroy the outputs of this task or overlaps with this task
        }

        @Override
        public String toString() {
            return task.getIdentityPath().toString();
        }
    }
}
