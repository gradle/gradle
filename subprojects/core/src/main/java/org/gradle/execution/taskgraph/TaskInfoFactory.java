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


import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.composite.internal.IncludedBuildTaskResource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TaskInfoFactory {
    private final Map<Task, TaskInfo> nodes = new HashMap<Task, TaskInfo>();
    private final TaskFailureCollector failureCollector;

    public TaskInfoFactory(TaskFailureCollector failureCollector) {
        this.failureCollector = failureCollector;
    }

    public Set<Task> getTasks() {
        return nodes.keySet();
    }

    public TaskInfo createNode(Task task) {
        TaskInfo node = nodes.get(task);
        if (node == null) {
            if (task instanceof IncludedBuildTaskResource) {
                node = new TaskResourceTaskInfo((TaskInternal) task, failureCollector);
            } else {
                node = new TaskInfo((TaskInternal) task);
            }
            nodes.put(task, node);
        }
        return node;
    }

    public void clear() {
        nodes.clear();
    }

    private static class TaskResourceTaskInfo extends TaskInfo {
        private final TaskFailureCollector failureCollector;
        private boolean complete;

        public TaskResourceTaskInfo(TaskInternal task, TaskFailureCollector failureCollector) {
            super(task);
            this.failureCollector = failureCollector;
            doNotRequire();
        }

        @Override
        public void require() {
            // Ignore
        }

        @Override
        public boolean isComplete() {
            if (complete) {
                return true;
            }

            IncludedBuildTaskResource task = (IncludedBuildTaskResource) getTask();
            try {
                complete = task.isComplete();
            } catch (Exception e) {
                complete = true;

                // The failures need to be explicitly collected here, since the wrapper task is never added to the execution plan,
                // and thus doesn't have failures collected in the usual way on execution.
                failureCollector.addFailure(e);
            }

            return complete;
        }
    }
}
