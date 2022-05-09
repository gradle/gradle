/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.internal.resources.ResourceLock;

import javax.annotation.Nullable;

public class ResolveMutationsNode extends Node implements SelfExecutingNode {
    private final LocalTaskNode node;
    private final NodeValidator nodeValidator;
    private Exception failure;

    public ResolveMutationsNode(LocalTaskNode node, NodeValidator nodeValidator) {
        this.node = node;
        this.nodeValidator = nodeValidator;
    }

    public Node getNode() {
        return node;
    }

    @Override
    public String toString() {
        return "Resolve mutations for " + node;
    }

    @Override
    public int compareTo(Node o) {
        return -1;
    }

    @Nullable
    @Override
    public Throwable getNodeFailure() {
        return failure;
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver) {
    }

    @Nullable
    @Override
    public ResourceLock getProjectToLock() {
        return node.getProjectToLock();
    }

    @Nullable
    @Override
    public ProjectInternal getOwningProject() {
        return node.getOwningProject();
    }

    @Override
    public void execute(NodeExecutionContext context) {
        try {
            MutationInfo mutations = node.getMutationInfo();
            node.resolveMutations();
            mutations.hasValidationProblem = nodeValidator.hasValidationProblems(node);
        } catch (Exception e) {
            failure = e;
        }
    }
}
