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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.internal.resources.ResourceLock;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class ActionNode extends Node implements SelfExecutingNode {
    private WorkNodeAction action;
    private List<Node> postExecutionNodes;
    private final ProjectInternal owningProject;
    private final ProjectInternal projectToLock;
    private boolean hasVisitedPreExecutionNode;

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

    @Nullable
    public WorkNodeAction getAction() {
        return action;
    }

    @Override
    public String toString() {
        return "work action " + action;
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
            if (action instanceof PostExecutionNodeAwareActionNode) {
                postExecutionNodes = ImmutableList.copyOf(((PostExecutionNodeAwareActionNode) action).getPostExecutionNodes());
            }
        } finally {
            action = null;
        }
    }

    @Override
    public boolean hasPendingPreExecutionNodes() {
        return !hasVisitedPreExecutionNode && action.getPreExecutionNode() != null;
    }

    @Override
    public void visitPreExecutionNodes(Consumer<? super Node> visitor) {
        if (!hasVisitedPreExecutionNode) {
            WorkNodeAction preExecutionNode = action.getPreExecutionNode();
            if (preExecutionNode != null) {
                visitor.accept(new ActionNode(preExecutionNode));
            }
            hasVisitedPreExecutionNode = true;
        }
    }

    @Override
    public void visitPostExecutionNodes(Consumer<? super Node> visitor) {
        if (postExecutionNodes != null) {
            try {
                for (Node node : postExecutionNodes) {
                    visitor.accept(node);
                }
            } finally {
                postExecutionNodes = null;
            }
        }
    }
}
