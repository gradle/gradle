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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.internal.resources.ResourceLock;

import javax.annotation.Nullable;

public class ActionNode extends Node implements SelfExecutingNode {
    private WorkNodeAction action;
    private final ProjectInternal owningProject;
    private final ProjectInternal projectToLock;

    public ActionNode(WorkNodeAction action) {
        this.action = action;
        this.owningProject = (ProjectInternal) action.getOwningProject();
        if (owningProject != null && action.usesMutableProjectState()) {
            this.projectToLock = owningProject;
        } else {
            this.projectToLock = null;
        }
    }

    @Nullable
    @Override
    public Throwable getNodeFailure() {
        return null;
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver) {
        TaskDependencyContainer dependencies = action::visitDependencies;
        for (Node node : dependencyResolver.resolveDependenciesFor(null, dependencies)) {
            addDependencySuccessor(node);
        }
    }

    public WorkNodeAction getAction() {
        return action;
    }

    @Override
    public String toString() {
        return "work action " + action;
    }

    @Override
    public int compareTo(Node other) {
        // Prefer to run task nodes before action nodes
        if (other instanceof LocalTaskNode) {
            return 1;
        } else {
            return -1;
        }
    }

    @Override
    public boolean isPriority() {
        return getProjectToLock() != null;
    }

    @Nullable
    @Override
    public ResourceLock getProjectToLock() {
        if (projectToLock != null) {
            return projectToLock.getOwner().getAccessLock();
        }
        return null;
    }

    @Nullable
    @Override
    public ProjectInternal getOwningProject() {
        return owningProject;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        try {
            action.run(context);
        } finally {
            action = null;
        }
    }
}
