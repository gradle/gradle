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
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.execution.ProjectExecutionServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

class ActionNode extends Node {
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
    }

    @Override
    public Set<Node> getFinalizers() {
        return Collections.emptySet();
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
    public Project getProject() {
        return action.getProject();
    }

    public void run(ProjectExecutionServiceRegistry services) {
        ProjectInternal project = (ProjectInternal) action.getProject();
        ServiceRegistry registry = project == null ? ServiceRegistry.EMPTY : services.forProject(project);
        action.run(registry);
    }
}
