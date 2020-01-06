/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Task;

/**
 * Resolves dependencies to {@link TaskNode} objects. Uses the same logic as {@link #TASK_AS_TASK}.
 */
public class TaskNodeDependencyResolver implements DependencyResolver {
    private final TaskNodeFactory taskNodeFactory;

    public TaskNodeDependencyResolver(TaskNodeFactory taskNodeFactory) {
        this.taskNodeFactory = taskNodeFactory;
    }

    @Override
    public boolean resolve(Task task, Object node, final Action<? super Node> resolveAction) {
        return TASK_AS_TASK.resolve(task, node, resolved -> resolveAction.execute(taskNodeFactory.getOrCreateNode(resolved)));
    }

    @Override
    public boolean attachActionTo(Node value, Action<? super Task> action) {
        if (value instanceof TaskNode) {
            TaskNode taskNode = (TaskNode) value;
            taskNode.appendPostAction(action);
            return true;
        }
        return false;
    }
}
