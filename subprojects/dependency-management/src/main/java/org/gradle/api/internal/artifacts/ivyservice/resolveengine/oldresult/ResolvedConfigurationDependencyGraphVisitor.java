/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.DefaultUnresolvedDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphPathResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts a {@link ResolvedConfigurationBuilder}, which is responsible for assembling the resolved configuration result, to a {@link DependencyGraphVisitor} and
 * {@link DependencyArtifactsVisitor}.
 */
public class ResolvedConfigurationDependencyGraphVisitor implements DependencyGraphVisitor, DependencyArtifactsVisitor {
    private final ResolvedConfigurationBuilder builder;
    private final Map<ModuleVersionSelector, BrokenDependency> failuresByRevisionId = new LinkedHashMap<ModuleVersionSelector, BrokenDependency>();
    private DependencyGraphNode root;

    public ResolvedConfigurationDependencyGraphVisitor(ResolvedConfigurationBuilder builder) {
        this.builder = builder;
    }

    public void start(DependencyGraphNode root) {
        this.root = root;
    }

    public void visitNode(DependencyGraphNode resolvedConfiguration) {
        builder.newResolvedDependency(resolvedConfiguration);
        for (DependencyGraphEdge dependency : resolvedConfiguration.getOutgoingEdges()) {
            ModuleVersionResolveException failure = dependency.getFailure();
            if (failure != null) {
                addUnresolvedDependency(dependency, dependency.getRequestedModuleVersion(), failure);
            }
        }
    }

    public void visitEdge(DependencyGraphNode resolvedConfiguration) {
        for (DependencyGraphEdge dependency : resolvedConfiguration.getIncomingEdges()) {
            if (dependency.getFrom() == root) {
                ModuleDependency moduleDependency = dependency.getModuleDependency();
                builder.addFirstLevelDependency(moduleDependency, resolvedConfiguration);
            }
        }
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, DependencyGraphNode to, ArtifactSet artifacts) {
        builder.addChild(from, to, artifacts.getId());
    }

    public void finish(DependencyGraphNode root) {
        attachFailures(builder);
        builder.done(root);
    }

    public void finishArtifacts() {
    }

    private void attachFailures(ResolvedConfigurationBuilder result) {
        for (Map.Entry<ModuleVersionSelector, BrokenDependency> entry : failuresByRevisionId.entrySet()) {
            Collection<List<ComponentIdentifier>> paths = DependencyGraphPathResolver.calculatePaths(entry.getValue().requiredBy, root);
            result.addUnresolvedDependency(new DefaultUnresolvedDependency(entry.getKey(), entry.getValue().failure.withIncomingPaths(paths)));
        }
    }

    private void addUnresolvedDependency(DependencyGraphEdge dependency, ModuleVersionSelector requested, ModuleVersionResolveException failure) {
        BrokenDependency breakage = failuresByRevisionId.get(requested);
        if (breakage == null) {
            breakage = new BrokenDependency(failure);
            failuresByRevisionId.put(requested, breakage);
        }
        breakage.requiredBy.add(dependency.getFrom());
    }

    private static class BrokenDependency {
        final ModuleVersionResolveException failure;
        final List<DependencyGraphNode> requiredBy = new ArrayList<DependencyGraphNode>();

        private BrokenDependency(ModuleVersionResolveException failure) {
            this.failure = failure;
        }
    }

}
