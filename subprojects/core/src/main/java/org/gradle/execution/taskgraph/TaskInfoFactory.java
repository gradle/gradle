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


import com.google.common.collect.ImmutableCollection;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.composite.internal.IncludedBuildTaskGraph;
import org.gradle.composite.internal.IncludedBuildTaskResource.State;
import org.gradle.internal.build.BuildState;

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

    private static class TaskInAnotherBuild extends TaskInfo {
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
        public void collectTaskInto(ImmutableCollection.Builder<Task> builder) {
            // Expose the task to build logic (for now)
            builder.add(task);
        }

        @Override
        public Throwable getWorkFailure() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rethrowFailure() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void prepareForExecution() {
            // Should get back some kind of reference that can be queried below instead of looking the task up every time
            taskGraph.addTask(thisBuild, targetBuild, task.getPath());
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<WorkInfo> processHardSuccessor) {
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
        public int compareTo(WorkInfo other) {
            if (getClass() != other.getClass()) {
                return getClass().getName().compareTo(other.getClass().getName());
            }
            TaskInAnotherBuild taskInfo = (TaskInAnotherBuild) other;
            return task.getIdentityPath().compareTo(taskInfo.task.getIdentityPath());
        }

        @Override
        public String toString() {
            return task.getIdentityPath().toString();
        }
    }
}
