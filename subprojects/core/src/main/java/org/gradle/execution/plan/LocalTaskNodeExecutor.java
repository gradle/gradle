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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskExecutionContext;

public class LocalTaskNodeExecutor implements NodeExecutor {

    @Override
    public boolean execute(Node node, NodeExecutionContext context) {
        if (node instanceof LocalTaskNode) {
            LocalTaskNode localTaskNode = (LocalTaskNode) node;
            TaskInternal task = localTaskNode.getTask();
            TaskStateInternal state = task.getState();
            MissingTaskDependencyDetector missingTaskDependencyDetector = context.getService(MissingTaskDependencyDetector.class);
            TaskExecutionContext ctx = new DefaultTaskExecutionContext(
                localTaskNode,
                localTaskNode.getTaskProperties(),
                localTaskNode.getValidationContext(),
                typeValidationContext -> missingTaskDependencyDetector.detectMissingDependencies(localTaskNode, typeValidationContext)
            );
            TaskExecuter taskExecuter = context.getService(TaskExecuter.class);
            taskExecuter.execute(task, state, ctx);
            return true;
        } else {
            return false;
        }
    }
}
