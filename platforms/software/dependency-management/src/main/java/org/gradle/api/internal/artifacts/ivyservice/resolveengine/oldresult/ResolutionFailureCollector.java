/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ivyservice.DefaultUnresolvedDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphPathResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ResolutionFailureCollector implements DependencyGraphVisitor {
    private final Map<ComponentSelector, BrokenDependency> failuresByRevisionId = new LinkedHashMap<>();
    private final ComponentSelectorConverter componentSelectorConverter;
    private RootGraphNode root;

    public ResolutionFailureCollector(ComponentSelectorConverter componentSelectorConverter) {
        this.componentSelectorConverter = componentSelectorConverter;
    }

    @Override
    public void start(RootGraphNode root) {
        this.root = root;
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        for (DependencyGraphEdge dependency : node.getOutgoingEdges()) {
            ModuleVersionResolveException failure = dependency.getFailure();
            if (failure != null) {
                addUnresolvedDependency(dependency, dependency.getRequested(), failure);
            }
        }
    }

    public Set<UnresolvedDependency> complete(Set<UnresolvedDependency> extraFailures) {
        if (extraFailures.isEmpty() && failuresByRevisionId.isEmpty()) {
            return ImmutableSet.of();
        }
        ImmutableSet.Builder<UnresolvedDependency> builder = ImmutableSet.builder();
        builder.addAll(extraFailures);
        for (Map.Entry<ComponentSelector, BrokenDependency> entry : failuresByRevisionId.entrySet()) {
            Collection<List<ComponentIdentifier>> paths = DependencyGraphPathResolver.calculatePaths(entry.getValue().requiredBy, root);

            ComponentSelector key = entry.getKey();
            ModuleVersionSelector moduleVersionSelector = componentSelectorConverter.getSelector(key);
            builder.add(new DefaultUnresolvedDependency(moduleVersionSelector, entry.getValue().failure.withIncomingPaths(paths)));
        }
        return builder.build();
    }

    private void addUnresolvedDependency(DependencyGraphEdge dependency, ComponentSelector selector, ModuleVersionResolveException failure) {
        BrokenDependency breakage = failuresByRevisionId.get(selector);
        if (breakage == null) {
            breakage = new BrokenDependency(failure);
            failuresByRevisionId.put(selector, breakage);
        }
        breakage.requiredBy.add(dependency.getFrom());
    }

    private static class BrokenDependency {
        final ModuleVersionResolveException failure;
        final List<DependencyGraphNode> requiredBy = new ArrayList<>();

        private BrokenDependency(ModuleVersionResolveException failure) {
            this.failure = failure;
        }
    }

}
