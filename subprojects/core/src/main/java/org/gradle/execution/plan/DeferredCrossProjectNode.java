/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.internal.tasks.DeferredCrossProjectDependency;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A placeholder {@link Node} representing an unresolved cross-project dependency.
 * Created during parallel task dependency resolution when a cross-project lookup
 * cannot be performed under the current project lock.
 *
 * <p>These nodes are injected into {@link ResolvedNodeRelationships} dependency sets
 * during parallel resolution and substituted with the real resolved nodes before
 * the results are passed to the sequential phase. They never appear in the final
 * execution graph.</p>
 */
@NullMarked
class DeferredCrossProjectNode extends Node {

    private final DeferredCrossProjectDependency deferredDependency;
    private List<Node> resolvedNodes = Collections.emptyList();

    DeferredCrossProjectNode(DeferredCrossProjectDependency deferredDependency) {
        this.deferredDependency = deferredDependency;
    }

    DeferredCrossProjectDependency getDeferredDependency() {
        return deferredDependency;
    }

    /**
     * Sets the real nodes that this placeholder resolves to.
     * For {@link DeferredCrossProjectDependency.ByProjectTask}, this is 0 or 1 node.
     * For {@link DeferredCrossProjectDependency.AllProjectsSearch}, this can be multiple nodes.
     */
    void resolve(List<Node> nodes) {
        this.resolvedNodes = nodes;
    }

    List<Node> getResolvedNodes() {
        return resolvedNodes;
    }

    @Override
    @Nullable
    public Throwable getNodeFailure() {
        return null;
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver) {
        throw new IllegalStateException(
            "DeferredCrossProjectNode should be substituted before sequential resolution: " + this
        );
    }

    @Override
    public String toString() {
        if (deferredDependency instanceof DeferredCrossProjectDependency.ByProjectTask) {
            DeferredCrossProjectDependency.ByProjectTask byProject = (DeferredCrossProjectDependency.ByProjectTask) deferredDependency;
            return "deferred cross-project node " + byProject.getTargetProjectIdentityPath() + ":" + byProject.getTaskName();
        }
        return "deferred cross-project node (all-projects search)";
    }
}
