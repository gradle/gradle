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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.internal.resources.ResourceLock;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ActionNode extends Node implements SelfExecutingNode {
    private final WorkNodeAction action;

    public ActionNode(WorkNodeAction action) {
        this.action = action;
    }

    @Nullable
    @Override
    public Throwable getNodeFailure() {
        return null;
    }

    @Override
    public void rethrowNodeFailure() {
    }

    @Override
    public void prepareForExecution() {
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
        TaskDependencyContainer dependencies = action::visitDependencies;
        for (Node node : dependencyResolver.resolveDependenciesFor(null, dependencies)) {
            addDependencySuccessor(node);
            processHardSuccessor.execute(node);
        }
    }

    @Override
    public Set<Node> getFinalizers() {
        return Collections.emptySet();
    }

    @Override
    public void resolveMutations() {
        // Assume has no outputs that can be destroyed or that overlap with another node
    }

    public WorkNodeAction getAction() {
        return action;
    }

    @Override
    public boolean isPublicNode() {
        return false;
    }

    @Override
    public boolean requiresMonitoring() {
        return false;
    }

    @Override
    public String toString() {
        return "work action " + action;
    }

    @Override
    public int compareTo(Node o) {
        return -1;
    }

    @Nullable
    @Override
    public ResourceLock getProjectToLock() {
        ProjectInternal project = (ProjectInternal) action.getProject();
        if (project != null) {
            return project.getMutationState().getAccessLock();
        }
        return null;
    }

    @Nullable
    @Override
    public ProjectInternal getOwningProject() {
        return (ProjectInternal) action.getProject();
    }

    @Override
    public List<ResourceLock> getResourcesToLock() {
        return Collections.emptyList();
    }

    @Override
    public void execute(NodeExecutionContext context) {
        action.run(context);
    }
}
