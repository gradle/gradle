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

package org.gradle.execution.taskgraph;


import com.google.common.collect.ImmutableSet;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.composite.internal.IncludedBuildTaskGraph;
import org.gradle.composite.internal.IncludedBuildTaskResource.State;
import org.gradle.internal.build.BuildState;
import org.gradle.util.Path;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TaskInfoFactory {
    private final Map<Task, TaskInfo> nodes = new HashMap<Task, TaskInfo>();
    private final IncludedBuildTaskGraph taskGraph;
    private final GradleInternal thisBuild;
    private final BuildIdentifier currentBuildId;

    public TaskInfoFactory(GradleInternal thisBuild, IncludedBuildTaskGraph taskGraph) {
        this.thisBuild = thisBuild;
        currentBuildId = thisBuild.getServices().get(BuildState.class).getBuildIdentifier();
        this.taskGraph = taskGraph;
    }

    public Set<Task> getTasks() {
        return nodes.keySet();
    }

    public TaskInfo getOrCreateNode(Task task) {
        TaskInfo node = nodes.get(task);
        if (node == null) {
            if (task.getProject().getGradle() == thisBuild) {
                node = new LocalTaskInfo((TaskInternal) task);
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

    public static class TaskInAnotherBuild extends TaskInfo {
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
        public Path getIdentityPath() {
            return task.getIdentityPath();
        }

        @Override
        public TaskInternal getTask() {
            // Do not expose the task to execution
            throw new UnsupportedOperationException();
        }

        @Override
        public void collectTaskInto(ImmutableSet.Builder<Task> builder) {
            // Expose the task to build logic (for now)
            builder.add(task);
        }

        @Override
        public Throwable getTaskFailure() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean satisfies(Spec<? super Task> filter) {
            // This is only present because something else in the graph requires it, and filters apply only to root build (for now) -> include
            return true;
        }

        @Override
        public Collection<? extends TaskInfo> getDependencies(TaskDependencyResolver dependencyResolver) {
            return Collections.emptyList();
        }

        @Override
        public Collection<? extends TaskInfo> getFinalizedBy(TaskDependencyResolver dependencyResolver) {
            return Collections.emptyList();
        }

        @Override
        public Collection<? extends TaskInfo> getMustRunAfter(TaskDependencyResolver dependencyResolver) {
            return Collections.emptyList();
        }

        @Override
        public Collection<? extends TaskInfo> getShouldRunAfter(TaskDependencyResolver dependencyResolver) {
            return Collections.emptyList();
        }

        @Override
        public void prepareForExecution() {
            // Should get back some kind of reference that can be queried below instead of looking the task up every time
            taskGraph.addTask(thisBuild, targetBuild, task.getPath());
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
        public int compareTo(TaskInfo other) {
            if (other.getClass() != getClass()) {
                return 1;
            }
            TaskInAnotherBuild taskInfo = (TaskInAnotherBuild) other;
            return task.getIdentityPath().compareTo(taskInfo.task.getIdentityPath());
        }
    }
}
